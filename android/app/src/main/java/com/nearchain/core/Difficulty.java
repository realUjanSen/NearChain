package com.nearchain.core;

import java.math.BigInteger;
import java.util.List;

/** LWMA difficulty adjustment. Depends on timestamps and difficulties, never on height. */
public final class Difficulty {

    public static final class Sample {
        public final long timestamp;
        public final long difficulty;

        public Sample(long timestamp, long difficulty) {
            this.timestamp = timestamp;
            this.difficulty = difficulty;
        }
    }

    private Difficulty() {
    }

    /** @param samples oldest first; the last entry is the tip. */
    public static long next(List<Sample> samples) {
        if (samples.isEmpty()) {
            return Consensus.MIN_DIFFICULTY;
        }
        if (samples.size() == 1) {
            return samples.get(0).difficulty;
        }

        int n = (int) Math.min(samples.size() - 1, Consensus.LWMA_WINDOW);
        int start = samples.size() - 1 - n;
        long weightedSolvetime = 0L;
        long difficultySum = 0L;
        for (int i = 1; i <= n; i++) {
            Sample prev = samples.get(start + i - 1);
            Sample cur = samples.get(start + i);
            long solve = Math.min(Math.max(cur.timestamp - prev.timestamp, 1L), Consensus.MAX_SOLVETIME);
            weightedSolvetime += (long) i * solve;
            difficultySum += cur.difficulty;
        }

        long average = difficultySum / n;
        long k = (long) n * (n + 1) / 2 * Consensus.TARGET_BLOCK_TIME;
        // average * k can exceed 64 bits, so use BigInteger here.
        BigInteger next = BigInteger.valueOf(average)
                .multiply(BigInteger.valueOf(k))
                .divide(BigInteger.valueOf(weightedSolvetime));
        BigInteger low = BigInteger.valueOf(Consensus.MIN_DIFFICULTY);
        BigInteger high = BigInteger.valueOf(Consensus.MAX_DIFFICULTY);
        return next.max(low).min(high).longValue();
    }
}
