package com.cuvar.app.guard;

import com.cuvar.app.rules.ForegroundTime;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Vreme po aplikaciji za prikaz u statistici, iz Androidovih zapisa (isti izvor kao Digital Wellbeing).
 * Čuvarovo merenje ne broji pozive ni vreme kad nije video šta je napred, pa je pokazivalo manje.
 * Limiti i dalje troše Čuvarovo merenje po pouzdanom satu. Upit ide na posebnoj niti (sporo na nekim telefonima).
 */
public final class AndroidUsage {
    private AndroidUsage() { }

    private static final long TODAY_FRESH_MS = 30000L;
    private static final Map<String, Map<String, Long>> CACHE = new HashMap<>();
    private static final Map<String, Long> CACHED_AT = new HashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cuvar-usage-day");
        t.setDaemon(true);
        return t;
    });

    /**
     * Vreme po paketu za dan "yyyyMMdd" ako je već izračunato, inače null. Kad podatak fali ili je star,
     * računa se u pozadini i posle se pozove onReady (na pozadinskoj niti).
     */
    public static Map<String, Long> day(Context c, String dayKey, String todayKey, Runnable onReady) {
        if (!ForegroundApp.granted(c)) return null;
        Context app = c.getApplicationContext();
        synchronized (CACHE) {
            Map<String, Long> m = CACHE.get(dayKey);
            Long at = CACHED_AT.get(dayKey);
            boolean stale = m == null || (dayKey.equals(todayKey) && at != null
                    && SystemClock.elapsedRealtime() - at > TODAY_FRESH_MS);
            if (stale && PENDING.add(dayKey)) {
                try {
                    WORKER.execute(() -> load(app, dayKey, onReady));
                } catch (RuntimeException rejected) {
                    PENDING.remove(dayKey);
                }
            }
            return m;
        }
    }

    private static void load(Context c, String dayKey, Runnable onReady) {
        boolean ok = false;
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new SimpleDateFormat("yyyyMMdd", Locale.US).parse(dayKey));
            long from = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            // Sat telefona je ovde potreban: Android beleži događaje po njemu (samo prikaz, ne pravila).
            long to = Math.min(cal.getTimeInMillis(), System.currentTimeMillis());
            Map<String, Long> result = Collections.emptyMap();
            if (to > from) {
                UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
                // Tri sata pre ponoći, da se zna šta je već bilo otvoreno kad je dan počeo.
                UsageEvents events = usm == null ? null : usm.queryEvents(from - 3 * 3600000L, to);
                List<ForegroundTime.Event> list = new ArrayList<>();
                UsageEvents.Event e = new UsageEvents.Event();
                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(e);
                    int type = e.getEventType();
                    if (type == ForegroundTime.RESUMED || type == ForegroundTime.PAUSED || type == ForegroundTime.STOPPED
                            || type == ForegroundTime.SCREEN_OFF || type == ForegroundTime.KEYGUARD_SHOWN
                            || type == ForegroundTime.SHUTDOWN) {
                        list.add(new ForegroundTime.Event(e.getTimeStamp(), type, e.getPackageName(), e.getClassName()));
                    }
                }
                if (!list.isEmpty()) result = ForegroundTime.perApp(list, from, to);
            }
            synchronized (CACHE) {
                CACHE.put(dayKey, result);
                CACHED_AT.put(dayKey, SystemClock.elapsedRealtime());
            }
            ok = true;
        } catch (Throwable error) {
            GuardDiagnostics.report("androidUsage", error);
        } finally {
            synchronized (CACHE) { PENDING.remove(dayKey); }
        }
        if (ok && onReady != null) {
            try {
                onReady.run();
            } catch (Throwable error) {
                GuardDiagnostics.report("androidUsageDone", error);
            }
        }
    }
}
