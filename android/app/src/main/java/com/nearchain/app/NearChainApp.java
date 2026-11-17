package com.nearchain.app;

import android.app.Application;

public class NearChainApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NodeHolder.init();
    }
}
