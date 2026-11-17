package com.nearchain.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.nearchain.core.Block;
import com.nearchain.core.Chain;
import com.nearchain.core.ChainException;
import com.nearchain.core.Miner;
import com.nearchain.wallet.WalletStore;

/** Foreground service that mines blocks. The notification keeps Android from suspending the work. */
public class MiningService extends Service {
    public static final String ACTION_START = "com.nearchain.app.action.START";
    public static final String ACTION_STOP = "com.nearchain.app.action.STOP";

    private static final String CHANNEL_ID = "mining";
    private static final int NOTIFICATION_ID = 1;
    private static final long BATCH_SIZE = 5000L;

    private volatile boolean stopRequested;
    private Thread worker;

    public static void start(Context context) {
        Intent intent = new Intent(context, MiningService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, MiningService.class).setAction(ACTION_STOP));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRequested = true;
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (worker != null && worker.isAlive()) {
            return START_STICKY;
        }
        String address = WalletStore.address(this);
        if (address == null) {
            NodeHolder.log("create a wallet before mining");
            stopSelf();
            return START_NOT_STICKY;
        }
        NodeHolder.startNetwork(this);
        createChannel();
        showForeground();
        stopRequested = false;
        worker = new Thread(() -> mineLoop(address), "nearchain-miner");
        worker.start();
        return START_STICKY;
    }

    private void mineLoop(String address) {
        NodeHolder.setMining(true);
        NodeHolder.log("mining started");
        Chain chain = NodeHolder.chain();
        while (!stopRequested) {
            Block block = chain.prepareBlock(address, System.currentTimeMillis() / 1000L);
            NodeHolder.recordAttempt(block.header.getNonce(), block.header.difficulty, NodeHolder.hashrate());
            boolean found = false;
            long windowStart = System.currentTimeMillis();
            long windowHashes = 0L;
            while (!stopRequested) {
                found = Miner.mineBatch(block, BATCH_SIZE);
                windowHashes += BATCH_SIZE;
                long elapsed = System.currentTimeMillis() - windowStart;
                if (elapsed >= 1000L) {
                    NodeHolder.recordAttempt(block.header.getNonce(), block.header.difficulty,
                            windowHashes * 1000.0 / elapsed);
                    windowStart = System.currentTimeMillis();
                    windowHashes = 0L;
                }
                if (found) {
                    break;
                }
                // A block arrived from a peer while we were mining: start again on the new tip.
                if (!chain.tip().hash().equals(block.header.prevHash)) {
                    break;
                }
            }
            if (found) {
                try {
                    NodeHolder.submitMined(block);
                } catch (ChainException e) {
                    NodeHolder.log("block discarded: " + e.getMessage());
                }
            }
        }
        NodeHolder.setMining(false);
        NodeHolder.log("mining stopped");
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Mining", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void showForeground() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle("NearChain")
                .setContentText("Mining blocks")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true);
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
