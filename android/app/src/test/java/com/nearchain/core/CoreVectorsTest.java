package com.nearchain.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Vectors below were produced by the Python reference core (src/nearchain). Keep them in sync. */
public class CoreVectorsTest {

    private static final String GENESIS_HASH = "03cbeb80d51db3025309136604c9e64b644c68def0f77fee09d21c9993abff7c";
    private static final String GENESIS_MERKLE = "474109327774c947e477e763b390c1869309857a220643fe146bcab0530d9256";
    private static final String BLOCK1_HASH = "b2debc3279bee9a8764e47156963a89ba3fa37d2482958d616fe4fe0dbf521f4";
    private static final String BLOCK1_MERKLE = "ec9cdb68e090d18de1f89ef05fd93b900a6490ad6b61664fa9c1af1f44122410";
    private static final String BLOCK2_HASH = "c775f61a08192eae15c26e9253959dc0e2067e2954e0046124f51595913510a8";
    private static final String MINER = "CDC" + "11".repeat(20);

    @Test
    public void genesisHashMatchesPython() {
        Block genesis = Chain.genesisBlock(1L);
        assertEquals(GENESIS_HASH, genesis.hash());
        assertEquals(GENESIS_MERKLE, genesis.header.merkleRoot);
        assertEquals(0L, genesis.header.getNonce());
    }

    @Test
    public void chainValidatesPythonBlocks() throws ChainException {
        Chain chain = new Chain(Chain.genesisBlock(1L));
        Block b1 = chain.prepareBlock(MINER, java.util.Collections.<Tx>emptyList(), Consensus.GENESIS_TIMESTAMP + 60);
        Miner.mineFull(b1);
        assertEquals(BLOCK1_MERKLE, b1.header.merkleRoot);
        assertEquals(BLOCK1_HASH, b1.hash());
        chain.append(b1);

        Block b2 = chain.prepareBlock(MINER, java.util.Collections.<Tx>emptyList(), Consensus.GENESIS_TIMESTAMP + 120);
        Miner.mineFull(b2);
        assertEquals(BLOCK2_HASH, b2.hash());
        chain.append(b2);

        assertEquals(2L, chain.height());
        assertEquals(2000L, chain.balance(MINER));
    }

    @Test
    public void rejectsInflatedReward() throws ChainException {
        Chain chain = new Chain(Chain.genesisBlock(1L));
        Block bad = new Block(new Header(1, 1, GENESIS_HASH, Block.coinbaseId(MINER, 1001L),
                Consensus.GENESIS_TIMESTAMP + 60, 1L, 0L), MINER, 1001L);
        Miner.mineFull(bad);
        try {
            chain.append(bad);
            throw new AssertionError("inflated reward was accepted");
        } catch (ChainException expected) {
            assertTrue(expected.getMessage().contains("bad block reward"));
        }
    }

    @Test
    public void difficultyVectors() {
        List<Difficulty.Sample> fast = new ArrayList<>();
        List<Difficulty.Sample> gap = new ArrayList<>();
        for (int i = 0; i < 81; i++) {
            fast.add(new Difficulty.Sample(Consensus.GENESIS_TIMESTAMP + i * 30L, 5000L));
            gap.add(new Difficulty.Sample(Consensus.GENESIS_TIMESTAMP + i * 10000L, 5000L));
        }
        assertEquals(10000L, Difficulty.next(fast));
        assertEquals(833L, Difficulty.next(gap));
    }

    @Test
    public void emissionCap() {
        assertEquals(1000L, Emission.rewardAt(1));
        assertEquals(1000L, Emission.rewardAt(21_000_000L));
        assertEquals(0L, Emission.rewardAt(21_000_001L));
        assertEquals(21_000_000_000L, Emission.emittedThrough(Long.MAX_VALUE / 2));
    }

    @Test
    public void powRejectsHighHash() {
        assertFalse(Pow.meetsTarget("ffffffffffffffff" + "00".repeat(24), 2L));
        assertTrue(Pow.meetsTarget("0000000000000001" + "00".repeat(24), 2L));
    }
}
