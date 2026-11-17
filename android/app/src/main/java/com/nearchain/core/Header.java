package com.nearchain.core;

/** Block header. Hash input is "version|height|prev|merkle|timestamp|difficulty|nonce", double SHA-256. */
public final class Header {
    public final long version;
    public final long height;
    public final String prevHash;
    public final String merkleRoot;
    public final long timestamp;
    public final long difficulty;
    private long nonce;

    public Header(long version, long height, String prevHash, String merkleRoot,
                  long timestamp, long difficulty, long nonce) {
        this.version = version;
        this.height = height;
        this.prevHash = prevHash;
        this.merkleRoot = merkleRoot;
        this.timestamp = timestamp;
        this.difficulty = difficulty;
        this.nonce = nonce;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    public String hash() {
        String text = version + "|" + height + "|" + prevHash + "|" + merkleRoot + "|"
                + timestamp + "|" + difficulty + "|" + nonce;
        return Sha.sha256dHex(text);
    }

    public boolean meetsTarget() {
        return Pow.meetsTarget(hash(), difficulty);
    }
}
