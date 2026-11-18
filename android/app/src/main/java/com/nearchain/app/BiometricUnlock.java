package com.nearchain.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Unlocks the wallet with a fingerprint or face. The wallet password is encrypted with an Android
 * Keystore key that can only be used after a successful biometric check. Requires Android 9 (API 28)
 * or newer; older phones keep typing the password.
 */
public final class BiometricUnlock {
    private static final String KEY_ALIAS = "nearchain-wallet-unlock";
    private static final String PREFS = "nearchain-unlock";
    private static final String PREF_IV = "iv";
    private static final String PREF_DATA = "data";

    public interface Callback {
        void onAuthenticated(Cipher cipher);

        void onFailed(String message);
    }

    private BiometricUnlock() {
    }

    public static boolean supported() {
        return Build.VERSION.SDK_INT >= 28;
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.contains(PREF_IV) && prefs.contains(PREF_DATA);
    }

    @TargetApi(23)
    private static SecretKey keyFor(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
        }
        if (!create) {
            return null;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build());
        return generator.generateKey();
    }

    /** A cipher ready for encrypting the password. Needs a biometric check before use. */
    @TargetApi(23)
    public static Cipher encryptCipher() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(true));
        return cipher;
    }

    /** A cipher ready for decrypting the saved password. Needs a biometric check before use. */
    @TargetApi(23)
    public static Cipher decryptCipher(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        byte[] iv = android.util.Base64.decode(prefs.getString(PREF_IV, ""), android.util.Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keyFor(false), new GCMParameterSpec(128, iv));
        return cipher;
    }

    /** Saves the password, encrypted with the cipher that passed the biometric check. */
    @TargetApi(23)
    public static void save(Context context, Cipher authenticated, String password) throws Exception {
        byte[] encrypted = authenticated.doFinal(password.getBytes(StandardCharsets.UTF_8));
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(PREF_IV, android.util.Base64.encodeToString(authenticated.getIV(), android.util.Base64.NO_WRAP));
        editor.putString(PREF_DATA, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP));
        editor.apply();
    }

    /** Reads the saved password using a cipher that passed the biometric check. */
    @TargetApi(23)
    public static String read(Context context, Cipher authenticated) throws Exception {
        byte[] encrypted = android.util.Base64.decode(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_DATA, ""),
                android.util.Base64.NO_WRAP);
        return new String(authenticated.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    /** Forgets the saved password and deletes the key. Used when the wallet is deleted. */
    @TargetApi(23)
    public static void forget(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) {
                store.deleteEntry(KEY_ALIAS);
            }
        } catch (Exception ignored) {
            // Nothing to delete.
        }
    }

    /** Shows the system fingerprint or face prompt for the given cipher. */
    @TargetApi(28)
    public static void authenticate(Activity activity, Cipher cipher, String reason, Callback callback) {
        Executor mainThread = activity.getMainExecutor();
        BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                .setTitle("NearChain")
                .setDescription(reason)
                .setNegativeButtonText("Use password")
                .build();
        prompt.authenticate(new BiometricPrompt.CryptoObject(cipher), new CancellationSignal(), mainThread,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        callback.onAuthenticated(result.getCryptoObject().getCipher());
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        callback.onFailed(String.valueOf(errString));
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // Shown by the system; the prompt stays open for another try.
                    }
                });
    }
}
