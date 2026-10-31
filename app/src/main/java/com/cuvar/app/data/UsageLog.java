package com.cuvar.app.data;

import com.cuvar.app.rules.ItemKey;
import com.cuvar.app.rules.UsageCalendar;
import com.cuvar.app.ui.Ui;

import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** Izmereno vreme, broj otvaranja i notifikacija po danu. */
public final class UsageLog {
    private final Store store;

    UsageLog(Store store) {
        this.store = store;
    }

    JSONObject usage;  // dan -> {ključ: ms}
    JSONObject opens;  // dan -> {"app:paket" ili "try:ključ": koliko puta}

    void prune() {
        List<String> days = Store.keysOf(usage);
        Collections.sort(days);
        while (days.size() > 14) {
            usage.remove(days.remove(0));
            store.dirty = true;
        }
    }

    /**
     * Istorija sajtova se ne čuva: brišu se svi zapisi "web:" (ranije se merio svaki sajt) i vreme
     * sajtova sa pravilom iz ranijih dana. Današnje vreme sajtova sa pravilom ostaje jer ga troši limit.
     */
    void dropSiteHistory() {
        String today = store.day();
        boolean changed = false;
        for (String dk : Store.keysOf(usage)) {
            JSONObject d = usage.optJSONObject(dk);
            if (d == null) continue;
            for (String k : Store.keysOf(d)) {
                if (k.startsWith("web:") || (ItemKey.isSite(k) && !dk.equals(today))) {
                    d.remove(k);
                    changed = true;
                }
            }
        }
        if (changed) store.sp.edit().putString("usage", usage.toString()).apply();
    }

    /** Interval po pouzdanom vremenu: deo pre ponoći pripada prethodnom danu. */
    public void addUsageBetween(String key, long from, long to) {
        synchronized (store) {
            if (from >= to) return;
            for (Map.Entry<String, Long> part : UsageCalendar.split(from, to, store.clock.zone()).entrySet()) {
                JSONObject d = usage.optJSONObject(part.getKey());
                try {
                    if (d == null) {
                        d = new JSONObject();
                        usage.put(part.getKey(), d);
                    }
                    d.put(key, d.optLong(key, 0L) + part.getValue());
                } catch (JSONException ignored) {
                }
            }
            prune();
            store.dirty = true;
            if (SystemClock.elapsedRealtime() - store.lastSave > 20000L) {
                store.flush();
            }
        }
    }

    public long usedToday(String key) {
        synchronized (store) {
            JSONObject d = usage.optJSONObject(store.day());
            return d == null ? 0L : d.optLong(key, 0L);
        }
    }

    public Map<String, Long> todayMap() {
        synchronized (store) {
            Map<String, Long> out = new HashMap<>();
            JSONObject d = usage.optJSONObject(store.day());
            if (d != null) {
                for (String k : Store.keysOf(d)) {
                    if (!k.equals(DayLimits.GUARDED_KEY)) out.put(k, d.optLong(k, 0L));
                }
            }
            return out;
        }
    }

    /** Ključevi poslednjih n kalendarskih dana zaključno sa danas (najstariji prvi), i dani bez korišćenja. */
    public List<String> lastDays(int n) {
        synchronized (store) {
            Calendar c = store.clock.calendarNow();
            c.add(Calendar.DAY_OF_MONTH, -(n - 1));
            List<String> out = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                out.add(UsageCalendar.dayKey(c));
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
            return out;
        }
    }

    /** Da li za dan postoji ijedan zapis (pre instalacije Čuvara ih nema). */
    public boolean hasDay(String dayKey) {
        synchronized (store) {
            return usage.has(dayKey);
        }
    }

    public Map<String, Long> dayMap(String dayKey) {
        synchronized (store) {
            Map<String, Long> out = new HashMap<>();
            JSONObject d = usage.optJSONObject(dayKey);
            if (d != null) {
                for (String k : Store.keysOf(d)) {
                    if (!k.equals(DayLimits.GUARDED_KEY)) out.put(k, d.optLong(k, 0L));
                }
            }
            return out;
        }
    }

    /** Dan kao "pet 3.10." za prikaz u statistici. */
    public static String dayLabel(String dayKey) {
        try {
            Date d = new SimpleDateFormat("yyyyMMdd", Locale.US).parse(dayKey);
            return new SimpleDateFormat("EEE d.M.", Ui.locale()).format(d);
        } catch (Exception e) {
            return dayKey;
        }
    }

    /** Koliko je danas puta otvoreno ("app:paket") ili pokušano ("try:ključ"). */
    public int opensToday(String key) {
        synchronized (store) {
            JSONObject d = opens.optJSONObject(store.day());
            return d == null ? 0 : d.optInt(key, 0);
        }
    }

    /** Broji jedno otvaranje ili pokušaj; vraća novi broj za danas. */
    public int countOpen(String key) {
        synchronized (store) {
            String k = store.day();
            JSONObject d = opens.optJSONObject(k);
            try {
                if (d == null) {
                    d = new JSONObject();
                    opens.put(k, d);
                    List<String> days = Store.keysOf(opens);
                    Collections.sort(days);
                    while (days.size() > 14) opens.remove(days.remove(0));
                }
                d.put(key, d.optInt(key, 0) + 1);
            } catch (JSONException ignored) {
            }
            store.sp.edit().putString("opens", opens.toString()).apply();
            return d.optInt(key, 0);
        }
    }
}
