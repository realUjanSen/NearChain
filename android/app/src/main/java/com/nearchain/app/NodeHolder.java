package com.nearchain.app;

import com.nearchain.core.Block;
import com.nearchain.core.Chain;
import com.nearchain.core.ChainException;
import com.nearchain.core.Consensus;
import com.nearchain.core.Emission;
import com.nearchain.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide chain and mining state shared by the Activity and the mining service.
 * The chain lives in memory only in this version: it starts again from genesis on each launch.
 */
public final class NodeHolder {
    private static Chain chain;
    private static volatile boolean mining;
    private static volatile String lastEvent = "not mining";
    private static volatile long currentNonce;
    private static volatile long currentDifficulty;
    private static volatile double hashrate;
    private static volatile int blocksMined;
    private static final List<String> log = new ArrayList<>();
    private static volatile Wallet wallet;

    private NodeHolder() {
    }

    public static synchronized void init() {
        if (chain == null) {
            try {
                chain = new Chain(Chain.genesisBlock(Consensus.GENESIS_DIFFICULTY));
            } catch (ChainException e) {
                throw new IllegalStateException("genesis failed to validate", e);
            }
        }
    }

    /** The unlocked wallet, or null while locked. Held in memory only. */
    public static Wallet wallet() {
        return wallet;
    }

    public static void setWallet(Wallet value) {
        wallet = value;
    }

    public static Chain chain() {
        init();
        return chain;
    }

    public static synchronized void log(String text) {
        String stamped = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()) + "  " + text;
        log.add(stamped);
        if (log.size() > 300) {
            log.subList(0, 100).clear();
        }
        lastEvent = text;
    }

    public static synchronized String logText() {
        StringBuilder sb = new StringBuilder();
        for (String line : log) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static void setMining(boolean value) {
        mining = value;
    }

    public static void recordAttempt(long nonce, long difficulty, double rate) {
        currentNonce = nonce;
        currentDifficulty = difficulty;
        hashrate = rate;
    }

    public static void blockMined() {
        blocksMined++;
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

    /** Mined rewards paid to this address, for the Trader Mode history. */
    public static synchronized String history(String address) {
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