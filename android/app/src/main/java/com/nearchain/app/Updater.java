package com.nearchain.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.nearchain.net.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub for a newer release and installs it. Release tags look like v0.1.<n>, where <n>
 * is the CI run number, which is also this app's versionCode. A higher <n> means a newer build.
 */
public final class Updater {
    public static final String REPO = "realUjanSen/NearChain";
    public static final String APK_ASSET = "NearChain.apk";
    private static final Pattern TAG = Pattern.compile("^v0\\.1\\.(\\d+)$");

    public static final class Release {
        public final int number;
        public final String tag;
        public final String apkUrl;

        Release(int number, String tag, String apkUrl) {
            this.number = number;
            this.tag = tag;
            this.apkUrl = apkUrl;
        }
    }

    private Updater() {
    }

    public static int installedVersionCode(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionCode;
    }

    /** The newest release on GitHub, or null if none matches the v0.1.<n> pattern. */
    public static Release latest() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "NearChain-Android");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        try {
            if (conn.getResponseCode() != 200) {
                throw new IOException("GitHub returned HTTP " + conn.getResponseCode());
            }
            String body = readAll(conn.getInputStream());
            JSONObject json = new JSONObject(body);
            String tag = json.getString("tag_name");
            Matcher m = TAG.matcher(tag);
            if (!m.matches()) {
                return null;
            }
            String apkUrl = null;
            JSONArray assets = json.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (APK_ASSET.equals(asset.getString("name"))) {
                    apkUrl = asset.getString("browser_download_url");
                }
            }
            if (apkUrl == null) {
                return null;
            }
            return new Release(Integer.parseInt(m.group(1)), tag, apkUrl);
        } catch (Exception e) {
            throw new IOException("could not read the latest release: " + e.getMessage(), e);
        } finally {
            conn.disconnect();
        }
    }

    /** True when the system will let this app install packages (Android 8 and later). */
    public static boolean canInstall(Context context) {
        return Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls();
    }

    /** Opens the screen where the user allows this app to install others. */
    public static Intent unknownSourcesSettings(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
    }

    /** Download the APK into the app's cache folder and return the file. */
    public static File download(Context context, Release release, Log log) throws IOException {
        log.log("downloading " + release.tag);
        File target = new File(context.getCacheDir(), "update.apk");
        HttpURLConnection conn = (HttpURLConnection) new URL(release.apkUrl).openConnection();
        conn.setRequestProperty("User-Agent", "NearChain-Android");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        try (InputStream in = conn.getInputStream();
             OutputStream out = new FileOutputStream(target)) {
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
        } finally {
            conn.disconnect();
        }
        log.log("downloaded " + target.length() + " bytes");
        return target;
    }

    /**
     * Hands the APK to the system package installer. Android shows its own confirmation
     * screen, which InstallReceiver opens. The signature must match the installed app.
     */
    public static void install(Context context, File apk, Log log) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try (InputStream in = new FileInputStream(apk);
             OutputStream out = session.openWrite("NearChain", 0, apk.length())) {
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
            session.fsync(out);
        }
        Intent intent = new Intent(context, InstallReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        // Android stops this app when the update replaces it, so it cannot reopen itself.
        // The notification stays behind, and tapping it starts the updated app.
        postReopenNotification(context);
        session.commit(pending.getIntentSender());
        session.close();
        log.log("install handed to Android");
    }

    private static void postReopenNotification(Context context) {
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    "updates", "Updates", NotificationManager.IMPORTANCE_DEFAULT));
            builder = new Notification.Builder(context, "updates");
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setContentTitle("NearChain update installed")
                .setContentText("Tap to open the new version")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setContentIntent(openPending);
        manager.notify(42, builder.build());
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayBuilder builder = new ByteArrayBuilder();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            builder.write(chunk, read);
        }
        return new String(builder.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Minimal growable byte buffer, so the code avoids newer java.io helpers. */
    private static final class ByteArrayBuilder {
        private byte[] data = new byte[8192];
        private int size;

        void write(byte[] src, int len) {
            if (size + len > data.length) {
                byte[] grown = new byte[Math.max(data.length * 2, size + len)];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            System.arraycopy(src, 0, data, size, len);
            size += len;
        }

        byte[] toByteArray() {
            byte[] out = new byte[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }
    }
}