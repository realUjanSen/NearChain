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
        return sha256dBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Double SHA-256 over raw bytes, returned as hex. */
    public static String sha256dBytes(byte[] data) {
        return toHex(digest(digest(data)));
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

    /** Decodes hex text. Throws IllegalArgumentException on bad input. */
    public static byte[] fromHex(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("odd-length hex");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid hex");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static byte[] digest(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
