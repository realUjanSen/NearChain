package com.nearchain.core;

/** Consensus constants. These must match src/nearchain/consensus.py exactly. */
public final class Consensus {
    public static final String NETWORK_ID = "nearchain-dev-1";
    public static final String ADDRESS_PREFIX = "CDC";
    public static final long PROTOCOL_VERSION = 1L;

    public static final long MAX_SUPPLY = 21_000_000_000L;
    public static final long BLOCK_REWARD = 1_000L;
    public static final long TARGET_BLOCK_TIME = 60L;
    public static final long LWMA_WINDOW = 60L;
    public static final long MAX_SOLVETIME = 6L * TARGET_BLOCK_TIME;
    public static final long MIN_DIFFICULTY = 1L;
    public static final long MAX_DIFFICULTY = 1L << 48;
    public static final long GENESIS_DIFFICULTY = 1L << 16;
    public static final long GENESIS_TIMESTAMP = 1_700_000_000L;
    /** Smallest fee a transfer may pay. Matches MIN_FEE in src/nearchain/consensus.py. */
    public static final long MIN_FEE = 1L;

    /** Difficulty rises by DIFFICULTY_STEP_PERCENT every DIFFICULTY_STEP_BLOCKS blocks. */
    public static final long DIFFICULTY_STEP_BLOCKS = 100L;
    public static final long DIFFICULTY_STEP_PERCENT = 1L;

    public static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    public static final String ZERO_MINER = ADDRESS_PREFIX + "0000000000000000000000000000000000000000";

    private Consensus() {
    }
}
