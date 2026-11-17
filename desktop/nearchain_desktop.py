#!/usr/bin/env python3
"""NearChain desktop client. Tabs: Miner Mode, Trader Mode, Explorer, Network."""

import os
import sys
import threading
import tkinter as tk
from tkinter import messagebox, simpledialog, ttk

if getattr(sys, "frozen", False):
    ROOT = os.path.dirname(sys.executable)          # packaged .exe: files sit next to it
else:
    ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    sys.path.insert(0, os.path.join(ROOT, "src"))

from nearchain.chain import Chain, ChainError, genesis_block  # noqa: E402
from nearchain.config import load_config  # noqa: E402
from nearchain.consensus import GENESIS_DIFFICULTY, MIN_FEE, NETWORK_NAME  # noqa: E402
from nearchain.emission import reward_at  # noqa: E402
from nearchain.miner import Miner  # noqa: E402
from nearchain.network import Node  # noqa: E402
from nearchain.wallet import Wallet, is_valid_address, load_keystore, save_keystore  # noqa: E402

BG = "#d4d0c8"
FIELD = "#ffffff"
TITLE = "NearChain"


class App:
    def __init__(self, root):
        self.root = root
        cfg_dir = os.path.join(ROOT, "config")
        self.cfg = load_config(os.path.join(cfg_dir, "nearchain.yaml"), os.path.join(cfg_dir, "windows.yaml"))
        data_dir = os.path.join(ROOT, self.cfg["node"]["data_dir"])
        os.makedirs(data_dir, exist_ok=True)
        self.chain_path = os.path.join(data_dir, self.cfg["storage"]["chain_file"])
        self.wallet_path = os.path.join(data_dir, self.cfg["storage"]["wallet_file"])

        self.chain = self._load_chain()
        self.node = Node(self.chain, host=self.cfg["node"]["listen_host"],
                         port=self.cfg["node"]["listen_port"], data_path=self.chain_path)
        self.wallet = None
        self.miner = None
        self._log_seen = 0
        self._net_seen = 0
        self._history_height = -1
        self._explorer_count = -1

        self._build_ui()
        self._start_network()
        self._unlock_wallet()
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)
        self._refresh()

    # ----- startup -----
    def _load_chain(self):
        default = Chain(genesis_block(GENESIS_DIFFICULTY))
        if not os.path.exists(self.chain_path):
            return default
        try:
            loaded = Chain.load(self.chain_path)
            if loaded.blocks[0].hash() == default.blocks[0].hash():
                return loaded
        except (ChainError, ValueError, KeyError, TypeError, OSError):
            pass
        os.replace(self.chain_path, self.chain_path + ".bad")  # keep the damaged file for inspection
        return default

    def _start_network(self):
        try:
            self.node.start()
        except OSError as exc:
            messagebox.showwarning(TITLE, f"Could not listen for peers: {exc}", parent=self.root)
        for entry in self.cfg["node"]["peers"]:
            threading.Thread(target=self._connect_peer, args=(entry,), daemon=True).start()

    def _connect_peer(self, entry):
        try:
            host, port = entry.rsplit(":", 1)
            self.node.connect(host, int(port))
        except (OSError, ValueError) as exc:
            self.node.log(f"could not connect to {entry}: {exc}")

    def _unlock_wallet(self):
        if os.path.exists(self.wallet_path):
            while True:
                password = simpledialog.askstring(TITLE, "Wallet password:", show="*", parent=self.root)
                if password is None:
                    return
                try:
                    self.wallet = load_keystore(self.wallet_path, password)
                    break
                except ValueError as exc:
                    if not messagebox.askretrycancel(TITLE, str(exc), parent=self.root):
                        return
        else:
            if not messagebox.askyesno(TITLE, "No wallet found. Create one now?", parent=self.root):
                return
            while True:
                password = simpledialog.askstring(TITLE, "Choose a wallet password:", show="*", parent=self.root)
                if not password:
                    return
                if simpledialog.askstring(TITLE, "Repeat the password:", show="*", parent=self.root) != password:
                    messagebox.showerror(TITLE, "Passwords do not match.", parent=self.root)
                    continue
                break
            self.wallet = Wallet.create()
            save_keystore(self.wallet_path, self.wallet, password)
        self.miner = Miner(self.node, self.wallet.address,
                           mode=self.cfg["mining"]["default_mode"],
                           batch_size=self.cfg["mining"]["batch_size"],
                           background_sleep_ms=self.cfg["mining"]["background_sleep_ms"])
        self.mode_var.set(self.cfg["mining"]["default_mode"])
        self.address_var.set(self.wallet.address)

    # ----- UI construction -----
    def _build_ui(self):
        root = self.root
        root.title(f"{TITLE} - {NETWORK_NAME}")
        root.geometry("780x580")
        root.configure(bg=BG)
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("TNotebook", background=BG)
        style.configure("TNotebook.Tab", padding=(10, 3))

        menubar = tk.Menu(root)
        file_menu = tk.Menu(menubar, tearoff=0)
        file_menu.add_command(label="Exit", command=self.on_close)
        menubar.add_cascade(label="File", menu=file_menu)
        root.config(menu=menubar)

        self.vars = {k: tk.StringVar(value="-") for k in
                     ("balance", "supply", "height", "difficulty", "peers", "pending", "reward")}
        status = tk.LabelFrame(root, text="Network status", bg=BG, padx=6, pady=4)
        status.pack(fill="x", padx=8, pady=6)
        labels = [("Wallet balance", "balance"), ("Total issued", "supply"), ("Block height", "height"),
                  ("Difficulty", "difficulty"), ("Peers", "peers"), ("Pending transactions", "pending"),
                  ("Next reward", "reward")]
        for i, (text, key) in enumerate(labels):
            tk.Label(status, text=text + ":", bg=BG).grid(row=i // 4, column=(i % 4) * 2, sticky="e", padx=4, pady=2)
            tk.Label(status, textvariable=self.vars[key], bg=BG, width=18, anchor="w",
                     relief="sunken", bd=1).grid(row=i // 4, column=(i % 4) * 2 + 1, sticky="w", padx=4)

        self.notebook = ttk.Notebook(root)
        self.notebook.pack(fill="both", expand=True, padx=8, pady=(0, 8))
        self._build_miner_tab()
        self._build_trader_tab()
        self._build_explorer_tab()
        self._build_network_tab()

    def _panel(self, parent):
        frame = tk.Frame(parent, bg=BG, padx=10, pady=10)
        frame.pack(fill="both", expand=True)
        return frame

    def _build_miner_tab(self):
        tab = self._panel(self.notebook)
        self.notebook.add(tab, text="Miner Mode")
        self.mode_var = tk.StringVar(value="foreground")
        top = tk.Frame(tab, bg=BG)
        top.pack(fill="x")
        tk.Label(top, text="Mining mode:", bg=BG).pack(side="left")
        for mode in ("foreground", "background"):
            tk.Radiobutton(top, text=mode.capitalize(), value=mode, variable=self.mode_var, bg=BG,
                           command=self._set_mode).pack(side="left", padx=4)
        tk.Button(top, text="Start mining", width=12, command=self._start_mining).pack(side="left", padx=(20, 4))
        tk.Button(top, text="Stop", width=8, command=self._stop_mining).pack(side="left")

        grid = tk.LabelFrame(tab, text="Mining statistics", bg=BG, padx=6, pady=6)
        grid.pack(fill="x", pady=8)
        self.mine_vars = {k: tk.StringVar(value="-") for k in
                          ("state", "nonce", "hashrate", "difficulty", "blocks", "reward")}
        rows = [("Status", "state"), ("Current nonce", "nonce"), ("Hash rate", "hashrate"),
                ("Difficulty", "difficulty"), ("Blocks mined", "blocks"), ("Reward per block", "reward")]
        for i, (text, key) in enumerate(rows):
            tk.Label(grid, text=text + ":", bg=BG).grid(row=i // 2, column=(i % 2) * 2, sticky="e", padx=4, pady=2)
            tk.Label(grid, textvariable=self.mine_vars[key], bg=BG, width=24, anchor="w").grid(
                row=i // 2, column=(i % 2) * 2 + 1, sticky="w", padx=4)

        log_frame = tk.LabelFrame(tab, text="Mining log", bg=BG, padx=4, pady=4)
        log_frame.pack(fill="both", expand=True)
        self.mine_log = tk.Text(log_frame, height=10, bg=FIELD, state="disabled", wrap="word")
        scroll = tk.Scrollbar(log_frame, command=self.mine_log.yview)
        self.mine_log.configure(yscrollcommand=scroll.set)
        self.mine_log.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

    def _build_trader_tab(self):
        tab = self._panel(self.notebook)
        self.notebook.add(tab, text="Trader Mode")
        self.address_var = tk.StringVar(value="(no wallet)")
        info = tk.LabelFrame(tab, text="Your wallet", bg=BG, padx=6, pady=6)
        info.pack(fill="x")
        tk.Label(info, text="Address:", bg=BG).grid(row=0, column=0, sticky="e")
        tk.Entry(info, textvariable=self.address_var, width=52, state="readonly").grid(row=0, column=1, sticky="w", padx=4)

        send = tk.LabelFrame(tab, text="Send CDC", bg=BG, padx=6, pady=6)
        send.pack(fill="x", pady=8)
        self.recipient = tk.Entry(send, width=52)
        self.amount = tk.Entry(send, width=14)
        self.fee = tk.Entry(send, width=8)
        self.fee.insert(0, str(MIN_FEE))
        for row, (label, widget) in enumerate([("Recipient:", self.recipient), ("Amount (CDC):", self.amount),
                                               ("Fee (CDC):", self.fee)]):
            tk.Label(send, text=label, bg=BG).grid(row=row, column=0, sticky="e", pady=2)
            widget.grid(row=row, column=1, sticky="w", padx=4, pady=2)
        tk.Button(send, text="Send", width=12, command=self._send).grid(row=3, column=1, sticky="w", padx=4, pady=4)

        hist = tk.LabelFrame(tab, text="Transaction history", bg=BG, padx=4, pady=4)
        hist.pack(fill="both", expand=True)
        self.history_list = tk.Listbox(hist, bg=FIELD, font=("Courier", 9))
        scroll = tk.Scrollbar(hist, command=self.history_list.yview)
        self.history_list.configure(yscrollcommand=scroll.set)
        self.history_list.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

    def _build_explorer_tab(self):
        tab = self._panel(self.notebook)
        self.notebook.add(tab, text="Explorer")
        pane = tk.PanedWindow(tab, orient="horizontal", bg=BG)
        pane.pack(fill="both", expand=True)
        left = tk.Frame(pane, bg=BG)
        self.block_list = tk.Listbox(left, bg=FIELD, font=("Courier", 9), width=34)
        scroll = tk.Scrollbar(left, command=self.block_list.yview)
        self.block_list.configure(yscrollcommand=scroll.set)
        self.block_list.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        self.block_list.bind("<<ListboxSelect>>", self._show_block)
        self.block_detail = tk.Text(pane, bg=FIELD, font=("Courier", 9), wrap="word", state="disabled")
        pane.add(left)
        pane.add(self.block_detail)

    def _build_network_tab(self):
        tab = self._panel(self.notebook)
        self.notebook.add(tab, text="Network")
        top = tk.Frame(tab, bg=BG)
        top.pack(fill="x")
        tk.Label(top, text="Connect to peer (host:port):", bg=BG).pack(side="left")
        self.peer_entry = tk.Entry(top, width=28)
        self.peer_entry.pack(side="left", padx=4)
        tk.Button(top, text="Connect", command=self._connect_from_ui).pack(side="left")
        self.peer_list = tk.Listbox(tab, height=5, bg=FIELD)
        self.peer_list.pack(fill="x", pady=6)
        log_frame = tk.LabelFrame(tab, text="Network log", bg=BG, padx=4, pady=4)
        log_frame.pack(fill="both", expand=True)
        self.net_log = tk.Text(log_frame, height=10, bg=FIELD, state="disabled", wrap="word")
        scroll = tk.Scrollbar(log_frame, command=self.net_log.yview)
        self.net_log.configure(yscrollcommand=scroll.set)
        self.net_log.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

    # ----- actions -----
    def _set_mode(self):
        if self.miner:
            self.miner.mode = self.mode_var.get()

    def _start_mining(self):
        if not self.miner:
            messagebox.showinfo(TITLE, "Create or unlock a wallet first.", parent=self.root)
            return
        self._set_mode()
        self.miner.start()

    def _stop_mining(self):
        if self.miner:
            self.miner.stop()

    def _connect_from_ui(self):
        entry = self.peer_entry.get().strip()
        threading.Thread(target=self._connect_peer, args=(entry,), daemon=True).start()

    def _send(self):
        if not self.wallet:
            messagebox.showinfo(TITLE, "Unlock a wallet first.", parent=self.root)
            return
        recipient = self.recipient.get().strip()
        if not is_valid_address(recipient):
            messagebox.showerror(TITLE, "That is not a valid CDC address.", parent=self.root)
            return
        try:
            amount = int(self.amount.get())
            fee = int(self.fee.get())
        except ValueError:
            messagebox.showerror(TITLE, "Amount and fee must be whole numbers of CDC.", parent=self.root)
            return
        tx = self.wallet.make_transfer(recipient, amount, fee, self.node.pending_nonce(self.wallet.address))
        ok, reason = self.node.submit_tx(tx, relay=True)
        if ok:
            messagebox.showinfo(TITLE, "Sent to the network. It will confirm when a miner includes it.", parent=self.root)
            self.amount.delete(0, "end")
        else:
            messagebox.showerror(TITLE, f"Transaction refused: {reason}", parent=self.root)

    def _show_block(self, _event=None):
        selection = self.block_list.curselection()
        if not selection:
            return
        index = selection[0]
        with self.node.lock:
            block = self.chain.blocks[len(self.chain.blocks) - 1 - index]
            text = self._block_text(block)
        self.block_detail.configure(state="normal")
        self.block_detail.delete("1.0", "end")
        self.block_detail.insert("1.0", text)
        self.block_detail.configure(state="disabled")

    @staticmethod
    def _block_text(block):
        h = block.header
        lines = [
            f"Height:        {h.height}",
            f"Hash:          {block.hash()}",
            f"Previous hash: {h.prev_hash}",
            f"Merkle root:   {h.merkle_root}",
            f"Timestamp:     {h.timestamp}",
            f"Difficulty:    {h.difficulty}",
            f"Nonce:         {h.nonce}",
            f"Miner:         {block.coinbase['miner']}",
            f"Reward:        {block.coinbase['reward']} CDC",
            f"Transactions:  {len(block.txs)}",
        ]
        for tx in block.txs:
            lines.append(f"  {tx['sender']} -> {tx['recipient']}  {tx['amount']} CDC  fee {tx['fee']}")
        return "\n".join(lines)

    # ----- periodic refresh -----
    def _append_text(self, widget, lines, seen):
        if len(lines) > seen:
            widget.configure(state="normal")
            widget.insert("end", "\n".join(lines[seen:]) + "\n")
            widget.see("end")
            widget.configure(state="disabled")
        return len(lines)

    def _refresh(self):
        with self.node.lock:
            height = self.chain.height
            difficulty = self.chain.expected_difficulty()
            issued = self.chain.issued
            pending = len(self.node.mempool)
            balance = self.chain.balance(self.wallet.address) if self.wallet else 0
            blocks = len(self.chain.blocks)
            history = self.chain.history(self.wallet.address) if self.wallet else []
        self.vars["balance"].set(f"{balance:,} CDC" if self.wallet else "-")
        self.vars["supply"].set(f"{issued:,} CDC")
        self.vars["height"].set(str(height))
        self.vars["difficulty"].set(f"{difficulty:,}")
        self.vars["peers"].set(str(len(self.node.peers)))
        self.vars["pending"].set(str(pending))
        self.vars["reward"].set(f"{reward_at(height + 1):,} CDC")

        if self.miner:
            m = self.miner
            state = "mining" if m.running else "stopped"
            self.mine_vars["state"].set(f"{state} ({m.mode})")
            self.mine_vars["nonce"].set(f"{m.current_nonce:,}")
            self.mine_vars["hashrate"].set(f"{m.hashrate:,.0f} hashes/s")
            self.mine_vars["difficulty"].set(f"{m.current_difficulty:,}")
            self.mine_vars["blocks"].set(str(m.blocks_mined))
            self.mine_vars["reward"].set(f"{reward_at(height + 1):,} CDC")
            self._log_seen = self._append_text(self.mine_log, m.log_lines, self._log_seen)

        self._net_seen = self._append_text(self.net_log, self.node.events, self._net_seen)
        self.peer_list.delete(0, "end")
        for peer in list(self.node.peers):
            self.peer_list.insert("end", f"{peer.addr[0]}:{peer.addr[1]}")

        if history and height != self._history_height:
            self.history_list.delete(0, "end")
            for row in reversed(history):
                other = row["other"][:14] + "..." if row["other"] else ""
                self.history_list.insert("end", f"#{row['height']:<8} {row['kind']:<9} {row['amount']:>14,}  {other}")
            self._history_height = height

        if blocks != self._explorer_count:
            self.block_list.delete(0, "end")
            for block in reversed(list(self.chain.blocks)):
                self.block_list.insert("end", f"#{block.header.height:<8} {block.hash()[:16]}...")
            self._explorer_count = blocks

        self.root.after(self.cfg["ui"]["refresh_ms"], self._refresh)

    def on_close(self):
        if self.miner:
            self.miner.stop()
        self.node.stop()
        self.root.destroy()


def main():
    root = tk.Tk()
    App(root)
    root.mainloop()


if __name__ == "__main__":
    main()
