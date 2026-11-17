"""Block headers, blocks, Merkle roots, and proof-of-work helpers."""

import hashlib
import json
from dataclasses import asdict, dataclass, field

from .wallet import tx_id

ZERO_HASH = "0" * 64


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256d_hex(data: bytes) -> str:
    return hashlib.sha256(hashlib.sha256(data).digest()).hexdigest()


def meets_target(block_hash: str, difficulty: int) -> bool:
    """True when the first 64 bits of the hash are at most (2^64 - 1) / difficulty."""
    if difficulty <= 0:
        return False
    return int(block_hash[:16], 16) <= (2**64 - 1) // difficulty


@dataclass
class Header:
    version: int
    height: int
    prev_hash: str
    merkle_root: str
    timestamp: int
    difficulty: int
    nonce: int = 0

    def hash(self) -> str:
        fields = (self.version, self.height, self.prev_hash, self.merkle_root,
                  self.timestamp, self.difficulty, self.nonce)
        return sha256d_hex("|".join(str(v) for v in fields).encode("utf-8"))

    def meets_target(self) -> bool:
        return meets_target(self.hash(), self.difficulty)

    def to_dict(self) -> dict:
        return asdict(self)


def coinbase_id(coinbase: dict) -> str:
    return sha256_hex(json.dumps(coinbase, sort_keys=True, separators=(",", ":")).encode("utf-8"))


def merkle_root(ids) -> str:
    if not ids:
        return ZERO_HASH
    level = list(ids)
    while len(level) > 1:
        if len(level) % 2 == 1:
            level.append(level[-1])
        level = [
            sha256d_hex(bytes.fromhex(level[i]) + bytes.fromhex(level[i + 1]))
            for i in range(0, len(level), 2)
        ]
    return level[0]


@dataclass
class Block:
    header: Header
    coinbase: dict                      # {"miner": address, "reward": int}
    txs: list = field(default_factory=list)

    def hash(self) -> str:
        return self.header.hash()

    def compute_merkle(self) -> str:
        return merkle_root([coinbase_id(self.coinbase)] + [tx_id(t) for t in self.txs])

    def to_dict(self) -> dict:
        return {"header": self.header.to_dict(), "coinbase": dict(self.coinbase), "txs": list(self.txs)}

    @staticmethod
    def from_dict(data: dict) -> "Block":
        return Block(Header(**data["header"]), dict(data["coinbase"]), list(data["txs"]))


def mine_batch(block: Block, count: int) -> bool:
    """Try up to `count` nonces. Returns True when the current nonce meets the target."""
    header = block.header
    for _ in range(count):
        if header.meets_target():
            return True
        header.nonce += 1
    return header.meets_target()


def mine_full(block: Block) -> None:
    while not mine_batch(block, 100_000):
        pass
