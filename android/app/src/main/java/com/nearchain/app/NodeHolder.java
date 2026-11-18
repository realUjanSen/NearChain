package com.nearchain.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.nearchain.core.Block;
import com.nearchain.core.Chain;
import com.nearchain.core.ChainException;
import com.nearchain.core.Consensus;
import com.nearchain.core.Emission;
import com.nearchain.core.Mempool;
import com.nearchain.core.Tx;
import com.nearchain.net.Events;
import com.nearchain.net.NetNode;
import com.nearchain.wallet.Wallet;
import com.nearchain.wallet.WalletStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide state shared by the screens, the mining service, and the network.
 * The chain is kept in memory: after a restart the phone starts from genesis and syncs again.
 */
public final class NodeHolder {
    static final int PORT = 8333;

    private static Context appContext;
    private static Chain chain;
    private static Mempool mempool;
    private static NetNode net;
    private static Discovery discovery;
    private static BluetoothLink bluetooth;
    private static boolean networkStarted;
    private static volatile Wallet wallet;
    private static volatile boolean mining;
    private static volatile String lastEvent = "not mining";
    private static volatile long currentNonce;
    private static volatile long currentDifficulty;
    private static volatile double hashrate;
    private static volatile int blocksMined;
    private static volatile int logLimit = 200;
    private static final List<String> log = new ArrayList<>();

    private static final Events EVENTS = new Events() {
        @Override
        public void transferPending(Tx tx) {
            notifyIfMine(tx, "Incoming " + tx.amount + " CDC from " + short_(tx.sender)
                    + ". Waiting for a block to confirm it.");
        }

        @Override
        public void transferConfirmed(Tx tx) {
            notifyIfMine(tx, "Received " + tx.amount + " CDC from " + short_(tx.sender) + ". Confirmed in a block.");
        }
    };

    private NodeHolder() {
    }

    /** Called once at startup. Gives the holder a context for toasts and the wallet file. */
    public static synchronized void attach(Context context) {
        appContext = context.getApplicationContext();
    }

    public static synchronized void init() {
        if (chain == null) {
            try {
                chain = new Chain(Chain.genesisBlock(Consensus.GENESIS_DIFFICULTY));
            } catch (ChainException e) {
                throw new IllegalStateException("genesis failed to validate", e);
            }
            mempool = new Mempool(chain);
            net = new NetNode(chain, mempool, NodeHolder::log, EVENTS);
        }
    }

    /** Start listening for peers and advertising on the local network. Safe to call more than once. */
    public static synchronized void startNetwork(Context context) {
        init();
        if (networkStarted) {
            return;
        }
        networkStarted = true;
        try {
            int port = net.start(PORT);
            discovery = new Discovery(context, net, NodeHolder::log);
            discovery.start(port);
        } catch (IOException e) {
            log("could not start networking: " + e.getMessage());
        }
    }

    public static Chain chain() {
        init();
        return chain;
    }

    public static NetNode net() {
        init();
        return net;
    }

    public static Wallet wallet() {
        return wallet;
    }

    public static void setWallet(Wallet value) {
        wallet = value;
        init();
        net.setLocalAddress(value == null ? "" : value.address());
    }

    /** The Bluetooth link. Null until startBluetooth() has been called. */
    public static BluetoothLink bluetooth() {
        return bluetooth;
    }

    /** Starts the Bluetooth listener. Call only after the Bluetooth permissions are granted. */
    public static synchronized String startBluetooth() {
        init();
        if (bluetooth == null) {
            bluetooth = new BluetoothLink(net, NodeHolder::log);
        }
        if (!bluetooth.available()) {
            return "Bluetooth is off or not available on this phone.";
        }
        try {
            bluetooth.startServer();
            return "Bluetooth link is on.";
        } catch (IOException | SecurityException e) {
            return "Could not start the Bluetooth link: " + e.getMessage();
        }
    }

    /** Builds the next block on the tip, including pending transfers. The caller mines it. */
    public static Block prepareMiningBlock(String miner) {
        init();
        synchronized (chain) {
            return chain.prepareBlock(miner, mempool.pending(), System.currentTimeMillis() / 1000L);
        }
    }

    /** Adds a block this phone mined to the chain, and sends it to peers. */
    public static void submitMined(Block block) throws ChainException {
        init();
        synchronized (chain) {
            chain.append(block);
        }
        mempool.removeMined(block);
        net.broadcastBlock(block, null);
        blocksMined++;
        log("mined block " + block.header.height + " (reward " + block.reward + " CDC)");
        for (Tx t : block.txs) {
            EVENTS.transferConfirmed(t);
        }
    }

    /** Adds a signed transfer to the pending pool and sends it to peers. Returns null, or the reason it was refused. */
    public static String submitTransfer(Tx tx) {
        init();
        String reason = mempool.add(tx);
        if (reason == null) {
            net.broadcastTx(tx, null);
            int peers = net.peerCount();
            log("transfer queued: " + tx.amount + " CDC to " + tx.recipient + (peers == 0
                    ? ". No peers are connected, so it will be sent when one connects."
                    : ". Sent to " + peers + " peer(s). Waiting for a miner to include it."));
        }
        return reason;
    }

    /** The nonce the next transfer from this address must use. */
    public static long pendingNonce(String address) {
        init();
        return mempool.pendingNonce(address);
    }

    public static synchronized void log(String text) {
        String stamped = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()) + "  " + text;
        log.add(stamped);
        while (log.size() > logLimit) {
            log.remove(0);
        }
        lastEvent = text;
    }

    /** How many log lines to keep. The user sets this in the app. */
    public static synchronized void setLogLimit(int lines) {
        logLimit = lines;
        while (log.size() > logLimit) {
            log.remove(0);
        }
    }

    public static synchronized String logText() {
        StringBuilder sb = new StringBuilder();
        for (String line : log) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static String peersText() {
        init();
        return net.describePeers();
    }

    public static void setMining(boolean value) {
        mining = value;
    }

    public static void recordAttempt(long nonce, long difficulty, double rate) {
        currentNonce = nonce;
        currentDifficulty = difficulty;
        hashrate = rate;
    }

    public static boolean isMining() {
        return mining;
    }

    public static int blocksMined() {
        return blocksMined;
    }

    public static long currentNonce() {
        return currentNonce;
    }

    public static long currentDifficulty() {
        return currentDifficulty;
    }

    public static double hashrate() {
        return hashrate;
    }

    public static String lastEvent() {
        return lastEvent;
    }

    /** Mining rewards and transfers for this address, for the Trader Mode history. */
    public static String history(String address) {
        StringBuilder sb = new StringBuilder();
        for (Block b : chain().blocks()) {
            if (b.miner.equals(address) && b.reward > 0) {
                sb.append("#").append(b.header.height).append("  mined  +")
                        .append(b.reward).append(" CDC\n");
            }
            for (Tx t : b.txs) {
                if (t.sender.equals(address)) {
                    sb.append("#").append(b.header.height).append("  sent  -")
                            .append(t.amount).append(" CDC to ").append(short_(t.recipient)).append('\n');
                } else if (t.recipient.equals(address)) {
                    sb.append("#").append(b.header.height).append("  received  +")
                            .append(t.amount).append(" CDC from ").append(short_(t.sender)).append('\n');
                }
            }
        }
        return sb.length() == 0 ? "No transactions yet." : sb.toString();
    }

    public static long rewardForNextBlock() {
        return Emission.rewardAt(chain().height() + 1);
    }

    /** Shows a toast on the main thread. Safe to call from any thread. */
    static void toast(String text) {
        if (appContext == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(appContext, text, Toast.LENGTH_LONG).show());
    }

    /** Logs the event and shows a toast, but only when the transfer is to this phone's wallet. */
    private static void notifyIfMine(Tx tx, String text) {
        String me = appContext == null ? null : WalletStore.address(appContext);
        if (me != null && me.equals(tx.recipient)) {
            log(text);
            toast(text);
        }
    }

    private static String short_(String address) {
        return address.length() > 14 ? address.substring(0, 14) + "..." : address;
    }
}
