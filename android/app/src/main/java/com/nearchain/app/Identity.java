package com.nearchain.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearchain.core.Consensus;
import com.nearchain.core.Sha;

import java.security.SecureRandom;

/**
 * The phone's mining address. It is random and stored in private preferences.
 * It is not yet a signing wallet: it has no key pair for transfers. Transfers
 * will come when Ed25519 signing is ported to the Android client.
 */
public final class Identity {
    private static final String PREFS = "nearchain";
    private static final String KEY = "mining_address";

    private Identity() {
    }

    public static synchronized String address(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String address = prefs.getString(KEY, null);
        if (address == null) {
            byte[] raw = new byte[20];
            new SecureRandom().nextBytes(raw);
            address = Consensus.ADDRESS_PREFIX + Sha.toHex(raw);
            prefs.edit().putString(KEY, address).apply();
        }
        return address;
    }
}
