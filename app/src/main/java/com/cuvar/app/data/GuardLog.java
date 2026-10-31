package com.cuvar.app.data;

import com.cuvar.app.ui.Ui;

import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Zapis o tome kada Čuvar nije radio (ugašen servis, restart telefona). */
public final class GuardLog {
    private final Store store;

    GuardLog(Store store) {
        this.store = store;
    }

    long beatSaved;

    /** Servis radi; čuva se najviše jednom u minutu, ili odmah kad force. */
    public void guardBeat(boolean force) {
        synchronized (store) {
            long el = SystemClock.elapsedRealtime();
            if (!force && el - beatSaved < 60000L) return;
            saveBeat(false);
        }
    }

    /** Servis je ugašen (isključen u Pristupačnosti ili zaustavljen). */
    public void guardStopped() {
        synchronized (store) {
            saveBeat(true);
        }
    }

    void saveBeat(boolean stopped) {
        long el = SystemClock.elapsedRealtime();
        try {
            JSONObject o = new JSONObject();
            o.put("t", store.now());
            o.put("el", el);
            o.put("boot", store.boot);
            o.put("stopped", stopped);
            store.sp.edit().putString("beat", o.toString()).apply();
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
        }
        beatSaved = el;
    }

    /**
     * Pri pokretanju servisa: ako je posle poslednjeg znaka života prošlo vreme u kome Čuvar nije radio,
     * to se zapisuje. Safe Mode se vidi po tome što je telefon u međuvremenu paljen više puta.
     */
    public void guardStarted() {
        synchronized (store) {
            JSONObject b = Store.parse(store.sp.getString("beat", "{}"));
            long el = SystemClock.elapsedRealtime();
            long t = store.now();
            if (b.has("t")) {
                int lastBoot = b.optInt("boot", -1);
                long lastT = b.optLong("t");
                if (store.boot != -1 && lastBoot == store.boot) {
                    long gap = el - b.optLong("el", el);
                    if (gap > 2 * 60000L) {
                        logGap(lastT, t, b.optBoolean("stopped") ? "isključen u Pristupačnosti" : "zaustavljen (verovatno zbog baterije)");
                    }
                } else {
                    long bootAt = t - el;
                    if (store.boot != -1 && lastBoot != -1 && store.boot - lastBoot > 1) {
                        logGap(lastT, bootAt, "telefon je paljen bez Čuvara, na primer u Safe Mode-u");
                    }
                    if (el > 3 * 60000L) {
                        logGap(bootAt, t, "posle paljenja telefona nije radio " + Ui.fmt(el));
                    }
                }
            }
            saveBeat(false);
        }
    }

    void logGap(long from, long to, String what) {
        JSONArray a;
        try {
            a = new JSONArray(store.sp.getString("gaps", "[]"));
        } catch (JSONException e) {
            a = new JSONArray();
        }
        try {
            a.put(new JSONObject().put("from", from).put("to", to).put("what", what));
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
        }
        while (a.length() > 20) a.remove(0);
        store.sp.edit().putString("gaps", a.toString()).apply();
    }

    /** Zapisi iz poslednjih n dana, najnoviji prvi, npr. "pet 9.10. 14:05–14:40: isključen u Pristupačnosti". */
    public List<String> guardGaps(int days) {
        synchronized (store) {
            List<String> out = new ArrayList<>();
            JSONArray a;
            try {
                a = new JSONArray(store.sp.getString("gaps", "[]"));
            } catch (JSONException e) {
                return out;
            }
            long since = store.now() - days * 86400000L;
            SimpleDateFormat f = new SimpleDateFormat("EEE d.M. HH:mm", Ui.locale());
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
            f.setTimeZone(store.clock.zone());
            hm.setTimeZone(store.clock.zone());
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || o.optLong("to") < since) continue;
                out.add(f.format(new Date(o.optLong("from"))) + "–" + hm.format(new Date(o.optLong("to")))
                        + ": " + o.optString("what"));
            }
            return out;
        }
    }
}
