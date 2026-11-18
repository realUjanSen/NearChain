package com.nearchain.app;

import android.app.Application;

import com.nearchain.core.Chain;
import com.nearchain.wallet.TxSignatureVerifier;

/** Sets up the transfer checks, then builds the genesis block in the background so the first screen opens right away. */
public class NearChainApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NodeHolder.attach(this);
        Chain.setSignatureVerifier(new TxSignatureVerifier());
        Thread genesis = new Thread(NodeHolder::init, "nearchain-genesis");
        genesis.setDaemon(true);
        genesis.start();
    }
}
