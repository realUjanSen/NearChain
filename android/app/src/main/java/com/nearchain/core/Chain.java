package com.nearchain.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chain with full block and transfer validation, and cumulative-work chain selection. */
public final class Chain {
    private static volatile SignatureVerifier verifier;

    private final List<Block> blocks = new ArrayList<>();
    private final List<BigInteger> work = new ArrayList<>();
    private final Map<String, Long> balances = new HashMap<>();
    private final Map<String, Long> nonces = new HashMap<>();

    /** Set once at startup by the app. Until it is set, every transfer is rejected. */
    public static void setSignatureVerifier(SignatureVerifier value) {
        verifier = value;
    }

    public Chain(Block genesis) throws ChainException {
        checkGenesis(genesis);
        blocks.add(genesis);
        work.add(BigInteger.valueOf(genesis.header.difficulty));
    }

    private static void checkGenesis(Block genesis) throws ChainException {
        Header h = genesis.header;
        boolean ok = h.height == 0 && h.version == Consensus.PROTOCOL_VERSION
                && Consensus.ZERO_HASH.equals(h.prevHash) && genesis.reward == 0 && genesis.txs.isEmpty()
                && h.merkleRoot.equals(genesis.computeMerkle()) && h.meetsTarget();
        if (!ok) {
            throw new ChainException("invalid genesis block");
        }
    }

    public static Chain fromBlocks(List<Block> list) throws ChainException {
        if (list.isEmpty()) {
            throw new ChainException("empty chain");
        }
        Chain chain = new Chain(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            chain.append(list.get(i));
        }
        return chain;
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

    public synchronized BigInteger cumulativeWork() {
        return work.get(work.size() - 1);
    }

    public synchronized long issued() {
        return Emission.emittedThrough(height());
    }

    public synchronized long balance(String address) {
        return get(balances, address);
    }

    public synchronized long nextNonce(String address) {
        return get(nonces, address);
    }

    /** Every address that has been paid, with its balance. A copy. */
    public synchronized Map<String, Long> balances() {
        return new HashMap<>(balances);
    }

    /** The next expected nonce for every sender. A copy. */
    public synchronized Map<String, Long> nonces() {
        return new HashMap<>(nonces);
    }

    public synchronized List<Block> blocks() {
        return new ArrayList<>(blocks);
    }

    public synchronized long expectedDifficulty() {
        Block tip = tip();
        return Difficulty.afterBlock(tip.header.height, tip.header.difficulty);
    }

    /**
     * Checks one transfer against the given balances and nonces. Returns null if valid, otherwise the reason.
     */
    public static String validateTransfer(Tx tx, Map<String, Long> bal, Map<String, Long> non) {
        if (!Consensus.NETWORK_ID.equals(tx.network)) {
            return "transaction is for a different network";
        }
        if (tx.amount <= 0 || tx.amount > Consensus.MAX_SUPPLY) {
            return "invalid amount";
        }
        if (tx.fee < Consensus.MIN_FEE) {
            return "fee is below the minimum";
        }
        if (!TxCodec.isValidAddress(tx.sender) || !TxCodec.isValidAddress(tx.recipient)) {
            return "invalid address";
        }
        if (tx.sender.equals(tx.recipient)) {
            return "cannot send to yourself";
        }
        if (!tx.sender.equals(TxCodec.addressFromPub(tx.senderPub))) {
            return "sender does not match the key";
        }
        if (tx.nonce != get(non, tx.sender)) {
            return "wrong nonce";
        }
        if (get(bal, tx.sender) < tx.amount + tx.fee) {
            return "insufficient balance";
        }
        SignatureVerifier check = verifier;
        if (check == null || !check.verify(tx.senderPub, tx.signingJson(), tx.signature)) {
            return "bad signature";
        }
        return null;
    }

    /** Applies a transfer that has already been validated. Returns its fee. */
    public static long applyTransfer(Tx tx, Map<String, Long> bal, Map<String, Long> non) {
        bal.put(tx.sender, get(bal, tx.sender) - tx.amount - tx.fee);
        bal.put(tx.recipient, get(bal, tx.recipient) + tx.amount);
        non.put(tx.sender, get(non, tx.sender) + 1);
        return tx.fee;
    }

    private static long get(Map<String, Long> map, String key) {
        Long value = map.get(key);
        return value == null ? 0L : value;
    }

    /** Builds an unmined block on the tip, including the candidate transfers that are valid, in order. */
    public synchronized Block prepareBlock(String miner, List<Tx> candidates, long timestamp) {
        Block tip = tip();
        long height = tip.header.height + 1;
        Map<String, Long> bal = new HashMap<>(balances);
        Map<String, Long> non = new HashMap<>(nonces);
        List<Tx> included = new ArrayList<>();
        long fees = 0;
        for (Tx tx : candidates) {
            if (validateTransfer(tx, bal, non) == null) {
                fees += applyTransfer(tx, bal, non);
                included.add(tx);
            }
        }
        long reward = Emission.rewardAt(height) + fees;
        Header header = new Header(Consensus.PROTOCOL_VERSION, height, tip.hash(),
                Block.merkleRootFor(miner, reward, included),
                Math.max(timestamp, tip.header.timestamp + 1), expectedDifficulty(), 0L);
        return new Block(header, miner, reward, included);
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
        if (!h.meetsTarget()) {
            throw new ChainException("proof of work does not meet the target");
        }

        Map<String, Long> bal = new HashMap<>(balances);
        Map<String, Long> non = new HashMap<>(nonces);
        long fees = 0;
        for (Tx tx : block.txs) {
            String reason = validateTransfer(tx, bal, non);
            if (reason != null) {
                throw new ChainException("bad transfer: " + reason);
            }
            fees += applyTransfer(tx, bal, non);
        }
        long reward = Emission.rewardAt(h.height);
        if (block.reward != reward + fees) {
            throw new ChainException("bad block reward " + block.reward + ", expected " + (reward + fees));
        }
        bal.put(block.miner, get(bal, block.miner) + block.reward);

        blocks.add(block);
        work.add(cumulativeWork().add(BigInteger.valueOf(h.difficulty)));
        balances.clear();
        balances.putAll(bal);
        nonces.clear();
        nonces.putAll(non);
    }

    /** Adopt the candidate chain if it is fully valid, has the same genesis, and more cumulative work. */
    public synchronized boolean consider(List<Block> candidateBlocks) throws ChainException {
        Chain candidate = fromBlocks(candidateBlocks);
        if (!candidate.blocks.get(0).hash().equals(blocks.get(0).hash())) {
            throw new ChainException("different genesis block");
        }
        if (candidate.cumulativeWork().compareTo(cumulativeWork()) <= 0) {
            return false;
        }
        blocks.clear();
        blocks.addAll(candidate.blocks);
        work.clear();
        work.addAll(candidate.work);
        balances.clear();
        balances.putAll(candidate.balances);
        nonces.clear();
        nonces.putAll(candidate.nonces);
        return true;
    }
}
