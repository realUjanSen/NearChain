import unittest

from nearchain.block import mine_full
from nearchain.chain import Chain, genesis_block
from nearchain.miner import Miner
from nearchain.network import Node
from nearchain.wallet import Wallet
from tests.helpers import MINER, OTHER, wait_for


def make_node():
    node = Node(Chain(genesis_block(1)), host="127.0.0.1", port=0)
    node.start()
    return node


def mine_one(node, address):
    block = node.prepare_mining_block(address)
    mine_full(block)
    return node.submit_block(block)


class NetworkTests(unittest.TestCase):
    def setUp(self):
        self.a = make_node()
        self.b = make_node()

    def tearDown(self):
        self.a.stop()
        self.b.stop()

    def test_nodes_sync_chain_and_relay_transactions(self):
        wallet = Wallet.create()
        for _ in range(3):
            self.assertTrue(mine_one(self.b, wallet.address))

        self.a.connect("127.0.0.1", self.b.bound_port)
        self.assertTrue(wait_for(lambda: self.a.chain.height == 3), "node A did not sync")
        self.assertEqual(self.a.chain.tip.hash(), self.b.chain.tip.hash())

        tx = wallet.make_transfer(OTHER, 100, 1, nonce=0)
        ok, reason = self.b.submit_tx(tx)
        self.assertTrue(ok, reason)
        self.assertTrue(wait_for(lambda: len(self.a.mempool) == 1), "transaction did not relay")

        self.assertTrue(mine_one(self.a, MINER))
        self.assertTrue(wait_for(lambda: self.b.chain.height == 4), "block did not relay back")
        self.assertEqual(self.b.chain.balance(OTHER), 100)
        self.assertEqual(len(self.b.mempool), 0)

    def test_invalid_transaction_refused_by_node(self):
        broke = Wallet.create()
        ok, reason = self.a.submit_tx(broke.make_transfer(OTHER, 5, 1, 0))
        self.assertFalse(ok)
        self.assertEqual(reason, "insufficient balance")

    def test_miner_thread_produces_blocks(self):
        miner = Miner(self.a, MINER, mode="foreground", batch_size=50)
        miner.start()
        self.assertTrue(wait_for(lambda: miner.blocks_mined >= 2), "miner did not produce blocks")
        miner.stop()
        miner.join(5)
        self.assertGreaterEqual(self.a.chain.height, 2)


if __name__ == "__main__":
    unittest.main()
