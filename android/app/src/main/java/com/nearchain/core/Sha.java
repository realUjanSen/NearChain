package com.nearchain.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers. Hex output is lowercase, matching the Python implementation. */
public final class Sha {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sha() {
    }

    public static String sha256Hex(String text) {
        return toHex(digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256dHex(String text) {
        return toHex(digest(digest(text.getBytes(StandardCharsets.UTF_8))));
    }

    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }

    private static byte[] digest(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
