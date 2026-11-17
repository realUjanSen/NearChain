package com.nearchain.core;

/**
 * A block. Transactions are not part of the Android client yet, so the Merkle root
 * is the coinbase digest alone (the Python core gives the same result for one leaf).
 */
public final class Block {
    public final Header header;
    public final String miner;
    public final long reward;

    public Block(Header header, String miner, long reward) {
        this.header = header;
        this.miner = miner;
        this.reward = reward;
    }

    public static String coinbaseId(String miner, long reward) {
        return Sha.sha256Hex("{\"miner\":\"" + miner + "\",\"reward\":" + reward + "}");
    }

    public String computeMerkle() {
        return coinbaseId(miner, reward);
    }

    public String hash() {
        return header.hash();
    }
}
