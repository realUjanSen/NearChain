package com.nearchain.core;

/** Height-based emission: issued through height h is min(1000 * h, MAX_SUPPLY). */
public final class Emission {
    public static final long CAP_HEIGHT = Consensus.MAX_SUPPLY / Consensus.BLOCK_REWARD;

    private Emission() {
    }

    public static long emittedThrough(long height) {
        if (height >= CAP_HEIGHT) {
            return Consensus.MAX_SUPPLY;
        }
        return Math.max(0L, height) * Consensus.BLOCK_REWARD;
    }

    public static long rewardAt(long height) {
        if (height <= 0) {
            return 0L;
        }
        return emittedThrough(height) - emittedThrough(height - 1);
    }
}
