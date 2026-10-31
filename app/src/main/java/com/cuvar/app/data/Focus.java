package com.cuvar.app.data;

import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.web.Hosts;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Ručni Fokus: šta je dozvoljeno i do kada. */
public final class Focus {
    private final Store store;

    Focus(Store store) {
        this.store = store;
    }

    final Set<String> focusApps = new HashSet<>();
    final Set<String> focusSites = new HashSet<>();
    long focusUntil;

    void loadFocus() {
        JSONObject o = Store.parse(store.sp.getString("focus", "{}"));
        focusUntil = Math.max(0L, o.optLong("until", 0L));
        JSONArray a = o.optJSONArray("apps");
        for (int i = 0; a != null && i < a.length(); i++) {
            String p = a.optString(i, "");
            if (!p.isEmpty()) focusApps.add(p);
        }
        JSONArray s = o.optJSONArray("sites");
        for (int i = 0; s != null && i < s.length(); i++) {
            String d = s.optString(i, "");
            if (!d.isEmpty()) focusSites.add(d);
        }
    }

    void saveFocus() {
        try {
            JSONObject o = new JSONObject();
            o.put("until", focusUntil);
            o.put("apps", new JSONArray(focusApps));
            o.put("sites", new JSONArray(focusSites));
            store.sp.edit().putString("focus", o.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public boolean focusActive() {
        synchronized (store) {
            if (focusUntil <= store.now()) {
                if (focusUntil != 0L || !focusApps.isEmpty() || !focusSites.isEmpty()) {
                    focusUntil = 0L;
                    focusApps.clear();
                    focusSites.clear();
                    saveFocus();
                }
                return false;
            }
            return true;
        }
    }

    public long focusLeft() {
        synchronized (store) {
            return focusActive() ? Math.max(0L, focusUntil - store.now()) : 0L;
        }
    }

    public Set<String> focusApps() {
        synchronized (store) {
            return new HashSet<>(focusApps);
        }
    }

    public Set<String> focusSites() {
        synchronized (store) {
            return new HashSet<>(focusSites);
        }
    }

    public boolean focusHasSites() {
        synchronized (store) {
            return focusActive() && !focusSites.isEmpty();
        }
    }

    public boolean focusAllowsApp(String pkg) {
        synchronized (store) {
            return focusActive() && focusApps.contains(pkg);
        }
    }

    public boolean focusAllowsSite(String host) {
        synchronized (store) {
            return focusActive() && host != null && DailySchedule.matchDomain(host, focusSites) != null;
        }
    }

    public void startFocus(long durationMs, Collection<String> apps, Collection<String> sites) {
        synchronized (store) {
            focusApps.clear();
            focusApps.addAll(apps);
            focusSites.clear();
            for (String site : sites) {
                String host = Hosts.hostOf(site);
                if (host != null && host.contains(".")) focusSites.add(host);
            }
            focusUntil = store.now() + Math.max(1L, durationMs);
            saveFocus();
        }
    }

    public void stopFocus() {
        synchronized (store) {
            focusUntil = 0L;
            focusApps.clear();
            focusSites.clear();
            saveFocus();
        }
    }
}
