import os
import tempfile
import unittest

from nearchain.config import ConfigError, load_config
from nearchain.wallet import Wallet, load_keystore, save_keystore


class ConfigTests(unittest.TestCase):
    def write(self, folder, name, text):
        path = os.path.join(folder, name)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        return path

    def test_defaults_when_files_missing(self):
        cfg = load_config("does-not-exist.yaml", None)
        self.assertEqual(cfg["node"]["listen_port"], 8333)

    def test_valid_files_merge(self):
        with tempfile.TemporaryDirectory() as tmp:
            shared = self.write(tmp, "c.yaml", "node:\n  listen_port: 9000\n")
            plat = self.write(tmp, "w.yaml", "mining:\n  default_mode: background\n")
            cfg = load_config(shared, plat)
        self.assertEqual(cfg["node"]["listen_port"], 9000)
        self.assertEqual(cfg["mining"]["default_mode"], "background")

    def test_consensus_parameter_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            shared = self.write(tmp, "c.yaml", "max_supply: 99999999999\n")
            with self.assertRaisesRegex(ConfigError, "consensus parameter"):
                load_config(shared, None)

    def test_out_of_range_value_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            shared = self.write(tmp, "c.yaml", "node:\n  listen_port: 99999\n")
            with self.assertRaisesRegex(ConfigError, "between"):
                load_config(shared, None)

    def test_unknown_key_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            shared = self.write(tmp, "c.yaml", "node:\n  listen_prot: 1\n")
            with self.assertRaisesRegex(ConfigError, "unknown"):
                load_config(shared, None)


class KeystoreTests(unittest.TestCase):
    def test_round_trip_and_wrong_password(self):
        wallet = Wallet.create()
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "wallet.json")
            save_keystore(path, wallet, "correct horse")
            loaded = load_keystore(path, "correct horse")
            self.assertEqual(loaded.address, wallet.address)
            with self.assertRaises(ValueError):
                load_keystore(path, "wrong")
            with open(path, encoding="utf-8") as f:
                self.assertNotIn(wallet.seed().hex(), f.read())


if __name__ == "__main__":
    unittest.main()
