package com.cuvar.app;

import com.cuvar.app.data.Store;
import com.cuvar.app.rules.NotifCounts;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.Set;

/**
 * Broji primljene notifikacije za Statistiku. Čita samo paket i oznake notifikacije,
 * nikad naslov ni tekst, i ne dira ni ne sakriva notifikacije.
 */
public class NotificationCounter extends NotificationListenerService {
    /** Notifikacije koje su već izbrojane (da se tiha izmena iste ne broji ponovo). */
    private final Set<String> seen = new HashSet<>();

    /** Da li je korisnik uključio „Pristup obaveštenjima“ za Čuvara. */
    public static boolean granted(android.content.Context c) {
        try {
            String list = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            return list != null && list.contains(new android.content.ComponentName(c, NotificationCounter.class).flattenToString());
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null) return;
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(getPackageName()) || pkg.equals("android") || pkg.equals("com.android.systemui")) return;
            Notification n = sbn.getNotification();
            int flags = n == null ? 0 : n.flags;
            boolean ongoing = sbn.isOngoing() || (flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
            boolean summary = (flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            boolean alertOnce = (flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0;
            String key = sbn.getKey();
            boolean seenBefore = key != null && seen.contains(key);
            if (key != null) {
                if (seen.size() > 2000) seen.clear();
                seen.add(key);
            }
            if (!NotifCounts.counts(ongoing, summary, alertOnce, seenBefore)) return;
            Store.get(this).usageLog.countNotification(pkg);
        } catch (Throwable ignored) {
        }
    }
}
