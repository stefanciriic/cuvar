package com.cuvar.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sva podešavanja i izmereno vreme. Čuva se samo na telefonu. */
final class Store {
    private static Store instance;

    static synchronized Store get(Context c) {
        if (instance == null) {
            instance = new Store(c.getApplicationContext());
        }
        return instance;
    }

    private final SharedPreferences sp;
    private final JSONObject apps;   // paket -> {lock, limit}
    private final JSONObject sites;  // domen -> {limit}
    private final JSONObject usage;  // dan -> {ključ: ms}
    private boolean dirty;
    private long lastSave;
    private int fails;
    private long blockedUntil;

    private Store(Context c) {
        sp = c.getSharedPreferences("cuvar", Context.MODE_PRIVATE);
        apps = parse(sp.getString("apps", "{}"));
        sites = parse(sp.getString("sites", "{}"));
        usage = parse(sp.getString("usage", "{}"));
        prune();
    }

    private static JSONObject parse(String s) {
        try {
            return new JSONObject(s == null ? "{}" : s);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static List<String> keysOf(JSONObject o) {
        List<String> out = new ArrayList<>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }

    // ---------- PIN ----------

    synchronized boolean hasPin() {
        return sp.getString("pin", null) != null;
    }

    synchronized void setPin(String pin) {
        sp.edit().putString("pin", hash(pin)).apply();
        fails = 0;
        blockedUntil = 0;
    }

    /** Vraća null ako je PIN tačan, inače poruku za korisnika. */
    synchronized String tryPin(String pin) {
        long now = SystemClock.elapsedRealtime();
        if (now < blockedUntil) {
            return "Previše pokušaja. Sačekaj " + ((blockedUntil - now) / 1000 + 1) + " s";
        }
        String saved = sp.getString("pin", null);
        if (saved != null && saved.equals(hash(pin))) {
            fails = 0;
            return null;
        }
        fails++;
        if (fails >= 5) {
            fails = 0;
            blockedUntil = now + 30000L;
            return "Previše pokušaja. Sačekaj 30 s";
        }
        return "Pogrešan PIN";
    }

    private static String hash(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("cuvar:" + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "plain:" + pin;
        }
    }

    // ---------- Aplikacije ----------

    synchronized boolean appLock(String pkg) {
        JSONObject o = apps.optJSONObject(pkg);
        return o != null && o.optBoolean("lock", false);
    }

    synchronized int appLimit(String pkg) {
        JSONObject o = apps.optJSONObject(pkg);
        return o == null ? 0 : o.optInt("limit", 0);
    }

    synchronized boolean hasAppRule(String pkg) {
        return apps.has(pkg);
    }

    synchronized int appRuleCount() {
        return apps.length();
    }

    synchronized void setApp(String pkg, boolean lock, int limitMin) {
        setApp(pkg, lock, limitMin, appScheduled(pkg));
    }

    synchronized boolean appScheduled(String pkg) {
        JSONObject o = apps.optJSONObject(pkg);
        return o != null && o.optBoolean("scheduled", false);
    }

    synchronized boolean scheduleEnabled() { return sp.getBoolean("scheduleEnabled", false); }
    synchronized int scheduleStart() { return sp.getInt("scheduleStart", 21 * 60); }
    synchronized int scheduleEnd() { return sp.getInt("scheduleEnd", 9 * 60); }

    synchronized void setSchedule(boolean enabled, int start, int end) {
        sp.edit().putBoolean("scheduleEnabled", enabled).putInt("scheduleStart", start)
                .putInt("scheduleEnd", end).apply();
    }

    synchronized boolean scheduleBlocks(String pkg) {
        return appScheduled(pkg) && scheduleActive();
    }

    synchronized boolean scheduleActive() {
        Calendar now = Calendar.getInstance();
        return scheduleEnabled() && DailySchedule.contains(
                scheduleStart(), scheduleEnd(), now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
    }

    synchronized String scheduleLabel() {
        return DailySchedule.label(scheduleStart()) + "–" + DailySchedule.label(scheduleEnd());
    }

    synchronized List<String> scheduledApps() {
        List<String> out = new ArrayList<>();
        for (String pkg : keysOf(apps)) if (appScheduled(pkg)) out.add(pkg);
        return out;
    }

    synchronized void setAppScheduled(String pkg, boolean selected) {
        setApp(pkg, appLock(pkg), appLimit(pkg), selected);
    }

    synchronized List<String> scheduledSites() {
        List<String> out = new ArrayList<>(sp.getStringSet("scheduledSites", Collections.emptySet()));
        Collections.sort(out);
        return out;
    }

    synchronized void setSiteScheduled(String domain, boolean selected) {
        java.util.Set<String> out = new java.util.HashSet<>(scheduledSites());
        if (selected) out.add(domain); else out.remove(domain);
        sp.edit().putStringSet("scheduledSites", out).apply();
    }

    synchronized String matchScheduledSite(String host) {
        return matchDomain(host, scheduledSites());
    }

    private static String matchDomain(String host, List<String> domains) {
        String best = null;
        for (String d : domains) {
            if ((host.equals(d) || host.endsWith("." + d)) && (best == null || d.length() > best.length())) best = d;
        }
        return best;
    }

    synchronized void setApp(String pkg, boolean lock, int limitMin, boolean scheduled) {
        if (!lock && limitMin <= 0 && !scheduled) {
            apps.remove(pkg);
        } else {
            try {
                JSONObject o = new JSONObject();
                o.put("lock", lock);
                o.put("limit", Math.max(0, limitMin));
                o.put("scheduled", scheduled);
                apps.put(pkg, o);
            } catch (JSONException ignored) {
            }
        }
        sp.edit().putString("apps", apps.toString()).apply();
    }

    // ---------- Sajtovi ----------

    synchronized List<String> siteList() {
        List<String> l = keysOf(sites);
        Collections.sort(l);
        return l;
    }

    /** Limit u minutima; 0 = uvek blokiran; -1 = sajt nije na listi. */
    synchronized int siteLimit(String domain) {
        JSONObject o = sites.optJSONObject(domain);
        return o == null ? -1 : o.optInt("limit", 0);
    }

    synchronized void setSite(String domain, int limitMin) {
        try {
            JSONObject o = new JSONObject();
            o.put("limit", Math.max(0, limitMin));
            sites.put(domain, o);
        } catch (JSONException ignored) {
        }
        sp.edit().putString("sites", sites.toString()).apply();
    }

    synchronized void removeSite(String domain) {
        sites.remove(domain);
        sp.edit().putString("sites", sites.toString()).apply();
    }

    /** Vraća domen sa liste koji pokriva dati host (npr. m.facebook.com -> facebook.com) ili null. */
    synchronized String matchSite(String host) {
        String best = null;
        for (String d : keysOf(sites)) {
            if (host.equals(d) || host.endsWith("." + d)) {
                if (best == null || d.length() > best.length()) {
                    best = d;
                }
            }
        }
        return best;
    }

    /** Iz adrese vadi čist domen: "https://www.Facebook.com/x" -> "facebook.com". */
    static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        String s = url.replaceAll("[\\u200b-\\u200f\\u202a-\\u202e\\u2066-\\u2069]", "")
                .trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.contains(" ")) {
            return null;
        }
        int i = s.indexOf("://");
        if (i >= 0) {
            s = s.substring(i + 3);
        }
        int cut = s.length();
        for (char ch : new char[]{'/', '?', '#'}) {
            int k = s.indexOf(ch);
            if (k >= 0 && k < cut) {
                cut = k;
            }
        }
        s = s.substring(0, cut);
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(0, colon);
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? null : s;
    }

    // ---------- Izmereno vreme ----------

    private static String day() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private JSONObject today() {
        String k = day();
        JSONObject d = usage.optJSONObject(k);
        if (d == null) {
            d = new JSONObject();
            try {
                usage.put(k, d);
            } catch (JSONException ignored) {
            }
            prune();
        }
        return d;
    }

    private void prune() {
        List<String> days = keysOf(usage);
        Collections.sort(days);
        while (days.size() > 14) {
            usage.remove(days.remove(0));
            dirty = true;
        }
    }

    synchronized void addUsage(String key, long ms) {
        JSONObject d = today();
        try {
            d.put(key, d.optLong(key, 0L) + ms);
        } catch (JSONException ignored) {
        }
        dirty = true;
        if (SystemClock.elapsedRealtime() - lastSave > 20000L) {
            flush();
        }
    }

    synchronized long usedToday(String key) {
        JSONObject d = usage.optJSONObject(day());
        return d == null ? 0L : d.optLong(key, 0L);
    }

    synchronized Map<String, Long> todayMap() {
        Map<String, Long> out = new HashMap<>();
        JSONObject d = usage.optJSONObject(day());
        if (d != null) {
            for (String k : keysOf(d)) {
                out.put(k, d.optLong(k, 0L));
            }
        }
        return out;
    }

    /** Ključevi poslednjih n dana koji imaju podatke (najstariji prvi); čuva se najviše 14 dana. */
    synchronized List<String> recentDays(int n) {
        List<String> days = keysOf(usage);
        Collections.sort(days);
        if (days.size() > n) {
            days = days.subList(days.size() - n, days.size());
        }
        return days;
    }

    synchronized Map<String, Long> dayMap(String dayKey) {
        Map<String, Long> out = new HashMap<>();
        JSONObject d = usage.optJSONObject(dayKey);
        if (d != null) {
            for (String k : keysOf(d)) {
                out.put(k, d.optLong(k, 0L));
            }
        }
        return out;
    }

    /** Dan kao "pet 3.10." za prikaz u statistici. */
    static String dayLabel(String dayKey) {
        try {
            Date d = new SimpleDateFormat("yyyyMMdd", Locale.US).parse(dayKey);
            return new SimpleDateFormat("EEE d.M.",
                    new Locale.Builder().setLanguage("sr").setScript("Latn").build()).format(d);
        } catch (Exception e) {
            return dayKey;
        }
    }

    synchronized void flush() {
        if (!dirty) {
            return;
        }
        sp.edit().putString("usage", usage.toString()).apply();
        dirty = false;
        lastSave = SystemClock.elapsedRealtime();
    }
}
