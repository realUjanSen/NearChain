package com.nearchain.core;

/** Nonce search. Mirrors mine_batch / mine_full in the Python core. */
public final class Miner {
    private Miner() {
    }

    /** Returns true when the current nonce meets the target after trying up to count nonces. */
    public static boolean mineBatch(Block block, long count) {
        Header header = block.header;
        for (long i = 0; i < count; i++) {
            if (header.meetsTarget()) {
                return true;
            }
            header.setNonce(header.getNonce() + 1);
        }
        return header.meetsTarget();
    }

    public static void mineFull(Block block) {
        while (!mineBatch(block, 100_000L)) {
            // keep searching
        }
    }
}
