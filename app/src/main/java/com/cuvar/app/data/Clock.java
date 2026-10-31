package com.cuvar.app.data;

import com.cuvar.app.rules.TrustedClock;

import android.os.SystemClock;
import android.provider.Settings;

import java.util.Calendar;
import java.util.TimeZone;
import org.json.JSONException;
import org.json.JSONObject;

/** Pouzdano vreme: meri se od paljenja telefona, pa pomeranje sata ne menja pravila. */
public final class Clock {
    private final Store store;

    Clock(Store store) {
        this.store = store;
    }

    android.content.ContentResolver resolver;
    long clockOff;             // pouzdano vreme = vreme od paljenja + clockOff
    long clockSaved;           // kada je pouzdano vreme poslednji put sačuvano (vreme od paljenja)
    long skewSaved;            // koliko je sat telefona bio pomeren pri poslednjem čuvanju
    TimeZone zone;             // vremenska zona zapamćena u ovom paljenju telefona

    /**
     * U jednom paljenju telefona vreme se meri od paljenja, pa pomeranje sata ne skraćuje režim
     * i ne donosi novu šifru ranije. Pamti se i koliko je sat telefona ručno pomeren u odnosu na
     * pouzdano vreme, pa ni pomeranje sata pa restart ne pomaže: posle paljenja se ta razlika oduzme.
     * Kad je uključeno automatsko vreme (sa mreže), sat telefona je pouzdan i prati se.
     */
    void startClock() {
        JSONObject o = Store.parse(store.sp.getString("clock", "{}"));
        long el = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        boolean sameBoot = TrustedClock.sameBoot(o.has("off"), store.boot, o.optInt("boot", -2), el, o.optLong("el", Long.MAX_VALUE));
        if (sameBoot) {
            clockOff = o.optLong("off");
            zone = TimeZone.getTimeZone(o.optString("zone", TimeZone.getDefault().getID()));
        } else {
            clockOff = TrustedClock.offsetAfterBoot(wall, el, o.optLong("last", 0L), o.optLong("skew", 0L), autoTime());
            // Ručno izabrana zona ne važi ni posle restarta; prati se samo automatska zona.
            zone = autoZone() || !o.has("zone") ? TimeZone.getDefault() : TimeZone.getTimeZone(o.optString("zone"));
        }
        saveClock();
    }

    void saveClock() {
        long el = SystemClock.elapsedRealtime();
        try {
            JSONObject o = new JSONObject();
            o.put("off", clockOff);
            o.put("boot", store.boot);
            o.put("el", el);
            o.put("last", el + clockOff);
            skewSaved = System.currentTimeMillis() - (el + clockOff);
            o.put("skew", skewSaved);
            o.put("zone", zone.getID());
            store.sp.edit().putString("clock", o.toString()).apply();
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
        }
        clockSaved = el;
    }

    /** Pouzdano trenutno vreme u milisekundama. */
    public long now() {
        synchronized (store) {
            long el = SystemClock.elapsedRealtime();
            long wall = System.currentTimeMillis();
            int action = TrustedClock.onRead(el, wall, clockOff, skewSaved, clockSaved, this::autoTime);
            if (action == TrustedClock.RESET) clockOff = wall - el;
            if (action != TrustedClock.KEEP) saveClock();
            return el + clockOff;
        }
    }

    long autoTimeAt = -1;
    boolean autoTime;

    /** Da li telefon uzima vreme sa mreže (provera najviše jednom u 10 s). */
    boolean autoTime() {
        long el = SystemClock.elapsedRealtime();
        if (autoTimeAt < 0 || el - autoTimeAt > 10000L) {
            try {
                autoTime = Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 0) == 1;
            } catch (Throwable t) {
                autoTime = false;
            }
            autoTimeAt = el;
        }
        return autoTime;
    }

    /**
     * Vremenska zona: ručna promena zone ne pomera režim do restarta telefona.
     * Kad telefon sam bira zonu (putovanje), prati se sistemska.
     */
    TimeZone zone() {
        TimeZone sys = TimeZone.getDefault();
        if (!sys.getID().equals(zone.getID()) && autoZone()) {
            zone = sys;
            saveClock();
        }
        return zone;
    }

    boolean autoZone() {
        try {
            return Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    Calendar calendarNow() {
        Calendar c = Calendar.getInstance(zone());
        c.setTimeInMillis(now());
        return c;
    }
}
