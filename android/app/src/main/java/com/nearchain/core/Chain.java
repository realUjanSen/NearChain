package com.nearchain.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chain with full block validation. Chain selection and transactions come in a later version. */
public final class Chain {
    private final List<Block> blocks = new ArrayList<>();
    private final Map<String, Long> balances = new HashMap<>();

    public Chain(Block genesis) throws ChainException {
        Header h = genesis.header;
        boolean ok = h.height == 0 && h.version == Consensus.PROTOCOL_VERSION
                && Consensus.ZERO_HASH.equals(h.prevHash) && genesis.reward == 0
                && h.merkleRoot.equals(genesis.computeMerkle()) && h.meetsTarget();
        if (!ok) {
            throw new ChainException("invalid genesis block");
        }
        blocks.add(genesis);
    }

    public static Block genesisBlock(long difficulty) {
        String root = Block.coinbaseId(Consensus.ZERO_MINER, 0L);
        Header header = new Header(Consensus.PROTOCOL_VERSION, 0L, Consensus.ZERO_HASH, root,
                Consensus.GENESIS_TIMESTAMP, difficulty, 0L);
        Block block = new Block(header, Consensus.ZERO_MINER, 0L);
        Miner.mineFull(block);
        return block;
    }

    public synchronized Block tip() {
        return blocks.get(blocks.size() - 1);
    }

    public synchronized long height() {
        return tip().header.height;
    }

    public synchronized long issued() {
        return Emission.emittedThrough(height());
    }

    public synchronized long balance(String address) {
        Long value = balances.get(address);
        return value == null ? 0L : value;
    }

    public synchronized List<Block> blocks() {
        return new ArrayList<>(blocks);
    }

    public synchronized long expectedDifficulty() {
        int start = Math.max(0, blocks.size() - (int) (Consensus.LWMA_WINDOW + 1));
        List<Difficulty.Sample> samples = new ArrayList<>();
        for (int i = start; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            samples.add(new Difficulty.Sample(b.header.timestamp, b.header.difficulty));
        }
        return Difficulty.next(samples);
    }

    /** Build an unmined block on the current tip. Mining is done by the caller. */
    public synchronized Block prepareBlock(String miner, long timestamp) {
        Block tip = tip();
        long height = tip.header.height + 1;
        long reward = Emission.rewardAt(height);
        Header header = new Header(Consensus.PROTOCOL_VERSION, height, tip.hash(),
                Block.coinbaseId(miner, reward), Math.max(timestamp, tip.header.timestamp + 1),
                expectedDifficulty(), 0L);
        return new Block(header, miner, reward);
    }

    public synchronized void append(Block block) throws ChainException {
        Block tip = tip();
        Header h = block.header;
        if (h.height != tip.header.height + 1) {
            throw new ChainException("bad height " + h.height);
        }
        if (h.version != Consensus.PROTOCOL_VERSION) {
            throw new ChainException("unsupported protocol version");
        }
        if (!h.prevHash.equals(tip.hash())) {
            throw new ChainException("previous block hash does not match");
        }
        if (h.timestamp <= tip.header.timestamp) {
            throw new ChainException("timestamp must be later than the previous block");
        }
        long expected = expectedDifficulty();
        if (h.difficulty != expected) {
            throw new ChainException("bad difficulty " + h.difficulty + ", expected " + expected);
        }
        if (!h.merkleRoot.equals(block.computeMerkle())) {
            throw new ChainException("merkle root does not match block contents");
        }
        long reward = Emission.rewardAt(h.height);
        if (block.reward != reward) {
            throw new ChainException("bad block reward " + block.reward + ", expected " + reward);
        }
        if (!h.meetsTarget()) {
            throw new ChainException("proof of work does not meet the target");
        }
        blocks.add(block);
        Long previous = balances.get(block.miner);
        balances.put(block.miner, (previous == null ? 0L : previous) + block.reward);
    }
}
