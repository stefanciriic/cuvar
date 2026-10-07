package com.cuvar.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final List<DailySchedule.Rule> schedules = new ArrayList<>();
    private boolean dirty;
    private long lastSave;
    private int fails;
    private long blockedUntil;
    private final int boot;          // redni broj paljenja telefona, -1 ako nije poznat
    private final JSONObject unlocks; // "app:paket" / "site:domen" -> kada je otključano PIN-om

    private Store(Context c) {
        sp = c.getSharedPreferences("cuvar", Context.MODE_PRIVATE);
        boot = bootCount(c);
        unlocks = parse(sp.getString("unlocks", "{}"));
        apps = parse(sp.getString("apps", "{}"));
        sites = parse(sp.getString("sites", "{}"));
        usage = parse(sp.getString("usage", "{}"));
        migrateSchedule();
        loadSchedules();
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

    // ---------- Otključavanje sa pauzom ----------

    /** Koliko se dugo sme koristiti posle otključavanja PIN-om. */
    static final long UNLOCK_USE_MS = 5 * 60000L;
    /** Koliko posle toga nema nikakvog otključavanja, ni PIN-om. */
    static final long UNLOCK_COOLDOWN_MS = 60 * 60000L;

    private static int bootCount(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Pamti da je stavka upravo otključana; ponovno otključavanje dok traje pauza ne pomera vreme. */
    synchronized void startUnlock(String key) {
        if (sinceUnlock(key) >= 0) {
            return;
        }
        saveUnlock(key, 0);
    }

    /** Koliko je još ostalo od 5 minuta korišćenja (0 ako nije otključano). */
    synchronized long unlockLeft(String key) {
        long since = sinceUnlock(key);
        return since >= 0 && since < UNLOCK_USE_MS ? UNLOCK_USE_MS - since : 0;
    }

    /** Koliko je još ostalo do kraja pauze u kojoj se ne može otključati (0 ako pauze nema). */
    synchronized long cooldownLeft(String key) {
        long since = sinceUnlock(key);
        return since >= UNLOCK_USE_MS ? UNLOCK_USE_MS + UNLOCK_COOLDOWN_MS - since : 0;
    }

    /** Koliko još traju otključavanje i pauza zajedno; dotle se pravila za stavku ne mogu menjati. */
    synchronized long unlockBusyLeft(String key) {
        long since = sinceUnlock(key);
        return since < 0 ? 0 : UNLOCK_USE_MS + UNLOCK_COOLDOWN_MS - since;
    }

    /**
     * Koliko je prošlo od otključavanja, ili -1 ako je i pauza završena.
     * U istom paljenju telefona meri se vremenom od paljenja, pa pomeranje sata ne pomaže.
     * Posle restarta jedino ostaje sat telefona; vraćanje sata unazad ne daje novih 5 minuta.
     */
    private long sinceUnlock(String key) {
        JSONObject o = unlocks.optJSONObject(key);
        if (o == null) {
            return -1;
        }
        long since;
        if (boot != -1 && o.optInt("boot", -2) == boot) {
            since = SystemClock.elapsedRealtime() - o.optLong("el", 0L);
        } else {
            since = System.currentTimeMillis() - o.optLong("wall", 0L);
            if (since < 0) {
                since = UNLOCK_USE_MS; // sat je vraćen unazad: kreće cela pauza, bez novih 5 minuta
            }
            saveUnlock(key, since); // od sada opet meri vremenom od paljenja
        }
        since = Math.max(0L, since);
        if (since >= UNLOCK_USE_MS + UNLOCK_COOLDOWN_MS) {
            unlocks.remove(key);
            sp.edit().putString("unlocks", unlocks.toString()).apply();
            return -1;
        }
        return since;
    }

    private void saveUnlock(String key, long since) {
        try {
            JSONObject o = new JSONObject();
            o.put("wall", System.currentTimeMillis() - since);
            o.put("el", SystemClock.elapsedRealtime() - since);
            o.put("boot", boot);
            unlocks.put(key, o);
        } catch (JSONException ignored) {
        }
        sp.edit().putString("unlocks", unlocks.toString()).apply();
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
        if (!lock && limitMin <= 0) {
            apps.remove(pkg);
        } else {
            try {
                JSONObject o = new JSONObject();
                o.put("lock", lock);
                o.put("limit", Math.max(0, limitMin));
                apps.put(pkg, o);
            } catch (JSONException ignored) {
            }
        }
        sp.edit().putString("apps", apps.toString()).apply();
    }

    // ---------- Vremenski režimi ----------

    /** Prebacuje stari jedini režim (pre više režima) u prvi režim liste, bez gubitka izbora. */
    private void migrateSchedule() {
        if (sp.contains("schedules")) {
            return;
        }
        DailySchedule.Rule first = new DailySchedule.Rule(newScheduleId(), "Režim 1",
                sp.getBoolean("scheduleEnabled", false),
                sp.getInt("scheduleStart", 21 * 60), sp.getInt("scheduleEnd", 9 * 60));
        for (String pkg : keysOf(apps)) {
            JSONObject o = apps.optJSONObject(pkg);
            if (o == null || !o.has("scheduled")) continue;
            if (o.optBoolean("scheduled", false)) first.apps.add(pkg);
            o.remove("scheduled");
            if (!o.optBoolean("lock", false) && o.optInt("limit", 0) <= 0) apps.remove(pkg);
        }
        first.sites.addAll(sp.getStringSet("scheduledSites", Collections.emptySet()));
        boolean hadOld = sp.contains("scheduleEnabled") || !first.apps.isEmpty() || !first.sites.isEmpty();
        if (hadOld) schedules.add(first);
        sp.edit().putString("apps", apps.toString()).putString("schedules", schedulesJson())
                .remove("scheduleEnabled").remove("scheduleStart").remove("scheduleEnd")
                .remove("scheduledSites").apply();
    }

    private void loadSchedules() {
        schedules.clear();
        JSONArray arr;
        try {
            arr = new JSONArray(sp.getString("schedules", "[]"));
        } catch (JSONException e) {
            arr = new JSONArray();
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            DailySchedule.Rule r = new DailySchedule.Rule(o.optString("id", newScheduleId()),
                    o.optString("name", "Režim " + (i + 1)), o.optBoolean("enabled", false),
                    o.optInt("start", 21 * 60), o.optInt("end", 9 * 60));
            JSONArray a = o.optJSONArray("apps");
            for (int k = 0; a != null && k < a.length(); k++) r.apps.add(a.optString(k));
            JSONArray s = o.optJSONArray("sites");
            for (int k = 0; s != null && k < s.length(); k++) r.sites.add(s.optString(k));
            schedules.add(r);
        }
    }

    private String schedulesJson() {
        JSONArray arr = new JSONArray();
        for (DailySchedule.Rule r : schedules) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("name", r.name);
                o.put("enabled", r.enabled);
                o.put("start", r.start);
                o.put("end", r.end);
                o.put("apps", new JSONArray(r.apps));
                o.put("sites", new JSONArray(r.sites));
                arr.put(o);
            } catch (JSONException ignored) {
            }
        }
        return arr.toString();
    }

    private void saveSchedules() {
        sp.edit().putString("schedules", schedulesJson()).apply();
    }

    private static String newScheduleId() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
    }

    private DailySchedule.Rule rule(String id) {
        for (DailySchedule.Rule r : schedules) if (r.id.equals(id)) return r;
        return null;
    }

    private static DailySchedule.Rule copy(DailySchedule.Rule r) {
        DailySchedule.Rule c = new DailySchedule.Rule(r.id, r.name, r.enabled, r.start, r.end);
        c.apps.addAll(r.apps);
        c.sites.addAll(r.sites);
        return c;
    }

    /** Kopije režima, da ih ekran i servis ne menjaju mimo Store-a. */
    synchronized List<DailySchedule.Rule> schedules() {
        List<DailySchedule.Rule> out = new ArrayList<>();
        for (DailySchedule.Rule r : schedules) out.add(copy(r));
        return out;
    }

    synchronized DailySchedule.Rule schedule(String id) {
        DailySchedule.Rule r = rule(id);
        return r == null ? null : copy(r);
    }

    synchronized DailySchedule.Rule addSchedule() {
        int n = schedules.size() + 1;
        while (true) {
            boolean taken = false;
            for (DailySchedule.Rule r : schedules) if (r.name.equals("Režim " + n)) taken = true;
            if (!taken) break;
            n++;
        }
        DailySchedule.Rule r = new DailySchedule.Rule(newScheduleId(), "Režim " + n, false, 21 * 60, 9 * 60);
        schedules.add(r);
        saveSchedules();
        return copy(r);
    }

    synchronized void removeSchedule(String id) {
        DailySchedule.Rule r = rule(id);
        if (r != null && schedules.remove(r)) saveSchedules();
    }

    synchronized void setSchedule(String id, String name, boolean enabled, int start, int end) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return;
        r.name = name;
        r.enabled = enabled;
        r.start = start;
        r.end = end;
        saveSchedules();
    }

    synchronized void setScheduleApp(String id, String pkg, boolean selected) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return;
        if (selected) r.apps.add(pkg); else r.apps.remove(pkg);
        saveSchedules();
    }

    synchronized void setScheduleSite(String id, String domain, boolean selected) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return;
        if (selected) r.sites.add(domain); else r.sites.remove(domain);
        saveSchedules();
    }

    private static int minuteNow() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    /** Režim koji trenutno blokira aplikaciju, ili null. */
    synchronized DailySchedule.Rule scheduleBlockingApp(String pkg) {
        DailySchedule.Rule r = DailySchedule.blockingApp(schedules, pkg, minuteNow());
        return r == null ? null : copy(r);
    }

    /** Režim koji trenutno blokira host (i poddomene), ili null. */
    synchronized DailySchedule.Rule scheduleBlockingSite(String host) {
        DailySchedule.Rule r = DailySchedule.blockingSite(schedules, host, minuteNow());
        return r == null ? null : copy(r);
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

    /** Drugi nivo ispod državnog domena, npr. "co" u "bbc.co.uk" ili "org" u "nesto.org.rs". */
    private static final Set<String> SECOND_LEVEL = new HashSet<>(Arrays.asList(
            "co", "com", "org", "net", "edu", "gov", "ac", "in", "or", "ne", "go"));

    /**
     * Glavni domen hosta, da se poddomeni sabiraju zajedno:
     * "m.youtube.com" -> "youtube.com", "news.bbc.co.uk" -> "bbc.co.uk". Null ako ne liči na domen.
     */
    static String mainDomain(String host) {
        if (host == null || host.indexOf('.') < 0 || !host.matches("[\\p{L}\\p{N}.-]+")) {
            return null;
        }
        if (host.matches("[0-9.]+")) {
            return host; // IP adresa
        }
        List<String> parts = new ArrayList<>();
        for (String part : host.split("\\.")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        int n = parts.size();
        if (n < 2) {
            return null;
        }
        int keep = n >= 3 && parts.get(n - 1).length() == 2 && SECOND_LEVEL.contains(parts.get(n - 2)) ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = n - Math.min(keep, n); i < n; i++) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    // ---------- Izmereno vreme ----------

    /** Ključ današnjeg dana ("yyyyMMdd"). */
    static String day() {
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
