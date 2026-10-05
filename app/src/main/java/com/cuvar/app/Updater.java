package com.cuvar.app;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Provera nove verzije na GitHub Releases, preuzimanje i pokretanje instalacije. */
final class Updater {

    static final String ACTION_START_DOWNLOAD = "com.cuvar.app.action.START_DOWNLOAD";
    static final String EXTRA_URL = "url";

    private static final String REPO = "stefanciriic/cuvar";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String ASSET_NAME = "cuvar.apk";
    private static final long CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000;
    private static final String CHANNEL_ID = "updates";
    private static final int NOTIF_AVAILABLE = 1001;
    private static final int NOTIF_PERMISSION = 1002;

    private static final String PREFS = "cuvar_update";

    private Updater() {
    }

    /** Pokreće proveru u pozadini, ali ne češće od CHECK_INTERVAL_MS. Bezbedno za pozivanje sa glavne niti. */
    static void maybeCheck(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("last_check", 0) < CHECK_INTERVAL_MS) {
            return;
        }
        sp.edit().putLong("last_check", now).apply();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> safeCheck(app), "cuvar-update-check").start();
    }

    private static void safeCheck(Context ctx) {
        try {
            check(ctx);
        } catch (Throwable ignored) {
        }
    }

    private static void check(Context ctx) throws Exception {
        String body = httpGet(API_URL);
        JSONObject root = new JSONObject(body);
        String tag = root.optString("tag_name", "");
        int remote = parseVersion(tag);
        if (remote <= BuildConfig.VERSION_CODE) {
            return;
        }

        String downloadUrl = null;
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (ASSET_NAME.equals(a.optString("name"))) {
                    downloadUrl = a.optString("browser_download_url", null);
                    break;
                }
            }
        }
        if (downloadUrl == null) {
            return;
        }

        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (sp.getInt("notified_version", 0) >= remote) {
            return; // već smo obavestili za ovu (ili noviju) verziju
        }
        sp.edit()
                .putInt("notified_version", remote)
                .putString("pending_url", downloadUrl)
                .apply();
        notifyAvailable(ctx, tag, downloadUrl);
    }

    private static int parseVersion(String tag) {
        String digits = tag.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "cuvar-app");
        try (InputStream in = c.getInputStream()) {
            return readAll(in);
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "Ažuriranja", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Obaveštenja o novim verzijama Čuvara.");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private static boolean canNotify(Context ctx) {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static void notifyAvailable(Context ctx, String tag, String downloadUrl) {
        if (!canNotify(ctx)) {
            return;
        }
        ensureChannel(ctx);

        Intent start = new Intent(ctx, UpdateReceiver.class);
        start.setAction(ACTION_START_DOWNLOAD);
        start.putExtra(EXTRA_URL, downloadUrl);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, NOTIF_AVAILABLE, start,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Nova verzija Čuvara: " + tag)
                .setContentText("Tapni da preuzmeš i instaliraš.")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_AVAILABLE, n);
        }
    }

    /** Da li postoji nova verzija koju korisnik još nije preuzeo/instalirao (za prikaz na glavnom ekranu). */
    static String pendingUrl(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pending_url", null);
    }

    static void clearPending(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pending_url").apply();
    }

    static void startDownload(Context ctx, String url) {
        if (url == null) {
            return;
        }
        try {
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                return;
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("Preuzimanje nove verzije Čuvara");
            req.setDestinationInExternalFilesDir(ctx, null, "cuvar-update.apk");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setMimeType("application/vnd.android.package-archive");
            long id = dm.enqueue(req);
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("download_id", id)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /** Pokreće instalaciju preuzetog APK-a, ili, ako nije dozvoljeno, usmeri korisnika na podešavanja. */
    static void installDownloaded(Context ctx, long downloadId) {
        try {
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                return;
            }
            Uri fileUri = dm.getUriForDownloadedFile(downloadId);
            if (fileUri == null) {
                return;
            }
            if (!ctx.getPackageManager().canRequestPackageInstalls()) {
                notifyNeedPermission(ctx);
                return;
            }
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(fileUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(install);
            clearPending(ctx);
        } catch (Throwable ignored) {
        }
    }

    private static void notifyNeedPermission(Context ctx) {
        if (!canNotify(ctx)) {
            return;
        }
        ensureChannel(ctx);
        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + ctx.getPackageName()));
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, NOTIF_PERMISSION, settings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Dozvoli instalaciju ažuriranja")
                .setContentText("Tapni, pa uključi „Dozvoli iz ovog izvora“ za Čuvara.")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_PERMISSION, n);
        }
    }
}
