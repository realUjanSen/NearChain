package com.nearchain.core;

import java.math.BigInteger;

/**
 * Proof-of-work target check: the first 64 bits of the hash, read as an unsigned
 * integer, must be at most (2^64 - 1) / difficulty. BigInteger is used because
 * unsigned helpers in java.lang.Long need Android API 26.
 */
public final class Pow {
    private static final BigInteger MAX_64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private Pow() {
    }

    public static boolean meetsTarget(String hashHex, long difficulty) {
        if (difficulty <= 0) {
            return false;
        }
        BigInteger prefix = new BigInteger(hashHex.substring(0, 16), 16);
        BigInteger limit = MAX_64.divide(BigInteger.valueOf(difficulty));
        return prefix.compareTo(limit) <= 0;
    }
}
