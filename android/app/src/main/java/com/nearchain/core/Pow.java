package com.nearchain.core;

import java.math.BigInteger;

/**
 * Proof-of-work target check: the first 64 bits of the hash, read as an unsigned integer, must be
 * at most (2^64 - 1) / difficulty. The limit for the last difficulty is cached, so each hash check
 * is a few integer operations instead of BigInteger arithmetic.
 */
public final class Pow {
    private static final BigInteger MAX_64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private static final class Limit {
        final long difficulty;
        final long limit;

        Limit(long difficulty, long limit) {
            this.difficulty = difficulty;
            this.limit = limit;
        }
    }

    private static volatile Limit last = new Limit(-1L, 0L);

    private Pow() {
    }

    public static boolean meetsTarget(String hashHex, long difficulty) {
        if (difficulty <= 0) {
            return false;
        }
        long prefix = parseFirst64Bits(hashHex);
        long limit = limitFor(difficulty);
        // Flip the sign bit on both sides so signed comparison gives unsigned ordering.
        return (prefix ^ Long.MIN_VALUE) <= (limit ^ Long.MIN_VALUE);
    }

    private static long limitFor(long difficulty) {
        Limit cached = last;
        if (cached.difficulty == difficulty) {
            return cached.limit;
        }
        long limit = MAX_64.divide(BigInteger.valueOf(difficulty)).longValue();
        last = new Limit(difficulty, limit);
        return limit;
    }

    private static long parseFirst64Bits(String hashHex) {
        long value = 0L;
        for (int i = 0; i < 16; i++) {
            value = (value << 4) | Character.digit(hashHex.charAt(i), 16);
        }
        return value;
    }
}