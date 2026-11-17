"""Chain state, full validation of every block, block building, and storage.
A longer chain from a peer is never trusted until every block in it is validated."""

import json
import os

from .block import Block, Header, ZERO_HASH, mine_full
from .consensus import GENESIS_DIFFICULTY, GENESIS_TIMESTAMP, LWMA_WINDOW, MAX_SUPPLY, MIN_FEE, NETWORK_ID, PROTOCOL_VERSION
from .difficulty import Sample, next_difficulty
from .emission import emitted_through, reward_at
from .wallet import is_valid_address, tx_id, verify_tx_signature


class ChainError(Exception):
    pass


def check_tx(tx, balances, nonces):
    """Return None if the transaction is valid against this state, otherwise the reason."""
    required = ("sender_pub", "sender", "recipient", "amount", "fee", "nonce", "network", "signature")
    if not isinstance(tx, dict) or any(k not in tx for k in required):
        return "malformed transaction"
    if tx["network"] != NETWORK_ID:
        return "transaction is for a different network"
    amount, fee, nonce = tx["amount"], tx["fee"], tx["nonce"]
    if not all(type(v) is int for v in (amount, fee, nonce)):
        return "amount, fee and nonce must be whole numbers"
    if amount <= 0 or amount > MAX_SUPPLY:
        return "invalid amount"
    if fee < MIN_FEE:
        return "fee is below the minimum"
    if not is_valid_address(tx["sender"]) or not is_valid_address(tx["recipient"]):
        return "invalid address"
    if tx["sender"] == tx["recipient"]:
        return "cannot send to yourself"
    if nonce != nonces.get(tx["sender"], 0):
        return "wrong nonce (possible replay or out-of-order transaction)"
    if balances.get(tx["sender"], 0) < amount + fee:
        return "insufficient balance"
    if not verify_tx_signature(tx):
        return "bad signature"
    return None


def apply_tx(tx, balances, nonces) -> int:
    """Validate and apply one transaction to the given balance/nonce maps. Returns the fee."""
    reason = check_tx(tx, balances, nonces)
    if reason is not None:
        raise ChainError(reason)
    sender, recipient = tx["sender"], tx["recipient"]
    balances[sender] = balances.get(sender, 0) - tx["amount"] - tx["fee"]
    balances[recipient] = balances.get(recipient, 0) + tx["amount"]
    nonces[sender] = nonces.get(sender, 0) + 1
    return tx["fee"]


def genesis_block(difficulty: int = GENESIS_DIFFICULTY) -> Block:
    block = Block(
        header=Header(PROTOCOL_VERSION, 0, ZERO_HASH, ZERO_HASH, GENESIS_TIMESTAMP, difficulty, 0),
        coinbase={"miner": "CDC" + "0" * 40, "reward": 0},
        txs=[],
    )
    block.header.merkle_root = block.compute_merkle()
    mine_full(block)
    return block


class Chain:
    def __init__(self, genesis: Block):
        self._check_genesis(genesis)
        self.blocks = [genesis]
        self.work = [genesis.header.difficulty]
        self.balances = {}
        self.nonces = {}

    @staticmethod
    def _check_genesis(g: Block) -> None:
        h = g.header
        ok = (h.height == 0 and h.version == PROTOCOL_VERSION and h.prev_hash == ZERO_HASH
              and g.coinbase.get("reward") == 0 and not g.txs
              and h.merkle_root == g.compute_merkle() and h.meets_target())
        if not ok:
            raise ChainError("invalid genesis block")

    @classmethod
    def from_blocks(cls, blocks):
        if not blocks:
            raise ChainError("empty chain")
        chain = cls(blocks[0])
        for block in blocks[1:]:
            chain.append(block)
        return chain

    @classmethod
    def load(cls, path: str):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        return cls.from_blocks([Block.from_dict(d) for d in data])

    def save(self, path: str) -> None:
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump([b.to_dict() for b in self.blocks], f)
        os.replace(tmp, path)

    # ----- queries -----
    @property
    def tip(self) -> Block:
        return self.blocks[-1]

    @property
    def height(self) -> int:
        return self.tip.header.height

    @property
    def cumulative_work(self) -> int:
        return self.work[-1]

    @property
    def issued(self) -> int:
        return emitted_through(self.height)

    def balance(self, address: str) -> int:
        return self.balances.get(address, 0)

    def next_nonce(self, address: str) -> int:
        return self.nonces.get(address, 0)

    def expected_difficulty(self) -> int:
        start = max(0, len(self.blocks) - (LWMA_WINDOW + 1))
        samples = [Sample(b.header.timestamp, b.header.difficulty) for b in self.blocks[start:]]
        return next_difficulty(samples)

    def history(self, address: str) -> list:
        rows = []
        for block in self.blocks:
            if block.coinbase["miner"] == address and block.coinbase["reward"] > 0:
                rows.append({"height": block.header.height, "kind": "mined",
                             "amount": block.coinbase["reward"], "other": "", "tx_id": ""})
            for tx in block.txs:
                if tx["sender"] == address:
                    rows.append({"height": block.header.height, "kind": "sent",
                                 "amount": tx["amount"], "other": tx["recipient"], "tx_id": tx_id(tx)})
                elif tx["recipient"] == address:
                    rows.append({"height": block.header.height, "kind": "received",
                                 "amount": tx["amount"], "other": tx["sender"], "tx_id": tx_id(tx)})
        return rows

    # ----- validation and growth -----
    def _check_header(self, block: Block) -> None:
        tip = self.tip
        h = block.header
        if h.height != tip.header.height + 1:
            raise ChainError(f"bad height {h.height}, expected {tip.header.height + 1}")
        if h.version != PROTOCOL_VERSION:
            raise ChainError("unsupported protocol version")
        if h.prev_hash != tip.hash():
            raise ChainError("previous block hash does not match")
        if h.timestamp <= tip.header.timestamp:
            raise ChainError("timestamp must be later than the previous block")
        expected = self.expected_difficulty()
        if h.difficulty != expected:
            raise ChainError(f"bad difficulty {h.difficulty}, expected {expected}")
        if h.merkle_root != block.compute_merkle():
            raise ChainError("merkle root does not match block contents")
        if not h.meets_target():
            raise ChainError("proof of work does not meet the target")

    @staticmethod
    def _apply_block(block: Block, balances: dict, nonces: dict) -> None:
        fees = 0
        for tx in block.txs:
            fees += apply_tx(tx, balances, nonces)
        cb = block.coinbase
        if not is_valid_address(cb.get("miner")):
            raise ChainError("invalid miner address")
        expected = reward_at(block.header.height) + fees
        if cb.get("reward") != expected:
            raise ChainError(f"bad block reward {cb.get('reward')}, expected {expected}")
        balances[cb["miner"]] = balances.get(cb["miner"], 0) + cb["reward"]

    def append(self, block: Block) -> None:
        self._check_header(block)
        balances, nonces = dict(self.balances), dict(self.nonces)
        self._apply_block(block, balances, nonces)
        self.work.append(self.work[-1] + block.header.difficulty)
        self.blocks.append(block)
        self.balances, self.nonces = balances, nonces

    def prepare_block(self, miner: str, candidate_txs, timestamp: int) -> Block:
        """Build an unmined block. Transactions that are invalid at this point are skipped."""
        balances, nonces = dict(self.balances), dict(self.nonces)
        included, fees = [], 0
        for tx in candidate_txs:
            if check_tx(tx, balances, nonces) is None:
                fees += apply_tx(tx, balances, nonces)
                included.append(tx)
        height = self.height + 1
        block = Block(
            header=Header(PROTOCOL_VERSION, height, self.tip.hash(), ZERO_HASH,
                          max(timestamp, self.tip.header.timestamp + 1),
                          self.expected_difficulty(), 0),
            coinbase={"miner": miner, "reward": reward_at(height) + fees},
            txs=included,
        )
        block.header.merkle_root = block.compute_merkle()
        return block

    def consider(self, blocks) -> bool:
        """Adopt a peer's chain if it is fully valid and has more cumulative work."""
        candidate = Chain.from_blocks(blocks)
        if candidate.blocks[0].hash() != self.blocks[0].hash():
            raise ChainError("different genesis block")
        if candidate.cumulative_work <= self.cumulative_work:
            return False
        self.blocks = candidate.blocks
        self.work = candidate.work
        self.balances = candidate.balances
        self.nonces = candidate.nonces
        return True
