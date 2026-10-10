package com.cuvar.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

/**
 * Koja je aplikacija napred, onako kako je beleži sam Android (dozvola „Pristup korišćenju“).
 * Ovo ne dira druge aplikacije, pa Čuvar kroz Pristupačnost prati samo aplikacije sa pravilom i pregledače.
 */
final class ForegroundApp {

    private final Context context;
    private final UsageStatsManager usm;
    private long lastQuery;
    private String last;
    private long lastAt;

    ForegroundApp(Context c) {
        context = c.getApplicationContext();
        usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    /** Da li je korisnik dozvolio Čuvaru pristup korišćenju. */
    static boolean granted(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Kada je aplikacija iz current() došla napred (System.currentTimeMillis), ili 0. */
    long since() {
        return lastAt;
    }

    /** Poslednja aplikacija koja je došla napred, ili null ako nije poznata. */
    String current() {
        if (usm == null) return null;
        try {
            long now = System.currentTimeMillis();
            long from = lastQuery == 0 ? now - 10 * 60000L : Math.min(lastQuery - 2000L, now - 1000L);
            UsageEvents events = usm.queryEvents(from, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && e.getPackageName() != null) {
                    last = e.getPackageName();
                    lastAt = e.getTimeStamp();
                }
            }
            lastQuery = now;
        } catch (Throwable t) {
            return null;
        }
        return last;
    }
}
