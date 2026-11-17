"""Peer-to-peer networking. Peers exchange newline-delimited JSON over TCP.

Messages:
  hello      {network, version, height, work}   sent by both sides on connect
  get_chain  {}                                 ask for the peer's full chain
  chain      {blocks: [...]}                    reply to get_chain
  tx         {tx: {...}}                        relay a transaction
  block      {block: {...}}                     relay a newly mined block
"""

import json
import socket
import threading
import time

from .block import Block
from .chain import ChainError, apply_tx, check_tx
from .consensus import MAX_MESSAGE_BYTES, NETWORK_ID, PROTOCOL_VERSION
from .wallet import tx_id


class ProtocolError(Exception):
    pass


class Peer:
    def __init__(self, sock: socket.socket, addr):
        self.sock = sock
        self.addr = addr
        self.reader = sock.makefile("rb")
        self.write_lock = threading.Lock()
        self.alive = True

    def send(self, message: dict) -> None:
        data = (json.dumps(message, separators=(",", ":")) + "\n").encode("utf-8")
        with self.write_lock:
            try:
                self.sock.sendall(data)
            except OSError:
                self.alive = False

    def read(self):
        line = self.reader.readline(MAX_MESSAGE_BYTES + 1)
        if not line:
            return None
        if len(line) > MAX_MESSAGE_BYTES:
            raise ProtocolError("message too large")
        return json.loads(line.decode("utf-8"))

    def close(self) -> None:
        self.alive = False
        for action in (lambda: self.sock.shutdown(socket.SHUT_RDWR), self.sock.close):
            try:
                action()
            except OSError:
                pass


class Node:
    def __init__(self, chain, host="0.0.0.0", port=8333, data_path=None):
        self.chain = chain
        self.host = host
        self.port = port
        self.bound_port = None
        self.data_path = data_path
        self.lock = threading.RLock()
        self.mempool = {}          # tx_id -> transaction, in arrival order
        self.peers = []
        self.events = []
        self._server = None
        self._running = False

    # ----- lifecycle -----
    def log(self, text: str) -> None:
        self.events.append(f"{time.strftime('%H:%M:%S')}  {text}")
        if len(self.events) > 300:
            del self.events[:50]

    def start(self) -> None:
        self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._server.bind((self.host, self.port))
        self._server.listen(16)
        self.bound_port = self._server.getsockname()[1]
        self._running = True
        threading.Thread(target=self._accept_loop, daemon=True).start()
        self.log(f"listening on port {self.bound_port}")

    def stop(self) -> None:
        self._running = False
        if self._server is not None:
            try:
                self._server.close()
            except OSError:
                pass
        with self.lock:
            for peer in list(self.peers):
                peer.close()
            self.save()

    def save(self) -> None:
        if self.data_path:
            with self.lock:
                self.chain.save(self.data_path)

    def _accept_loop(self) -> None:
        while self._running:
            try:
                conn, addr = self._server.accept()
            except OSError:
                break
            self._add_peer(conn, addr)

    # ----- connections -----
    def connect(self, host: str, port: int) -> Peer:
        sock = socket.create_connection((host, int(port)), timeout=5)
        sock.settimeout(None)
        return self._add_peer(sock, (host, port))

    def _add_peer(self, sock, addr) -> Peer:
        peer = Peer(sock, addr)
        with self.lock:
            self.peers.append(peer)
        threading.Thread(target=self._peer_loop, args=(peer,), daemon=True).start()
        peer.send(self._hello())
        self.log(f"peer connected: {addr[0]}:{addr[1]}")
        return peer

    def _hello(self) -> dict:
        with self.lock:
            return {"type": "hello", "network": NETWORK_ID, "version": PROTOCOL_VERSION,
                    "height": self.chain.height, "work": self.chain.cumulative_work}

    def _peer_loop(self, peer: Peer) -> None:
        try:
            while self._running and peer.alive:
                message = peer.read()
                if message is None:
                    break
                self._handle(peer, message)
        except (ProtocolError, ChainError, ValueError, KeyError, TypeError, OSError) as exc:
            self.log(f"peer {peer.addr[0]} dropped: {exc}")
        finally:
            peer.close()
            with self.lock:
                if peer in self.peers:
                    self.peers.remove(peer)

    def broadcast(self, message: dict, exclude=None) -> None:
        with self.lock:
            targets = list(self.peers)
        for peer in targets:
            if peer is not exclude and peer.alive:
                peer.send(message)

    # ----- message handling -----
    def _handle(self, peer: Peer, msg) -> None:
        if not isinstance(msg, dict):
            raise ProtocolError("message is not an object")
        kind = msg.get("type")
        if kind == "hello":
            self._on_hello(peer, msg)
        elif kind == "get_chain":
            with self.lock:
                blocks = [b.to_dict() for b in self.chain.blocks]
            peer.send({"type": "chain", "blocks": blocks})
        elif kind == "chain":
            self._on_chain(msg)
        elif kind == "tx":
            ok, reason = self.submit_tx(msg.get("tx"), relay=True, source=peer)
            if not ok and reason != "already pending":
                self.log(f"rejected transaction from {peer.addr[0]}: {reason}")
        elif kind == "block":
            self._on_block(peer, msg)
        else:
            raise ProtocolError(f"unknown message type {kind!r}")

    def _on_hello(self, peer: Peer, msg: dict) -> None:
        if msg.get("network") != NETWORK_ID:
            raise ProtocolError("peer is on a different network")
        if msg.get("version") != PROTOCOL_VERSION:
            raise ProtocolError("protocol version mismatch")
        with self.lock:
            ours = self.chain.cumulative_work
        if int(msg.get("work", 0)) > ours:
            peer.send({"type": "get_chain"})

    def _on_chain(self, msg: dict) -> None:
        blocks = [Block.from_dict(d) for d in msg["blocks"]]
        with self.lock:
            try:
                replaced = self.chain.consider(blocks)
            except ChainError as exc:
                self.log(f"rejected peer chain: {exc}")
                return
            if replaced:
                self._prune_mempool()
                self.save()
                self.log(f"synced to height {self.chain.height}")

    def _on_block(self, peer: Peer, msg: dict) -> None:
        block = Block.from_dict(msg["block"])
        with self.lock:
            tip = self.chain.tip
            if block.header.height <= tip.header.height:
                return  # already have it or an older fork
            if block.header.prev_hash != tip.hash() or block.header.height != tip.header.height + 1:
                need_sync = True
                accepted = False
            else:
                need_sync = False
                try:
                    self.chain.append(block)
                except ChainError as exc:
                    self.log(f"rejected block {block.header.height}: {exc}")
                    return
                self._prune_mempool()
                self.save()
                accepted = True
                self.log(f"accepted block {block.header.height}")
        if accepted:
            self.broadcast({"type": "block", "block": msg["block"]}, exclude=peer)
        if need_sync:
            peer.send({"type": "get_chain"})

    # ----- transactions and blocks -----
    def _pending_state(self):
        balances, nonces = dict(self.chain.balances), dict(self.chain.nonces)
        for tx in self.mempool.values():
            apply_tx(tx, balances, nonces)
        return balances, nonces

    def _prune_mempool(self) -> None:
        balances, nonces = dict(self.chain.balances), dict(self.chain.nonces)
        kept = {}
        for tid, tx in self.mempool.items():
            if check_tx(tx, balances, nonces) is None:
                apply_tx(tx, balances, nonces)
                kept[tid] = tx
        self.mempool = kept

    def submit_tx(self, tx, relay=True, source=None):
        if not isinstance(tx, dict):
            return False, "not a transaction"
        try:
            tid = tx_id(tx)
        except (TypeError, ValueError):
            return False, "malformed transaction"
        with self.lock:
            if tid in self.mempool:
                return False, "already pending"
            balances, nonces = self._pending_state()
            reason = check_tx(tx, balances, nonces)
            if reason is not None:
                return False, reason
            self.mempool[tid] = tx
        if relay:
            self.broadcast({"type": "tx", "tx": tx}, exclude=source)
        return True, "accepted"

    def pending_nonce(self, address: str) -> int:
        with self.lock:
            confirmed = self.chain.next_nonce(address)
            return confirmed + sum(1 for t in self.mempool.values() if t["sender"] == address)

    def prepare_mining_block(self, miner: str) -> Block:
        with self.lock:
            txs = list(self.mempool.values())
            return self.chain.prepare_block(miner, txs, int(time.time()))

    def submit_block(self, block: Block) -> bool:
        with self.lock:
            if block.header.prev_hash != self.chain.tip.hash():
                return False
            try:
                self.chain.append(block)
            except ChainError as exc:
                self.log(f"own block rejected: {exc}")
                return False
            self._prune_mempool()
            self.save()
        self.broadcast({"type": "block", "block": block.to_dict()})
        self.log(f"mined block {block.header.height}")
        return True

    def tip_hash(self) -> str:
        with self.lock:
            return self.chain.tip.hash()
