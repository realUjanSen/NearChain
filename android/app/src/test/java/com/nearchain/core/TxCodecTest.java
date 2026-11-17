package com.nearchain.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Vectors produced by the Python core: Wallet.from_seed(bytes(range(1, 33))) sending 100 CDC, fee 2, nonce 0. */
public class TxCodecTest {
    private static final String PUB = "79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664";
    private static final String SENDER = "CDC65b60673d6ed884bf01c2c222d82ada0740f29ac";
    private static final String RECIPIENT = "CDC" + "22".repeat(20);
    private static final String SIGNING =
            "{\"amount\":100,\"fee\":2,\"network\":\"nearchain-dev-1\",\"nonce\":0,"
            + "\"recipient\":\"CDC2222222222222222222222222222222222222222\","
            + "\"sender\":\"CDC65b60673d6ed884bf01c2c222d82ada0740f29ac\","
            + "\"sender_pub\":\"79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664\"}";
    private static final String SIGNATURE =
            "a806e03d49c979600645a13df6c60de7f14a7125d08fab8243212d3fdadd03b4e964443a3b61f0fab8b7e8ba953917703512429ae87946412e6e5a2ca2c7610c";
    private static final String TX_ID = "496fd15cd00dcb1fcc30fb8323fe7dc98d34603e1f12eeff601a219c3d15cc9e";

    @Test
    public void signingBytesMatchPython() {
        assertEquals(SIGNING, TxCodec.signingJson(PUB, SENDER, RECIPIENT, 100, 2, 0, Consensus.NETWORK_ID));
    }

    @Test
    public void txIdMatchesPython() {
        String signed = TxCodec.signedJson(PUB, SENDER, RECIPIENT, 100, 2, 0, Consensus.NETWORK_ID, SIGNATURE);
        assertEquals(TX_ID, TxCodec.txId(signed));
    }

    @Test
    public void addressValidation() {
        assertTrue(TxCodec.isValidAddress(SENDER));
        assertFalse(TxCodec.isValidAddress("CDC123"));
        assertFalse(TxCodec.isValidAddress("XYZ" + "22".repeat(20)));
        assertFalse(TxCodec.isValidAddress("CDC" + "zz".repeat(20)));
    }
}