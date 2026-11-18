package com.nearchain.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Transaction encoding. The JSON text must match Python's json.dumps(..., sort_keys=True,
 * separators=(",", ":")) exactly, because signatures and transaction IDs are computed over it.
 */
public final class TxCodec {
    private TxCodec() {
    }

    public static boolean isValidAddress(String address) {
        if (address == null || !address.startsWith(Consensus.ADDRESS_PREFIX)) {
            return false;
        }
        String body = address.substring(Consensus.ADDRESS_PREFIX.length());
        if (body.length() != 40) {
            return false;
        }
        for (int i = 0; i < body.length(); i++) {
            if (Character.digit(body.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Address for a public key given as hex: CDC + first 20 bytes of SHA-256(key). Null if the key is malformed. */
    public static String addressFromPub(String pubHex) {
        try {
            byte[] raw = Sha.fromHex(pubHex);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            byte[] first20 = new byte[20];
            System.arraycopy(digest, 0, first20, 0, 20);
            return Consensus.ADDRESS_PREFIX + Sha.toHex(first20);
        } catch (IllegalArgumentException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** The bytes that the sender signs. Keys are in alphabetical order. */
    public static String signingJson(String senderPub, String sender, String recipient,
                                     long amount, long fee, long nonce, String network) {
        return "{\"amount\":" + amount
                + ",\"fee\":" + fee
                + ",\"network\":\"" + network + "\""
                + ",\"nonce\":" + nonce
                + ",\"recipient\":\"" + recipient + "\""
                + ",\"sender\":\"" + sender + "\""
                + ",\"sender_pub\":\"" + senderPub + "\"}";
    }

    /** The full signed transaction. Keys are in alphabetical order. */
    public static String signedJson(String senderPub, String sender, String recipient,
                                    long amount, long fee, long nonce, String network,
                                    String signatureHex) {
        return "{\"amount\":" + amount
                + ",\"fee\":" + fee
                + ",\"network\":\"" + network + "\""
                + ",\"nonce\":" + nonce
                + ",\"recipient\":\"" + recipient + "\""
                + ",\"sender\":\"" + sender + "\""
                + ",\"sender_pub\":\"" + senderPub + "\""
                + ",\"signature\":\"" + signatureHex + "\"}";
    }

    /** Transaction ID: SHA-256 of the signed JSON, as in the Python core. */
    public static String txId(String signedJson) {
        return Sha.sha256Hex(signedJson);
    }
}
