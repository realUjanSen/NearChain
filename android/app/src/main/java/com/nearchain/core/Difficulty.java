package com.nearchain.core;

/**
 * Difficulty rule: every DIFFICULTY_STEP_BLOCKS blocks, difficulty rises by DIFFICULTY_STEP_PERCENT percent.
 * It never falls. Every phone computes the same value from the tip's height and difficulty.
 */
public final class Difficulty {
    private Difficulty() {
    }

    /** Difficulty for the block after a tip at this height with this difficulty. */
    public static long afterBlock(long tipHeight, long tipDifficulty) {
        if (tipHeight <= 0 || tipHeight % Consensus.DIFFICULTY_STEP_BLOCKS != 0) {
            return tipDifficulty;
        }
        long step = Math.max(1L, tipDifficulty * Consensus.DIFFICULTY_STEP_PERCENT / 100L);
        return Math.min(Consensus.MAX_DIFFICULTY, tipDifficulty + step);
    }
}
