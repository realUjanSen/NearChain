package com.nearchain.app;

import android.content.Context;

import com.nearchain.core.Block;
import com.nearchain.core.Chain;
import com.nearchain.core.ChainException;
import com.nearchain.core.Consensus;
import com.nearchain.core.Emission;
import com.nearchain.net.NetNode;
import com.nearchain.wallet.Wallet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide state shared by the screens, the mining service, and the network.
 * The chain is kept in memory: after a restart the phone starts from genesis and syncs again.
 */
public final class NodeHolder {
    static final int PORT = 8333;

    private static Chain chain;
    private static NetNode net;
    private static Discovery discovery;
    private static boolean networkStarted;
    private static volatile Wallet wallet;
    private static volatile boolean mining;
    private static volatile String lastEvent = "not mining";
    private static volatile long currentNonce;
    private static volatile long currentDifficulty;
    private static volatile double hashrate;
    private static volatile int blocksMined;
    private static final List<String> log = new ArrayList<>();
    private static volatile int logLimit = 200;

    private NodeHolder() {
    }

    public static synchronized void init() {
        if (chain == null) {
            try {
                chain = new Chain(Chain.genesisBlock(Consensus.GENESIS_DIFFICULTY));
            } catch (ChainException e) {
                throw new IllegalStateException("genesis failed to validate", e);
            }
            net = new NetNode(chain, NodeHolder::log);
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
    }

    /** Add a mined block to the chain and send it to peers. */
    public static void submitMined(Block block) throws ChainException {
        synchronized (chain) {
            chain.append(block);
        }
        net.broadcastBlock(block, null);
        blocksMined++;
        log("mined block " + block.header.height + " (reward " + block.reward + " CDC)");
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

    /** Rewards paid to this address, for the Trader Mode history. */
    public static String history(String address) {
        StringBuilder sb = new StringBuilder();
        for (Block b : chain().blocks()) {
            if (b.miner.equals(address) && b.reward > 0) {
                sb.append("#").append(b.header.height).append("  mined  +")
                        .append(b.reward).append(" CDC\n");
            }
        }
        return sb.length() == 0 ? "No rewards yet." : sb.toString();
    }

    public static long rewardForNextBlock() {
        return Emission.rewardAt(chain().height() + 1);
    }
}