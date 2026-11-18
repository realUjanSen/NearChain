package com.nearchain.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A block: header, coinbase (miner and reward), and the transfers it includes. */
public final class Block {
    public final Header header;
    public final String miner;
    public final long reward;
    public final List<Tx> txs;

    public Block(Header header, String miner, long reward) {
        this(header, miner, reward, new ArrayList<Tx>());
    }

    public Block(Header header, String miner, long reward, List<Tx> txs) {
        this.header = header;
        this.miner = miner;
        this.reward = reward;
        this.txs = Collections.unmodifiableList(new ArrayList<>(txs));
    }

    public static String coinbaseId(String miner, long reward) {
        return Sha.sha256Hex("{\"miner\":\"" + miner + "\",\"reward\":" + reward + "}");
    }

    /** Merkle root over the coinbase ID followed by each transfer ID. Matches the Python core. */
    public static String merkleRootFor(String miner, long reward, List<Tx> txs) {
        List<String> leaves = new ArrayList<>();
        leaves.add(coinbaseId(miner, reward));
        for (Tx t : txs) {
            leaves.add(t.id());
        }
        return merkleRoot(leaves);
    }

    public String computeMerkle() {
        return merkleRootFor(miner, reward, txs);
    }

    private static String merkleRoot(List<String> ids) {
        if (ids.isEmpty()) {
            return Consensus.ZERO_HASH;
        }
        List<String> level = new ArrayList<>(ids);
        while (level.size() > 1) {
            if (level.size() % 2 == 1) {
                level.add(level.get(level.size() - 1));
            }
            List<String> next = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                byte[] left = Sha.fromHex(level.get(i));
                byte[] right = Sha.fromHex(level.get(i + 1));
                byte[] both = new byte[left.length + right.length];
                System.arraycopy(left, 0, both, 0, left.length);
                System.arraycopy(right, 0, both, left.length, right.length);
                next.add(Sha.sha256dBytes(both));
            }
            level = next;
        }
        return level.get(0);
    }

    public String hash() {
        return header.hash();
    }
}
