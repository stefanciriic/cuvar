package com.cuvar.app.data;

import com.cuvar.app.rules.RemovalWindow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Zahtevi za uklanjanje iz pravila. Klik na „Ukloni iz pravila“ ništa ne skida: sutradan od 08:00 do 22:00
 * korisnik potvrđuje, i tek tada se stavka skida. Nepotvrđen zahtev propada i pravilo ostaje.
 */
public final class Removals {
    private final Store store;

    Removals(Store store) {
        this.store = store;
    }

    /** Jedan zahtev: aplikacija (ili null) i domeni koji se skidaju zajedno sa njom. */
    public static final class Request {
        public final String key;
        public final String pkg;
        public final Set<String> sites;
        public final int state;
        /** Pitanje stiže danas od 08:00 (zahtev je dat posle ponoći), a ne sutra. */
        public final boolean today;

        Request(String key, String pkg, Set<String> sites, int state, boolean today) {
            this.key = key;
            this.pkg = pkg;
            this.sites = sites;
            this.state = state;
            this.today = today;
        }
    }

    /** Zapamti zahtev; isti ključ (paket ili domen) zamenjuje stari, a rok se računa od danas. */
    public void request(String pkg, Collection<String> domains) {
        synchronized (store) {
            String key = pkg != null ? pkg : domains.iterator().next();
            JSONArray out = new JSONArray();
            for (JSONObject o : load()) if (!key.equals(o.optString("key"))) out.put(o);
            try {
                out.put(new JSONObject().put("key", key).put("pkg", pkg == null ? "" : pkg)
                        .put("sites", new JSONArray(domains)).put("day", store.rulesDay()));
            } catch (JSONException ignored) {
                // put sa ispravnim ključem i vrednošću ne baca grešku
            }
            save(out);
        }
    }

    /** Da li bi pitanje za zahtev dat sada stiglo danas od 08:00 (posle ponoći, a pre 06:00). */
    public boolean newRequestAsksToday() {
        synchronized (store) {
            return RemovalWindow.asksToday(store.rulesDay(), now().toLocalDate());
        }
    }

    /** Zahtevi koji važe (propali se brišu). */
    public List<Request> list() {
        synchronized (store) {
            List<Request> out = new ArrayList<>();
            JSONArray keep = new JSONArray();
            LocalDateTime now = now();
            boolean dropped = false;
            for (JSONObject o : load()) {
                int state = RemovalWindow.state(o.optString("day"), now);
                if (state == RemovalWindow.EXPIRED) {
                    dropped = true;
                    continue;
                }
                keep.put(o);
                Set<String> sites = new LinkedHashSet<>();
                JSONArray a = o.optJSONArray("sites");
                for (int i = 0; a != null && i < a.length(); i++) {
                    String d = a.optString(i, "");
                    if (!d.isEmpty()) sites.add(d);
                }
                String pkg = o.optString("pkg", "");
                out.add(new Request(o.optString("key"), pkg.isEmpty() ? null : pkg, sites, state,
                        RemovalWindow.asksToday(o.optString("day"), now.toLocalDate())));
            }
            if (dropped) save(keep);
            return out;
        }
    }

    /** Potvrda: skida stavku odmah, ali samo dok traje vreme za potvrdu. */
    public boolean confirm(String key) {
        synchronized (store) {
            for (Request r : list()) {
                if (!r.key.equals(key)) continue;
                if (r.state != RemovalWindow.ASK) return false;
                cancel(key);
                store.removeNow(r.pkg, r.sites);
                return true;
            }
            return false;
        }
    }

    /** Odustaje od zahteva: pravilo ostaje. */
    public void cancel(String key) {
        synchronized (store) {
            JSONArray out = new JSONArray();
            for (JSONObject o : load()) if (!key.equals(o.optString("key"))) out.put(o);
            save(out);
        }
    }

    private LocalDateTime now() {
        Calendar c = store.clock.calendarNow();
        return LocalDateTime.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private List<JSONObject> load() {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = Store.parseArray(store.sp.getString("removals", null));
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !o.optString("key").isEmpty()) out.add(o);
        }
        return out;
    }

    private void save(JSONArray a) {
        store.sp.edit().putString("removals", a.toString()).apply();
    }
}
