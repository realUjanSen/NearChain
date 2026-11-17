"""Shared helpers for the test suite."""
import time

from nearchain.block import mine_full
from nearchain.chain import Chain, genesis_block

MINER = "CDC" + "11" * 20
OTHER = "CDC" + "22" * 20


def easy_chain() -> Chain:
    """A chain with difficulty 1, so every nonce is valid and tests run quickly."""
    return Chain(genesis_block(1))


def add_block(chain: Chain, txs=(), miner=MINER, step=60):
    block = chain.prepare_block(miner, list(txs), chain.tip.header.timestamp + step)
    mine_full(block)
    chain.append(block)
    return block


def wait_for(condition, timeout=10.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if condition():
            return True
        time.sleep(0.05)
    return False
