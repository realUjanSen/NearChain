"""Keys, addresses, transaction signing, and the encrypted keystore.

Signatures use Ed25519 and key encryption uses scrypt + AES-GCM, both from the
`cryptography` library. No custom cryptographic primitives are used."""

import hashlib
import json
import os

from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.primitives.serialization import Encoding, NoEncryption, PrivateFormat, PublicFormat

from .consensus import ADDRESS_PREFIX, NETWORK_ID

KEYSTORE_VERSION = 1
_KEYSTORE_AAD = b"nearchain-keystore-v1"


def address_from_pubkey(raw_pubkey: bytes) -> str:
    return ADDRESS_PREFIX + hashlib.sha256(raw_pubkey).digest()[:20].hex()


def is_valid_address(addr) -> bool:
    if not isinstance(addr, str) or not addr.startswith(ADDRESS_PREFIX):
        return False
    body = addr[len(ADDRESS_PREFIX):]
    if len(body) != 40:
        return False
    try:
        bytes.fromhex(body)
    except ValueError:
        return False
    return True


def signing_bytes(tx: dict) -> bytes:
    body = {k: tx[k] for k in ("sender_pub", "sender", "recipient", "amount", "fee", "nonce", "network")}
    return json.dumps(body, sort_keys=True, separators=(",", ":")).encode("utf-8")


def tx_id(tx: dict) -> str:
    data = json.dumps(tx, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(data).hexdigest()


def verify_tx_signature(tx: dict) -> bool:
    try:
        raw = bytes.fromhex(tx["sender_pub"])
        if address_from_pubkey(raw) != tx["sender"]:
            return False
        Ed25519PublicKey.from_public_bytes(raw).verify(bytes.fromhex(tx["signature"]), signing_bytes(tx))
        return True
    except (KeyError, ValueError, TypeError, InvalidSignature):
        return False


class Wallet:
    def __init__(self, private_key: Ed25519PrivateKey):
        self._key = private_key
        self.public_key = private_key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
        self.address = address_from_pubkey(self.public_key)

    @classmethod
    def create(cls):
        return cls(Ed25519PrivateKey.generate())

    @classmethod
    def from_seed(cls, seed: bytes):
        return cls(Ed25519PrivateKey.from_private_bytes(seed))

    def seed(self) -> bytes:
        return self._key.private_bytes(Encoding.Raw, PrivateFormat.Raw, NoEncryption())

    def make_transfer(self, recipient: str, amount: int, fee: int, nonce: int) -> dict:
        tx = {
            "sender_pub": self.public_key.hex(),
            "sender": self.address,
            "recipient": recipient,
            "amount": amount,
            "fee": fee,
            "nonce": nonce,
            "network": NETWORK_ID,
        }
        tx["signature"] = self._key.sign(signing_bytes(tx)).hex()
        return tx


def _derive_key(password: str, salt: bytes) -> bytes:
    return Scrypt(salt=salt, length=32, n=2**15, r=8, p=1).derive(password.encode("utf-8"))


def save_keystore(path: str, wallet: Wallet, password: str) -> None:
    salt = os.urandom(16)
    nonce = os.urandom(12)
    ciphertext = AESGCM(_derive_key(password, salt)).encrypt(nonce, wallet.seed(), _KEYSTORE_AAD)
    data = {
        "version": KEYSTORE_VERSION,
        "address": wallet.address,
        "salt": salt.hex(),
        "nonce": nonce.hex(),
        "ciphertext": ciphertext.hex(),
    }
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    os.replace(tmp, path)


def load_keystore(path: str, password: str) -> Wallet:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if data.get("version") != KEYSTORE_VERSION:
        raise ValueError("unsupported keystore version")
    try:
        seed = AESGCM(_derive_key(password, bytes.fromhex(data["salt"]))).decrypt(
            bytes.fromhex(data["nonce"]), bytes.fromhex(data["ciphertext"]), _KEYSTORE_AAD
        )
    except InvalidTag:
        raise ValueError("wrong password, or the wallet file is damaged")
    wallet = Wallet.from_seed(seed)
    if wallet.address != data["address"]:
        raise ValueError("wallet file does not match its address")
    return wallet
