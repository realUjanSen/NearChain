package com.nearchain.core;

/** Checks an Ed25519 signature. The app supplies the implementation (BouncyCastle), so this package has no crypto dependency. */
public interface SignatureVerifier {
    boolean verify(String senderPubHex, String message, String signatureHex);
}
