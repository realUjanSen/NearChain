import unittest

from nearchain.block import mine_full
from nearchain.chain import ChainError, Chain, check_tx, genesis_block
from nearchain.consensus import BLOCK_REWARD, MAX_DIFFICULTY, MAX_SUPPLY, MIN_DIFFICULTY, TARGET_BLOCK_TIME, GENESIS_TIMESTAMP
from nearchain.difficulty import Sample, next_difficulty
from nearchain.emission import CAP_HEIGHT, emitted_through, reward_at
from nearchain.wallet import Wallet, tx_id, verify_tx_signature
from tests.helpers import MINER, OTHER, add_block, easy_chain


class EmissionTests(unittest.TestCase):
    def test_reward_is_1000_per_block(self):
        self.assertEqual(reward_at(0), 0)
        self.assertEqual(reward_at(1), BLOCK_REWARD)
        self.assertEqual(reward_at(12_345), BLOCK_REWARD)

    def test_cap_reached_exactly_at_21_million(self):
        self.assertEqual(CAP_HEIGHT, 21_000_000)
        self.assertEqual(emitted_through(CAP_HEIGHT), MAX_SUPPLY)
        self.assertEqual(reward_at(CAP_HEIGHT), BLOCK_REWARD)

    def test_no_reward_after_cap(self):
        self.assertEqual(reward_at(CAP_HEIGHT + 1), 0)
        self.assertEqual(emitted_through(10**12), MAX_SUPPLY)

    def test_total_never_exceeds_cap_near_boundary(self):
        totals = [emitted_through(h) for h in range(CAP_HEIGHT - 3, CAP_HEIGHT + 4)]
        self.assertTrue(all(t <= MAX_SUPPLY for t in totals))
        self.assertEqual(totals, sorted(totals))


class DifficultyTests(unittest.TestCase):
    @staticmethod
    def samples(count, step, difficulty):
        return [Sample(GENESIS_TIMESTAMP + i * step, difficulty) for i in range(count)]

    def test_stable_when_blocks_on_target(self):
        self.assertEqual(next_difficulty(self.samples(81, TARGET_BLOCK_TIME, 5000)), 5000)

    def test_rises_when_blocks_are_fast(self):
        self.assertEqual(next_difficulty(self.samples(81, TARGET_BLOCK_TIME // 2, 5000)), 10_000)

    def test_falls_when_blocks_are_slow(self):
        self.assertEqual(next_difficulty(self.samples(81, TARGET_BLOCK_TIME * 2, 5000)), 2500)

    def test_does_not_rise_just_because_chain_is_longer(self):
        for length in (2, 10, 61, 200, 1000):
            self.assertEqual(next_difficulty(self.samples(length, TARGET_BLOCK_TIME, 5000)), 5000)

    def test_huge_gap_is_clamped(self):
        self.assertEqual(next_difficulty(self.samples(81, 10_000, 5000)), 833)

    def test_bounds_hold(self):
        self.assertLessEqual(next_difficulty(self.samples(81, 1, MAX_DIFFICULTY)), MAX_DIFFICULTY)
        self.assertGreaterEqual(next_difficulty(self.samples(81, 10**6, MIN_DIFFICULTY)), MIN_DIFFICULTY)


class WalletTests(unittest.TestCase):
    def test_signed_transfer_verifies(self):
        w = Wallet.create()
        tx = w.make_transfer(OTHER, 10, 1, 0)
        self.assertTrue(verify_tx_signature(tx))

    def test_tampered_amount_fails_signature(self):
        w = Wallet.create()
        tx = w.make_transfer(OTHER, 10, 1, 0)
        tx["amount"] = 10_000
        self.assertFalse(verify_tx_signature(tx))

    def test_sender_address_must_match_key(self):
        w, other = Wallet.create(), Wallet.create()
        tx = w.make_transfer(OTHER, 10, 1, 0)
        tx["sender"] = other.address
        self.assertFalse(verify_tx_signature(tx))

    def test_transaction_id_changes_with_content(self):
        w = Wallet.create()
        a = w.make_transfer(OTHER, 10, 1, 0)
        b = w.make_transfer(OTHER, 11, 1, 0)
        self.assertNotEqual(tx_id(a), tx_id(b))


class ChainRuleTests(unittest.TestCase):
    def setUp(self):
        self.chain = easy_chain()

    def test_honest_blocks_issue_rewards(self):
        for _ in range(5):
            add_block(self.chain)
        self.assertEqual(self.chain.height, 5)
        self.assertEqual(self.chain.issued, 5 * BLOCK_REWARD)
        self.assertEqual(self.chain.balance(MINER), 5 * BLOCK_REWARD)

    def test_rejects_wrong_previous_hash(self):
        block = self.chain.prepare_block(MINER, [], self.chain.tip.header.timestamp + 60)
        block.header.prev_hash = "ab" * 32
        mine_full(block)
        with self.assertRaisesRegex(ChainError, "previous block hash"):
            self.chain.append(block)

    def test_rejects_non_increasing_timestamp(self):
        block = self.chain.prepare_block(MINER, [], self.chain.tip.header.timestamp)
        block.header.timestamp = self.chain.tip.header.timestamp
        mine_full(block)
        with self.assertRaisesRegex(ChainError, "timestamp"):
            self.chain.append(block)

    def test_rejects_inflated_reward(self):
        block = self.chain.prepare_block(MINER, [], self.chain.tip.header.timestamp + 60)
        block.coinbase["reward"] += 1
        block.header.merkle_root = block.compute_merkle()
        mine_full(block)
        with self.assertRaisesRegex(ChainError, "bad block reward"):
            self.chain.append(block)

    def test_rejects_tampered_contents_without_fixing_merkle(self):
        block = self.chain.prepare_block(MINER, [], self.chain.tip.header.timestamp + 60)
        block.coinbase["miner"] = OTHER
        mine_full(block)
        with self.assertRaisesRegex(ChainError, "merkle"):
            self.chain.append(block)

    def test_rejects_wrong_difficulty(self):
        block = self.chain.prepare_block(MINER, [], self.chain.tip.header.timestamp + 60)
        block.header.difficulty = 999
        mine_full(block)
        with self.assertRaisesRegex(ChainError, "bad difficulty"):
            self.chain.append(block)

    def test_rejects_block_failing_proof_of_work(self):
        chain = Chain(genesis_block(1 << 12))
        block = chain.prepare_block(MINER, [], chain.tip.header.timestamp + 60)
        block.header.nonce = 0
        while block.header.meets_target():
            block.header.nonce += 1
        with self.assertRaisesRegex(ChainError, "proof of work"):
            chain.append(block)

    def test_transfer_moves_funds_and_blocks_replay(self):
        sender = Wallet.create()
        add_block(self.chain, miner=sender.address)
        tx = sender.make_transfer(OTHER, 100, 2, 0)
        add_block(self.chain, txs=[tx])
        self.assertEqual(self.chain.balance(OTHER), 100)
        self.assertEqual(self.chain.balance(sender.address), BLOCK_REWARD - 102)
        self.assertEqual(self.chain.next_nonce(sender.address), 1)
        # The same transaction again must fail: its nonce is used up.
        self.assertIsNotNone(check_tx(tx, dict(self.chain.balances), dict(self.chain.nonces)))

    def test_overspend_rejected(self):
        broke = Wallet.create()
        tx = broke.make_transfer(OTHER, 50, 1, 0)
        self.assertEqual(check_tx(tx, self.chain.balances, self.chain.nonces), "insufficient balance")

    def test_bad_signature_rejected(self):
        sender = Wallet.create()
        add_block(self.chain, miner=sender.address)
        tx = sender.make_transfer(OTHER, 100, 1, 0)
        tx["amount"] = 900
        self.assertEqual(check_tx(tx, self.chain.balances, self.chain.nonces), "bad signature")

    def test_wrong_network_rejected(self):
        sender = Wallet.create()
        add_block(self.chain, miner=sender.address)
        tx = sender.make_transfer(OTHER, 100, 1, 0)
        tx["network"] = "some-other-network"
        self.assertEqual(check_tx(tx, self.chain.balances, self.chain.nonces),
                         "transaction is for a different network")

    def test_block_with_invalid_transaction_rejected(self):
        broke = Wallet.create()
        tx = broke.make_transfer(OTHER, 50, 1, 0)
        block = self.chain.prepare_block(MINER, [], self.chain.tip.header.timestamp + 60)
        block.txs = [tx]
        block.coinbase["reward"] = BLOCK_REWARD + 1
        block.header.merkle_root = block.compute_merkle()
        mine_full(block)
        with self.assertRaises(ChainError):
            self.chain.append(block)


class ChainSelectionTests(unittest.TestCase):
    def test_longer_valid_chain_replaces_shorter(self):
        a, b = easy_chain(), easy_chain()
        for _ in range(3):
            add_block(a)
        for _ in range(5):
            add_block(b)
        self.assertTrue(a.consider(b.blocks))
        self.assertEqual(a.height, 5)

    def test_shorter_chain_does_not_replace_longer(self):
        a, b = easy_chain(), easy_chain()
        for _ in range(5):
            add_block(a)
        for _ in range(3):
            add_block(b)
        self.assertFalse(a.consider(b.blocks))
        self.assertEqual(a.height, 5)

    def test_forged_candidate_rejected_and_local_chain_kept(self):
        a, b = easy_chain(), easy_chain()
        add_block(a)
        before = a.tip.hash()
        for _ in range(6):
            add_block(b)
        forged = list(b.blocks)
        forged[3].coinbase["reward"] += 1
        with self.assertRaises(ChainError):
            a.consider(forged)
        self.assertEqual(a.tip.hash(), before)

    def test_different_genesis_rejected(self):
        a = easy_chain()
        other = Chain(genesis_block(2))
        add_block(other)
        with self.assertRaisesRegex(ChainError, "genesis"):
            a.consider(other.blocks)

    def test_save_and_load_round_trip(self):
        import os, tempfile
        chain = easy_chain()
        for _ in range(3):
            add_block(chain)
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "chain.json")
            chain.save(path)
            loaded = Chain.load(path)
        self.assertEqual(loaded.tip.hash(), chain.tip.hash())
        self.assertEqual(loaded.balance(MINER), chain.balance(MINER))


if __name__ == "__main__":
    unittest.main()
