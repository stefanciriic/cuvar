package com.cuvar.app.data;

import com.cuvar.app.rules.SessionLimit;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Korišćenje u komadu i obavezna pauza posle njega. */
public final class Sessions {
    private final Store store;

    Sessions(Store store) {
        this.store = store;
    }

    JSONObject sessions; // paket -> {u: ms u komadu, l: poslednji put viđena, b: pauza do}

    /** Koliko traje obavezna pauza posle najdužeg dozvoljenog korišćenja u komadu. */
    public static final long SESSION_BREAK_MS = SessionLimit.BREAK_MS;

    /**
     * Dodaje vreme u komadu za aplikaciju sa tim pravilom. Izlazak kraći od pauze ne prekida komad,
     * inače bi se ograničenje zaobišlo kratkim izlaskom na početni ekran. Vraća koliko je ostalo do pauze
     * (0 kad pauza upravo počinje), ili -1 ako aplikacija nema ovo pravilo.
     */
    public long addSession(String pkg, long ms) {
        synchronized (store) {
            int cap = store.appSessionNow(pkg);
            if (cap <= 0) return -1;
            long now = store.now();
            JSONObject o = sessions.optJSONObject(pkg);
            try {
                if (o == null) {
                    o = new JSONObject();
                    sessions.put(pkg, o);
                }
                long oldBreak = o.optLong("b", 0);
                if (oldBreak > now) return 0;
                SessionLimit.State st = new SessionLimit.State(o.optLong("u", 0), o.optLong("l", 0), oldBreak);
                long left = SessionLimit.add(st, cap * 60000L, now, ms);
                o.put("u", st.used);
                o.put("l", st.lastSeen);
                if (st.breakUntil != oldBreak) {
                    o.put("b", st.breakUntil);
                    store.sp.edit().putString("sessions", sessions.toString()).apply();
                    return 0;
                }
                store.dirty = true;
                return left;
            } catch (JSONException e) {
                return -1;
            }
        }
    }

    /** Koliko je ostalo obavezne pauze posle korišćenja u komadu (0 ako je nema). */
    public long sessionBreakLeft(String pkg) {
        synchronized (store) {
            JSONObject o = sessions.optJSONObject(pkg);
            if (o == null) return 0;
            return SessionLimit.breakLeft(o.optLong("b", 0), store.now());
        }
    }

    /** Ne zadržava sesije za aplikacije koje više nemaju pravilo. */
    void pruneSessions() {
        long now = store.now();
        for (String pkg : Store.keysOf(sessions)) {
            JSONObject o = sessions.optJSONObject(pkg);
            if (o == null) {
                sessions.remove(pkg);
                continue;
            }
            boolean hasRule = store.appSession(pkg) > 0 || store.appSessionNow(pkg) > 0;
            if (SessionLimit.stale(hasRule, o.optLong("b", 0L), o.optLong("l", 0L), now)) sessions.remove(pkg);
        }
    }

    /** Aplikacije koje su sada na obaveznoj pauzi. */
    public List<String> appsOnBreak() {
        synchronized (store) {
            List<String> out = new ArrayList<>();
            for (String pkg : Store.keysOf(sessions)) if (sessionBreakLeft(pkg) > 0) out.add(pkg);
            return out;
        }
    }
}
