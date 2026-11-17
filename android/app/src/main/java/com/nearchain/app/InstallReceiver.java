package com.nearchain.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Receives the status of an update install and opens Android's confirmation screen when needed. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                NodeHolder.log("update installed");
                break;
            default:
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                NodeHolder.log("update not installed: " + message);
                break;
        }
    }
}
