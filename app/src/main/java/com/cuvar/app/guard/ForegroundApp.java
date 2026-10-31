package com.cuvar.app.guard;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Koja je aplikacija napred, onako kako je beleži sam Android (dozvola „Pristup korišćenju“).
 * Ovo ne dira druge aplikacije, pa Čuvar kroz Pristupačnost prati samo aplikacije sa pravilom i pregledače.
 */
public final class ForegroundApp {

    private final UsageStatsManager usm;
    private long lastQuery;
    private volatile String last;
    private volatile long lastAt;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cuvar-usage-events");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean queryPending = new AtomicBoolean();

    public ForegroundApp(Context c) {
        usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    /** Gasi pozadinsku nit kad se servis ugasi; posle toga se ništa više ne javlja. */
    public void close() {
        worker.shutdownNow();
    }

    /** Da li je korisnik dozvolio Čuvaru pristup korišćenju. */
    public static boolean granted(Context c) {
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
    public long since() {
        return lastAt;
    }

    /** Poslednja aplikacija koja je došla napred, ili null ako nije poznata. */
    public String current() {
        return last;
    }

    /** UsageStats IPC and event iteration can be slow on vendor builds; never run it on the service/UI thread. */
    public void refresh(Runnable onUpdated) {
        if (usm == null || worker.isShutdown() || !queryPending.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> query(onUpdated));
        } catch (RuntimeException rejected) {
            // Nit je ugašena (servis se gasi): sledeći poziv ne sme da čeka zauvek.
            queryPending.set(false);
        }
    }

    private void query(Runnable onUpdated) {
        String old;
        synchronized (this) { old = last; }
        try {
            // Sat telefona je ovde potreban: UsageStats beleži događaje po njemu (ne odlučuje o pravilima).
            long now = System.currentTimeMillis();
            long from = lastQuery == 0 ? now - 10 * 60000L : Math.min(lastQuery - 2000L, now - 1000L);
            UsageEvents events = usm.queryEvents(from, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String found = old;
            long foundAt = lastAt;
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && e.getPackageName() != null) {
                    found = e.getPackageName();
                    foundAt = e.getTimeStamp();
                }
            }
            synchronized (this) {
                last = found;
                lastAt = foundAt;
                lastQuery = now;
            }
        } catch (Throwable error) {
            // Ostaje poslednja poznata aplikacija; prolazna greška UsageStats-a ne sme da zaustavi pravila.
            GuardDiagnostics.report("usageEvents", error);
        } finally {
            queryPending.set(false);
            try {
                if (onUpdated != null && !worker.isShutdown()) onUpdated.run();
            } catch (Throwable error) {
                GuardDiagnostics.report("usageEventsDone", error);
            }
        }
    }
}
