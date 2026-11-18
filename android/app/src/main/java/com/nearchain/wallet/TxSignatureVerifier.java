package com.nearchain.wallet;

import com.nearchain.core.Sha;
import com.nearchain.core.SignatureVerifier;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.charset.StandardCharsets;

/** Checks transfer signatures with BouncyCastle, which works on every Android version the app supports. */
public final class TxSignatureVerifier implements SignatureVerifier {
    @Override
    public boolean verify(String senderPubHex, String message, String signatureHex) {
        try {
            Ed25519PublicKeyParameters key = new Ed25519PublicKeyParameters(Sha.fromHex(senderPubHex), 0);
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, key);
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            verifier.update(bytes, 0, bytes.length);
            return verifier.verifySignature(Sha.fromHex(signatureHex));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
