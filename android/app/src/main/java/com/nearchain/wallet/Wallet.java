package com.nearchain.wallet;

import com.nearchain.core.Consensus;
import com.nearchain.core.Sha;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** An Ed25519 key pair. Signing uses BouncyCastle, so it works below Android 13. */
public final class Wallet {
    private final Ed25519PrivateKeyParameters privateKey;
    private final byte[] publicKey;
    private final String address;

    private Wallet(byte[] seed) {
        this.privateKey = new Ed25519PrivateKeyParameters(seed, 0);
        Ed25519PublicKeyParameters pub = privateKey.generatePublicKey();
        this.publicKey = pub.getEncoded();
        this.address = addressFor(publicKey);
    }

    public static Wallet fromSeed(byte[] seed) {
        if (seed == null || seed.length != 32) {
            throw new IllegalArgumentException("seed must be 32 bytes");
        }
        return new Wallet(seed);
    }

    /** Address = "CDC" + first 20 bytes of SHA-256(public key), hex. Same rule as the Python core. */
    public static String addressFor(byte[] publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey);
            return Consensus.ADDRESS_PREFIX + Sha.toHex(Arrays.copyOf(digest, 20));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String address() {
        return address;
    }

    public String publicKeyHex() {
        return Sha.toHex(publicKey);
    }

    /** The 32-byte private seed. Only WalletStore should call this, to encrypt it. */
    byte[] seed() {
        return privateKey.getEncoded();
    }

    public byte[] sign(byte[] message) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(message, 0, message.length);
        return signer.generateSignature();
    }
}