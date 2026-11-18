package com.nearchain.core;

/** A signed transfer of CDC. Its JSON form matches the desktop client's transaction dictionaries. */
public final class Tx {
    public final String senderPub;
    public final String sender;
    public final String recipient;
    public final long amount;
    public final long fee;
    public final long nonce;
    public final String network;
    public final String signature;

    public Tx(String senderPub, String sender, String recipient, long amount, long fee,
              long nonce, String network, String signature) {
        this.senderPub = senderPub;
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
        this.fee = fee;
        this.nonce = nonce;
        this.network = network;
        this.signature = signature;
    }

    public String signingJson() {
        return TxCodec.signingJson(senderPub, sender, recipient, amount, fee, nonce, network);
    }

    public String signedJson() {
        return TxCodec.signedJson(senderPub, sender, recipient, amount, fee, nonce, network, signature);
    }

    public String id() {
        return TxCodec.txId(signedJson());
    }
}
