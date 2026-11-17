package com.nearchain.app;

import android.app.Application;

/** Builds the genesis block in the background so the first screen opens right away. */
public class NearChainApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread genesis = new Thread(NodeHolder::init, "nearchain-genesis");
        genesis.setDaemon(true);
        genesis.start();
    }
}