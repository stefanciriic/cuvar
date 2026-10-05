package com.cuvar.app;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Prima "tapni za preuzimanje" iz obaveštenja i sistemsko "preuzimanje završeno". */
public final class UpdateReceiver extends BroadcastReceiver {

    private static final String PREFS = "cuvar_update";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Updater.ACTION_START_DOWNLOAD.equals(action)) {
            Updater.startDownload(context, intent.getStringExtra(Updater.EXTRA_URL));
        } else if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == -1) {
                return;
            }
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long expected = sp.getLong("download_id", -1);
            if (id == expected) {
                Updater.installDownloaded(context, id);
            }
        }
    }
}
