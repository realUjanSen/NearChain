"""Background miner. Foreground mode mines flat out; background mode sleeps between batches."""

import threading
import time

from .block import mine_batch


class Miner:
    def __init__(self, node, address, mode="foreground", batch_size=5000, background_sleep_ms=50):
        self.node = node
        self.address = address
        self.mode = mode
        self.batch_size = batch_size
        self.background_sleep = background_sleep_ms / 1000.0
        self.hashes_total = 0
        self.blocks_mined = 0
        self.hashrate = 0.0
        self.current_nonce = 0
        self.current_difficulty = 0
        self.log_lines = []
        self._stop = threading.Event()
        self._thread = None

    @property
    def running(self) -> bool:
        return self._thread is not None and self._thread.is_alive() and not self._stop.is_set()

    def start(self) -> None:
        if self.running:
            return
        if not self.address:
            raise RuntimeError("a wallet is needed before mining")
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def join(self, timeout=None) -> None:
        if self._thread is not None:
            self._thread.join(timeout)

    def _log(self, text: str) -> None:
        self.log_lines.append(f"{time.strftime('%H:%M:%S')}  {text}")
        if len(self.log_lines) > 500:
            del self.log_lines[:100]

    def _run(self) -> None:
        self._log(f"mining started ({self.mode} mode)")
        while not self._stop.is_set():
            block = self.node.prepare_mining_block(self.address)
            self.current_difficulty = block.header.difficulty
            found = False
            window_start, window_hashes = time.time(), 0
            while not self._stop.is_set():
                found = mine_batch(block, self.batch_size)
                self.hashes_total += self.batch_size
                window_hashes += self.batch_size
                self.current_nonce = block.header.nonce
                elapsed = time.time() - window_start
                if elapsed >= 1.0:
                    self.hashrate = window_hashes / elapsed
                    window_start, window_hashes = time.time(), 0
                if found:
                    break
                if self.mode == "background" and self.background_sleep > 0:
                    time.sleep(self.background_sleep)
                if self.node.tip_hash() != block.header.prev_hash:
                    break  # another block arrived first; start over on the new tip
            if found:
                if self.node.submit_block(block):
                    self.blocks_mined += 1
                    self._log(f"mined block {block.header.height} (reward {block.coinbase['reward']} CDC)")
                else:
                    self._log("block discarded: the chain moved on first")
        self._log("mining stopped")
