package com.nearchain.wallet;

import android.content.Context;

import com.nearchain.core.Consensus;

import org.bouncycastle.crypto.generators.SCrypt;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted keystore. The file format matches src/nearchain/wallet.py, so a wallet
 * created here can be copied to the desktop client's data folder and used there.
 */
public final class WalletStore {
    public static final class WalletException extends Exception {
        public WalletException(String message) {
            super(message);
        }

        public WalletException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final String FILE_NAME = "wallet.json";
    private static final int VERSION = 1;
    private static final byte[] AAD = "nearchain-keystore-v1".getBytes(StandardCharsets.US_ASCII);
    private static final int SCRYPT_N = 1 << 15;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;

    private WalletStore() {
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static boolean exists(Context context) {
        return file(context).exists();
    }

    /** The public address. Reading it needs no password. Returns null when there is no wallet. */
    public static String address(Context context) {
        if (!exists(context)) {
            return null;
        }
        try {
            return readJson(context).getString("address");
        } catch (Exception e) {
            return null;
        }
    }

    public static Wallet create(Context context, String password) throws WalletException {
        if (exists(context)) {
            throw new WalletException("a wallet already exists on this phone");
        }
        byte[] seed = new byte[32];
        new SecureRandom().nextBytes(seed);
        Wallet wallet = Wallet.fromSeed(seed);
        byte[] salt = randomBytes(16);
        byte[] nonce = randomBytes(12);
        byte[] ciphertext = encrypt(seed, password, salt, nonce);
        try {
            JSONObject json = new JSONObject();
            json.put("version", VERSION);
            json.put("address", wallet.address());
            json.put("salt", toHex(salt));
            json.put("nonce", toHex(nonce));
            json.put("ciphertext", toHex(ciphertext));
            writeAtomically(context, json.toString(2));
        } catch (Exception e) {
            throw new WalletException("could not save the wallet", e);
        }
        return wallet;
    }

    public static Wallet unlock(Context context, String password) throws WalletException {
        if (!exists(context)) {
            throw new WalletException("no wallet on this phone");
        }
        try {
            JSONObject json = readJson(context);
            if (json.getInt("version") != VERSION) {
                throw new WalletException("unsupported wallet version");
            }
            byte[] salt = fromHex(json.getString("salt"));
            byte[] nonce = fromHex(json.getString("nonce"));
            byte[] ciphertext = fromHex(json.getString("ciphertext"));
            byte[] seed = decrypt(ciphertext, password, salt, nonce);
            Wallet wallet = Wallet.fromSeed(seed);
            if (!wallet.address().equals(json.getString("address"))) {
                throw new WalletException("wallet file does not match its address");
            }
            return wallet;
        } catch (AEADBadTagException e) {
            throw new WalletException("wrong password");
        } catch (WalletException e) {
            throw e;
        } catch (Exception e) {
            throw new WalletException("wallet file is damaged", e);
        }
    }

    private static byte[] deriveKey(String password, byte[] salt) {
        return SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt,
                SCRYPT_N, SCRYPT_R, SCRYPT_P, 32);
    }

    private static byte[] encrypt(byte[] seed, String password, byte[] salt, byte[] nonce) throws WalletException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(deriveKey(password, salt), "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            return cipher.doFinal(seed);
        } catch (Exception e) {
            throw new WalletException("encryption failed", e);
        }
    }

    private static byte[] decrypt(byte[] ciphertext, String password, byte[] salt, byte[] nonce)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(password, salt), "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(AAD);
        return cipher.doFinal(ciphertext);
    }

    /** Removes the wallet file. Coins held by this address are lost unless the key was backed up. */
    public static boolean delete(Context context) {
        return !exists(context) || file(context).delete();
    }

    private static JSONObject readJson(Context context) throws Exception {
        // FileInputStream rather than java.nio.file.Files, which needs Android API 26.
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.io.FileInputStream in = new java.io.FileInputStream(file(context))) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        }
        return new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
    }

    private static void writeAtomically(Context context, String text) throws IOException {
        File target = file(context);
        File tmp = new File(target.getParentFile(), FILE_NAME + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            throw new IOException("could not replace the wallet file");
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] out = new byte[length];
        new SecureRandom().nextBytes(out);
        return out;
    }

    private static String toHex(byte[] bytes) {
        return com.nearchain.core.Sha.toHex(bytes);
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}