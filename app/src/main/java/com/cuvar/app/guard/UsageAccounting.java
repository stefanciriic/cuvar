package com.cuvar.app.guard;

import com.cuvar.app.rules.ItemKey;
import com.cuvar.app.GuardService;
import com.cuvar.app.data.DayLimits;
import com.cuvar.app.data.Sessions;
import com.cuvar.app.rules.LimitWarnings;
import com.cuvar.app.rules.UsageTracker;
import com.cuvar.app.ui.Ui;

import android.content.Context;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Upis izmerenog vremena za aplikaciju i sajt koji su napred, i upozorenja pred kraj limita. */
public final class UsageAccounting {
    private final GuardService svc;

    public UsageAccounting(GuardService svc) {
        this.svc = svc;
    }

    private final LimitWarnings limitWarnings = new LimitWarnings();

    public Set<String> usageKeys() {
        Set<String> keys = new HashSet<>();
        if (!svc.observationValid || svc.store == null || svc.power == null || svc.keyguard == null || !svc.power.isInteractive()
                || svc.keyguard.isKeyguardLocked() || svc.screen.overlay != null || svc.currentPkg == null) return keys;
        if (svc.currentCall) return keys; // poziv ne troši limit aplikacije ni ukupni dnevni limit
        if (tracked(svc, svc.currentPkg)) keys.add(svc.currentPkg);
        for (String host : svc.visibleHosts) {
            // Meri se samo vreme sajtova sa pravilom (za limit); istorija ostalih sajtova se ne čuva.
            for (String domain : svc.store.matchingSitesNow(host)) keys.add(ItemKey.site(domain));
        }
        if (countsForDayLimit()) keys.add(DayLimits.GUARDED_KEY);
        return keys;
    }

    /**
     * Ukupni dnevni limit troši samo ono što on i zaključa: aplikacija sa pravilom ili sajt sa pravilom.
     * Mape, pozivi i sve ostalo bez pravila ga ne troše.
     */
    private boolean countsForDayLimit() {
        if (svc.kinds.exempt().contains(svc.currentPkg)) return false;
        if (svc.kinds.guarded(svc.currentPkg)) return true;
        for (String host : svc.visibleHosts) if (svc.store.siteGuarded(host) != null) return true;
        return false;
    }

    public void accountUntilNow(Set<String> nextKeys) {
        if (svc.store == null) return;
        try {
            long elapsed = SystemClock.elapsedRealtime();
            long epoch = svc.store.now();
            UsageTracker.Interval interval = nextKeys == null ? svc.usageTracker.checkpoint(elapsed, epoch)
                    : svc.usageTracker.transition(elapsed, epoch, nextKeys);
            if (interval == null) return;
            long dt = interval.toMs - interval.fromMs;
            for (String key : interval.keys) {
                svc.store.usageLog.addUsageBetween(key, interval.fromMs, interval.toMs);
                if (ItemKey.isSite(key) || key.startsWith("web:") || key.equals(DayLimits.GUARDED_KEY)) continue;
                long left = svc.store.sessions.addSession(key, dt);
                if (left > 0 && left <= 60000L && left + dt > 60000L) {
                    Ui.toast(svc, "Čuvar: još minut u komadu, pa pauza od "
                            + Sessions.SESSION_BREAK_MS / 60000L + " min.");
                }
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("usage", error);
        }
    }

    private static final Map<String, Boolean> TRACKED = new HashMap<>();

    /**
     * Meri se samo vreme aplikacija koje imaju ikonicu u meniju. Sistemski prozori bez ikonice
     * (biometrija, dozvole, instalacija) ne ulaze u statistiku ni u limite.
     */
    public static synchronized void forgetTracked(String pkg) {
        if (pkg != null) TRACKED.remove(pkg);
    }

    public static synchronized boolean tracked(Context c, String pkg) {
        Boolean t = TRACKED.get(pkg);
        if (t == null) {
            try {
                t = c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
            } catch (Throwable e) {
                t = true;
            }
            TRACKED.put(pkg, t);
        }
        return t;
    }

    /**
     * Upozorenje 5 i 1 min pre isteka limita aplikacije ili sajta, jednom po pragu u danu,
     * da se stigne završiti bez naglog prekida.
     */
    public void warnLimit(String key, String name, int limitMin, long used) {
        long left = limitMin * 60000L - used;
        if (left <= 0 || left > LimitWarnings.FIRST_MS) return;
        if (!limitWarnings.shouldWarn(key, svc.store.day(), left)) return;
        Ui.toast(svc, "Čuvar: limit ističe za " + Ui.fmt(left + 59999L) + " – " + name);
    }
}
