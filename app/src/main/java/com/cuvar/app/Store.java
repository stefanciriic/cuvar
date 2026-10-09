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
import java.security.SecureRandom;
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
import java.util.TimeZone;

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
    // Pravila koja servis zaista primenjuje. Pooštravanje važi odmah, a popuštanje tek od sledećeg dana:
    // ovde ostaje strožija verzija dok se dan ne promeni (vidi enforce()).
    private JSONObject eApps;
    private JSONObject eSites;
    private final List<DailySchedule.Rule> eSchedules = new ArrayList<>();
    private String eDay;
    private boolean dirty;
    private long lastSave;
    private int fails;
    private long blockedUntil;
    private final int boot;          // redni broj paljenja telefona, -1 ako nije poznat
    private final JSONObject unlocks; // "app:paket" / "site:domen" -> kada je otključano PIN-om
    private final JSONObject emergency; // hitno otključavanje: koliko je danas iskorišćeno i šta je otključano
    private boolean stampMoved;        // elapsed() je posle restarta pomerio žig, treba ga sačuvati
    private final android.content.ContentResolver resolver;
    private long clockOff;             // pouzdano vreme = vreme od paljenja + clockOff
    private long clockSaved;           // kada je pouzdano vreme poslednji put sačuvano (vreme od paljenja)
    private TimeZone zone;             // vremenska zona zapamćena u ovom paljenju telefona

    private Store(Context c) {
        sp = c.getSharedPreferences("cuvar", Context.MODE_PRIVATE);
        resolver = c.getContentResolver();
        boot = bootCount(c);
        startClock();
        unlocks = parse(sp.getString("unlocks", "{}"));
        emergency = parse(sp.getString("emergency", "{}"));
        apps = parse(sp.getString("apps", "{}"));
        sites = parse(sp.getString("sites", "{}"));
        usage = parse(sp.getString("usage", "{}"));
        migrateSchedule();
        loadSchedules();
        workEveryDay();
        prune();
        loadEnforced();
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

    /** Kao tryPin, ali za dnevnu šifru, koja važi samo od 17:00 do ponoći; pogrešni pokušaji se broje zajedno. */
    synchronized String tryCode(String code) {
        long now = SystemClock.elapsedRealtime();
        if (now < blockedUntil) {
            return "Previše pokušaja. Sačekaj " + ((blockedUntil - now) / 1000 + 1) + " s";
        }
        if (!dailyCodeVisible()) {
            return "Dnevna šifra važi od " + DailyCode.CHANGE_HOUR + ":00 do ponoći";
        }
        if (dailyCode().equals(code)) {
            fails = 0;
            return null;
        }
        fails++;
        if (fails >= 5) {
            fails = 0;
            blockedUntil = now + 30000L;
            return "Previše pokušaja. Sačekaj 30 s";
        }
        return "Pogrešna dnevna šifra";
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

    /**
     * Koliko je još ostalo do kraja pauze u kojoj se ne može otključati (0 ako pauze nema).
     * Pauza važi za sve: dok traje otključavanje ili pauza bilo čega, ništa drugo se ne otključava PIN-om.
     */
    synchronized long cooldownLeft(String key) {
        long left = 0;
        for (String k : keysOf(unlocks)) {
            long since = sinceUnlock(k);
            if (since >= 0 && (!k.equals(key) || since >= UNLOCK_USE_MS)) {
                left = Math.max(left, UNLOCK_USE_MS + UNLOCK_COOLDOWN_MS - since);
            }
        }
        return left;
    }

    /** Koliko još traju otključavanje i pauza bilo čega; dotle se postojeća pravila ne mogu menjati. */
    synchronized long unlockBusyLeft() {
        long left = 0;
        for (String k : keysOf(unlocks)) {
            long since = sinceUnlock(k);
            if (since >= 0) {
                left = Math.max(left, UNLOCK_USE_MS + UNLOCK_COOLDOWN_MS - since);
            }
        }
        return left;
    }

    /** Najduže preostalo vreme korišćenja posle otključavanja bilo čega (0 ako ništa nije otključano). */
    synchronized long unlockUseLeft() {
        long left = 0;
        for (String k : keysOf(unlocks)) {
            left = Math.max(left, unlockLeft(k));
        }
        return left;
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

    // ---------- Hitno otključavanje ----------

    /** Koliko traje hitno otključavanje. Jedini izuzetak od pauze posle otključavanja. */
    static final long EMERGENCY_MS = 10 * 60000L;
    /** Koliko puta dnevno sme hitno otključavanje; brojač se vraća u ponoć. */
    static final int EMERGENCY_PER_DAY = 1;

    /** Da li danas još ima hitnog otključavanja. */
    synchronized boolean emergencyAvailable() {
        return emergency().optInt("used", 0) < EMERGENCY_PER_DAY;
    }

    /** Koliko je još ostalo od hitnog otključavanja ove stavke (0 ako ga nema). */
    synchronized long emergencyLeft(String key) {
        JSONObject o = emergency();
        JSONObject at = o.optJSONObject("at");
        if (at == null || !key.equals(o.optString("key"))) {
            return 0;
        }
        long since = elapsed(at, EMERGENCY_MS);
        if (since >= EMERGENCY_MS) {
            o.remove("at");
            o.remove("key");
            saveEmergency();
            return 0;
        }
        if (stampMoved) {
            saveEmergency();
        }
        return EMERGENCY_MS - since;
    }

    /** Troši jedno hitno otključavanje za stavku; vraća false ako ga danas više nema. */
    synchronized boolean startEmergency(String key) {
        if (!emergencyAvailable()) {
            return false;
        }
        JSONObject o = emergency();
        try {
            if (!o.has("day")) {
                // Pamti se koliko je ostalo do ponoći, pa pomeranje sata ne donosi novi dan ranije.
                Calendar midnight = Calendar.getInstance();
                midnight.add(Calendar.DAY_OF_MONTH, 1);
                midnight.set(Calendar.HOUR_OF_DAY, 0);
                midnight.set(Calendar.MINUTE, 0);
                midnight.set(Calendar.SECOND, 0);
                midnight.set(Calendar.MILLISECOND, 0);
                o.put("day", stamp());
                o.put("toMidnight", midnight.getTimeInMillis() - System.currentTimeMillis());
            }
            o.put("used", o.optInt("used", 0) + 1);
            o.put("key", key);
            o.put("at", stamp());
        } catch (JSONException e) {
            return false;
        }
        saveEmergency();
        return true;
    }

    /** Zapis o hitnom otključavanju; posle ponoći brojač kreće ispočetka, a započeto otključavanje traje. */
    private JSONObject emergency() {
        JSONObject day = emergency.optJSONObject("day");
        if (day != null) {
            long since = elapsed(day, 0L);
            if (since >= emergency.optLong("toMidnight", 0L)) {
                emergency.remove("day");
                emergency.remove("toMidnight");
                emergency.remove("used");
                saveEmergency();
            } else if (stampMoved) {
                saveEmergency();
            }
        }
        return emergency;
    }

    private void saveEmergency() {
        sp.edit().putString("emergency", emergency.toString()).apply();
    }

    /** Vremenski žig koji se meri isto kao otključavanje: od paljenja telefona, a posle restarta po satu. */
    private JSONObject stamp() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("wall", System.currentTimeMillis());
        o.put("el", SystemClock.elapsedRealtime());
        o.put("boot", boot);
        return o;
    }

    /**
     * Koliko je prošlo od žiga; posle restarta žig se pomera da opet meri od paljenja.
     * Ako je sat u međuvremenu vraćen unazad, uzima se ifBack.
     */
    private long elapsed(JSONObject t, long ifBack) {
        stampMoved = false;
        long since;
        if (boot != -1 && t.optInt("boot", -2) == boot) {
            since = SystemClock.elapsedRealtime() - t.optLong("el", 0L);
        } else {
            since = System.currentTimeMillis() - t.optLong("wall", 0L);
            if (since < 0) {
                since = ifBack;
            }
            try {
                t.put("wall", System.currentTimeMillis() - since);
                t.put("el", SystemClock.elapsedRealtime() - since);
                t.put("boot", boot);
                stampMoved = true;
            } catch (JSONException ignored) {
            }
        }
        return Math.max(0L, since);
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

    /** Aplikacije kojima je danas potrošen dnevni limit. */
    synchronized List<String> appsOverLimit() {
        List<String> out = new ArrayList<>();
        for (String pkg : keysOf(enforcedApps())) {
            int limit = appLimitNow(pkg);
            if (limit > 0 && usedToday(pkg) >= limit * 60000L) out.add(pkg);
        }
        return out;
    }

    /** Sajtovi sa liste kojima je danas potrošen dnevni limit (bez onih koji su uvek blokirani). */
    synchronized List<String> sitesOverLimit() {
        List<String> out = new ArrayList<>();
        for (String d : keysOf(enforcedSites())) {
            int limit = siteLimitNow(d);
            if (limit > 0 && usedToday("site:" + d) >= limit * 60000L) out.add(d);
        }
        return out;
    }

    synchronized boolean hasAppRule(String pkg) {
        return apps.has(pkg);
    }

    /** Aplikacije sa PIN-om ili dnevnim limitom. */
    synchronized List<String> appList() {
        return keysOf(apps);
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
        enforce();
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
        parseSchedules(sp.getString("schedules", "[]"), schedules);
    }

    private static void parseSchedules(String json, List<DailySchedule.Rule> schedules) {
        schedules.clear();
        JSONArray arr;
        try {
            arr = new JSONArray(json);
        } catch (JSONException e) {
            arr = new JSONArray();
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            DailySchedule.Rule r = new DailySchedule.Rule(o.optString("id", newScheduleId()),
                    o.optString("name", "Režim " + (i + 1)), o.optBoolean("enabled", false),
                    o.optInt("start", 21 * 60), o.optInt("end", 9 * 60));
            r.days = o.optInt("days", DailySchedule.ALL_DAYS);
            r.code = o.optBoolean("code", false);
            JSONArray a = o.optJSONArray("apps");
            for (int k = 0; a != null && k < a.length(); k++) r.apps.add(a.optString(k));
            JSONArray s = o.optJSONArray("sites");
            for (int k = 0; s != null && k < s.length(); k++) r.sites.add(s.optString(k));
            schedules.add(r);
        }
    }

    private String schedulesJson() {
        return schedulesJson(schedules);
    }

    private static String schedulesJson(List<DailySchedule.Rule> schedules) {
        JSONArray arr = new JSONArray();
        for (DailySchedule.Rule r : schedules) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("name", r.name);
                o.put("enabled", r.enabled);
                o.put("start", r.start);
                o.put("end", r.end);
                o.put("days", r.days);
                o.put("code", r.code);
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
        enforce();
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
        c.days = r.days;
        c.code = r.code;
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

    /** Gotov noćni režim: uključen, bez otključavanja, aplikacije bira korisnik. */
    static final String NIGHT_NAME = "Noćno zaključavanje";

    synchronized DailySchedule.Rule addNightSchedule() {
        DailySchedule.Rule r = new DailySchedule.Rule(newScheduleId(), NIGHT_NAME, true, 22 * 60 + 30, 6 * 60);
        schedules.add(r);
        saveSchedules();
        return copy(r);
    }

    synchronized boolean hasNightSchedule() {
        for (DailySchedule.Rule r : schedules) if (r.name.equals(NIGHT_NAME)) return true;
        return false;
    }

    /** Gotovo radno vreme: svakog dana 09:00–17:00 bez otključavanja, posle toga dnevnom šifrom. */
    static final String WORK_NAME = "Radno vreme";

    synchronized DailySchedule.Rule addWorkSchedule() {
        DailySchedule.Rule r = new DailySchedule.Rule(newScheduleId(), WORK_NAME, true, 9 * 60, 17 * 60);
        r.code = true;
        schedules.add(r);
        saveSchedules();
        return copy(r);
    }

    /** Jednom: postojeće radno vreme sa samo radnim danima važi i vikendom (dodaje dane, ne slabi režim). */
    private void workEveryDay() {
        if (sp.getBoolean("workEveryDay", false)) return;
        boolean changed = false;
        for (DailySchedule.Rule r : schedules) {
            if (r.name.equals(WORK_NAME) && r.days == DailySchedule.WORK_DAYS) {
                r.days = DailySchedule.ALL_DAYS;
                changed = true;
            }
        }
        if (changed) saveSchedules();
        sp.edit().putBoolean("workEveryDay", true).apply();
    }

    synchronized boolean hasWorkSchedule() {
        for (DailySchedule.Rule r : schedules) if (r.name.equals(WORK_NAME)) return true;
        return false;
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

    /**
     * Dok je režim aktivan ne može da se oslabi: isključi, obriše, promeni mu se period, dani ili šifra,
     * niti da se iz njega uklone aplikacije i sajtovi. Dodavanje i promena naziva su dozvoljeni.
     */
    synchronized boolean scheduleActive(String id) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return false;
        Calendar now = calendarNow();
        return r.active(minuteOf(now), dayOf(now));
    }

    /** Briše režim; vraća false ako je upravo aktivan. */
    synchronized boolean removeSchedule(String id) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return true;
        if (scheduleActive(id)) return false;
        if (schedules.remove(r)) saveSchedules();
        return true;
    }

    /** Vraća false (i ništa ne menja) ako bi izmena oslabila aktivan režim. */
    synchronized boolean setSchedule(String id, String name, boolean enabled, int start, int end) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return true;
        if (scheduleActive(id) && (!enabled || start != r.start || end != r.end)) return false;
        r.name = name;
        r.enabled = enabled;
        r.start = start;
        r.end = end;
        saveSchedules();
        return true;
    }

    synchronized boolean setScheduleDays(String id, int days) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return true;
        if (scheduleActive(id) && days != r.days) return false;
        r.days = days;
        saveSchedules();
        return true;
    }

    synchronized boolean setScheduleCode(String id, boolean code) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return true;
        if (scheduleActive(id) && !code && r.code) return false;
        r.code = code;
        saveSchedules();
        return true;
    }

    synchronized boolean setScheduleApp(String id, String pkg, boolean selected) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return true;
        if (!selected && r.apps.contains(pkg) && scheduleActive(id)) return false;
        if (selected) r.apps.add(pkg); else r.apps.remove(pkg);
        saveSchedules();
        return true;
    }

    synchronized boolean setScheduleSite(String id, String domain, boolean selected) {
        DailySchedule.Rule r = rule(id);
        if (r == null) return true;
        if (!selected && r.sites.contains(domain) && scheduleActive(id)) return false;
        if (selected) r.sites.add(domain); else r.sites.remove(domain);
        saveSchedules();
        return true;
    }

    private static int minuteOf(Calendar c) {
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** 0 = ponedeljak … 6 = nedelja. */
    private static int dayOf(Calendar c) {
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    /** Režim koji trenutno blokira aplikaciju, ili null. */
    synchronized DailySchedule.Rule scheduleBlockingApp(String pkg) {
        Calendar now = calendarNow();
        DailySchedule.Rule r = DailySchedule.blockingApp(enforced(), pkg, minuteOf(now), dayOf(now));
        return r == null ? null : copy(r);
    }

    /** Režim koji trenutno blokira host (i poddomene), ili null. */
    synchronized DailySchedule.Rule scheduleBlockingSite(String host) {
        Calendar now = calendarNow();
        DailySchedule.Rule r = DailySchedule.blockingSite(enforced(), host, minuteOf(now), dayOf(now));
        return r == null ? null : copy(r);
    }

    /** Režim sa dnevnom šifrom koji sadrži aplikaciju, ili null. */
    synchronized DailySchedule.Rule codeRuleForApp(String pkg) {
        DailySchedule.Rule r = DailySchedule.codeApp(enforced(), pkg);
        return r == null ? null : copy(r);
    }

    /** Režim sa dnevnom šifrom koji sadrži host (i poddomene), ili null. */
    synchronized DailySchedule.Rule codeRuleForSite(String host) {
        DailySchedule.Rule r = DailySchedule.codeSite(enforced(), host);
        return r == null ? null : copy(r);
    }

    /** Režimi koji upravo traju (bez obzira da li imaju aplikacije ili sajtove). */
    synchronized List<DailySchedule.Rule> activeSchedules() {
        Calendar now = calendarNow();
        List<DailySchedule.Rule> out = new ArrayList<>();
        for (DailySchedule.Rule r : enforced()) {
            if (r.active(minuteOf(now), dayOf(now))) out.add(copy(r));
        }
        return out;
    }

    /** Da li postoji uključen režim sa dnevnom šifrom. */
    synchronized boolean usesDailyCode() {
        for (DailySchedule.Rule r : schedules) if (r.enabled && r.code) return true;
        return false;
    }

    /** Uključen režim sa dnevnom šifrom koji je upravo aktivan (tada se šifra ne prikazuje), ili null. */
    synchronized DailySchedule.Rule activeCodeRule() {
        Calendar now = calendarNow();
        for (DailySchedule.Rule r : enforced()) {
            if (r.code && r.active(minuteOf(now), dayOf(now))) return copy(r);
        }
        return null;
    }

    // ---------- Dnevna šifra ----------

    /** Šifra se prikazuje samo od 17:00 do ponoći, pa je ujutru i tokom dana nema na ekranu. */
    synchronized boolean dailyCodeVisible() {
        return calendarNow().get(Calendar.HOUR_OF_DAY) >= DailyCode.CHANGE_HOUR;
    }

    /** Šifra koja važi sada (od 17:00 do 17:00 sledećeg dana, po pouzdanom vremenu). */
    synchronized String dailyCode() {
        return DailyCode.code(codeKey(), DailyCode.dayKey(now(), zone()));
    }

    /** Tajni ključ za dnevnu šifru, nasumičan i samo na ovom telefonu (bez rezervne kopije). */
    private byte[] codeKey() {
        String hex = sp.getString("codeKey", null);
        if (hex == null || hex.length() != 64) {
            byte[] k = new byte[32];
            new SecureRandom().nextBytes(k);
            StringBuilder sb = new StringBuilder();
            for (byte b : k) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            hex = sb.toString();
            sp.edit().putString("codeKey", hex).apply();
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    // ---------- Pouzdano vreme ----------

    /** Sat telefona u nekom trenutku posle 1.1.2026; manje od toga znači da sat još nije podešen. */
    private static final long SANE_WALL = 1767225600000L;

    /**
     * U jednom paljenju telefona vreme se meri od paljenja, pa pomeranje sata ne skraćuje režim
     * i ne donosi novu šifru ranije. Posle restarta sat se ponovo čita, ali ne može unazad
     * u odnosu na poslednje zapamćeno vreme.
     */
    private void startClock() {
        JSONObject o = parse(sp.getString("clock", "{}"));
        long el = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        boolean sameBoot = o.has("off") && (boot != -1 ? o.optInt("boot", -2) == boot : el >= o.optLong("el", Long.MAX_VALUE));
        if (sameBoot) {
            clockOff = o.optLong("off");
            zone = TimeZone.getTimeZone(o.optString("zone", TimeZone.getDefault().getID()));
        } else {
            long last = o.optLong("last", 0L);
            clockOff = Math.max(wall, last) - el;
            zone = TimeZone.getDefault();
        }
        saveClock();
    }

    private void saveClock() {
        long el = SystemClock.elapsedRealtime();
        try {
            JSONObject o = new JSONObject();
            o.put("off", clockOff);
            o.put("boot", boot);
            o.put("el", el);
            o.put("last", el + clockOff);
            o.put("zone", zone.getID());
            sp.edit().putString("clock", o.toString()).apply();
        } catch (JSONException ignored) {
        }
        clockSaved = el;
    }

    /** Pouzdano trenutno vreme u milisekundama. */
    synchronized long now() {
        long el = SystemClock.elapsedRealtime();
        long wall = System.currentTimeMillis();
        if (el + clockOff < SANE_WALL && wall >= SANE_WALL) {
            clockOff = wall - el; // telefon je upaljen pre nego što je dobio tačno vreme
            saveClock();
        } else if (el - clockSaved > 60000L) {
            saveClock();
        }
        return el + clockOff;
    }

    /**
     * Vremenska zona: ručna promena zone ne pomera režim do restarta telefona.
     * Kad telefon sam bira zonu (putovanje), prati se sistemska.
     */
    private TimeZone zone() {
        TimeZone sys = TimeZone.getDefault();
        if (!sys.getID().equals(zone.getID()) && autoZone()) {
            zone = sys;
            saveClock();
        }
        return zone;
    }

    private boolean autoZone() {
        try {
            return Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    private Calendar calendarNow() {
        Calendar c = Calendar.getInstance(zone());
        c.setTimeInMillis(now());
        return c;
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
        enforce();
    }

    synchronized void removeSite(String domain) {
        sites.remove(domain);
        sp.edit().putString("sites", sites.toString()).apply();
        enforce();
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

    /** Ključ današnjeg dana ("yyyyMMdd") po pouzdanom vremenu, pa pomeranje sata ne vraća dnevne limite. */
    synchronized String day() {
        Calendar c = calendarNow();
        return String.format(Locale.US, "%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
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

    /** Ključevi poslednjih n kalendarskih dana zaključno sa danas (najstariji prvi), i dani bez korišćenja. */
    synchronized List<String> lastDays(int n) {
        Calendar c = calendarNow();
        c.add(Calendar.DAY_OF_MONTH, -(n - 1));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(String.format(Locale.US, "%04d%02d%02d",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)));
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    /** Da li za dan postoji ijedan zapis (pre instalacije Čuvara ih nema). */
    synchronized boolean hasDay(String dayKey) {
        return usage.has(dayKey);
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

    // ---------- Ukupni dnevni limit ----------

    /** Danas ukupno na telefonu: sve aplikacije osim onih u skip (početni ekran, Čuvar, pozivi, poruke). */
    synchronized long phoneToday(Set<String> skip) {
        JSONObject d = usage.optJSONObject(day());
        long total = 0;
        if (d != null) {
            for (String k : keysOf(d)) {
                if (k.startsWith("site:") || k.startsWith("web:") || skip.contains(k)) continue;
                total += d.optLong(k, 0L);
            }
        }
        return total;
    }

    /** Ukupni dnevni limit u minutima koji danas važi (0 = isključen); zakazana promena stupa na snagu u ponoć. */
    synchronized int dayLimit() {
        int cur = sp.getInt("dayLimit", 0);
        String from = sp.getString("dayLimitFrom", null);
        if (from != null && sp.getInt("dayLimitNext", -1) > 0) {
            // Zakazano povećanje iz ranije verzije više ne važi: povećava se samo jednom dnevno i malo.
            sp.edit().remove("dayLimitNext").remove("dayLimitFrom").apply();
            from = null;
        }
        String today = day();
        int eff = DayLimit.effective(cur, sp.getInt("dayLimitNext", -1), from, today);
        if (from != null && today.compareTo(from) >= 0) {
            sp.edit().putInt("dayLimit", eff).remove("dayLimitNext").remove("dayLimitFrom").apply();
        }
        return eff;
    }

    /** Vrednost zakazana od sutra, ili -1 ako je nema. */
    synchronized int dayLimitNext() {
        dayLimit();
        return sp.contains("dayLimitFrom") ? sp.getInt("dayLimitNext", -1) : -1;
    }

    /** Strožiji limit (ili uključivanje) važi odmah i poništava zakazano isključivanje. Vraća false ako bi bio blaži. */
    synchronized boolean lowerDayLimit(int min) {
        int cur = dayLimit();
        if (!DayLimit.appliesNow(cur, min)) return false;
        sp.edit().putInt("dayLimit", min).remove("dayLimitNext").remove("dayLimitFrom").apply();
        return true;
    }

    /** Da li danas još može da se poveća limit (jednom dnevno, samo kad je uključen). */
    synchronized boolean canRaiseDayLimit() {
        return dayLimit() > 0 && !day().equals(sp.getString("dayLimitRaised", null));
    }

    /** Povećava limit za najviše dozvoljeno; vraća koliko je minuta dodato (0 ako danas više ne može). */
    synchronized int raiseDayLimit() {
        if (!canRaiseDayLimit()) return 0;
        int cur = dayLimit();
        int add = DayLimit.maxRaise(cur);
        sp.edit().putInt("dayLimit", cur + add).putString("dayLimitRaised", day()).apply();
        return add;
    }

    /** Isključivanje važi tek od sutra. */
    synchronized void turnOffDayLimitTomorrow() {
        if (dayLimit() <= 0) return;
        Calendar c = calendarNow();
        c.add(Calendar.DAY_OF_MONTH, 1);
        String tomorrow = String.format(Locale.US, "%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        sp.edit().putInt("dayLimitNext", 0).putString("dayLimitFrom", tomorrow).apply();
    }

    /** Poništava zakazano isključivanje. */
    synchronized void keepDayLimit() {
        sp.edit().remove("dayLimitNext").remove("dayLimitFrom").apply();
    }

    /** Da li je danas već stiglo upozorenje pred kraj limita (pa se vraća true samo prvi put). */
    synchronized boolean firstDayLimitWarning() {
        String d = day();
        if (d.equals(sp.getString("dayLimitWarned", null))) return false;
        sp.edit().putString("dayLimitWarned", d).apply();
        return true;
    }

    /** Da li je aplikacija u nekom pravilu: zaključana, sa limitom ili u uključenom režimu. */
    synchronized boolean appGuarded(String pkg) {
        if (enforcedApps().has(pkg)) return true;
        for (DailySchedule.Rule r : enforced()) if (r.enabled && r.apps.contains(pkg)) return true;
        return false;
    }

    /** Domen sa liste ili iz uključenog režima koji pokriva host, ili null. */
    synchronized String siteGuarded(String host) {
        String best = matchSiteNow(host);
        for (DailySchedule.Rule r : enforced()) {
            if (!r.enabled) continue;
            String d = DailySchedule.matchDomain(host, r.sites);
            if (d != null && (best == null || d.length() > best.length())) best = d;
        }
        return best;
    }

    // ---------- Pravila koja važe sada (popuštanje od sutra) ----------

    private void loadEnforced() {
        eDay = sp.getString("eDay", null);
        if (eDay == null) {
            // Prvo pokretanje ove verzije: važi ono što je podešeno.
            copyToEnforced();
            return;
        }
        eApps = parse(sp.getString("eApps", "{}"));
        eSites = parse(sp.getString("eSites", "{}"));
        parseSchedules(sp.getString("eSchedules", "[]"), eSchedules);
        enforce();
    }

    private void copyToEnforced() {
        eApps = parse(apps.toString());
        eSites = parse(sites.toString());
        eSchedules.clear();
        for (DailySchedule.Rule r : schedules) eSchedules.add(copy(r));
        eDay = day();
        saveEnforced();
    }

    private void saveEnforced() {
        sp.edit().putString("eApps", eApps.toString()).putString("eSites", eSites.toString())
                .putString("eSchedules", schedulesJson(eSchedules)).putString("eDay", eDay).apply();
    }

    /** Novog dana važi tačno ono što je podešeno, i sva odložena popuštanja stupaju na snagu. */
    private void roll() {
        if (eApps == null) return;
        if (!day().equals(eDay)) copyToEnforced();
    }

    /** Posle svake izmene: ono što važi sada postaje strožije od starog i novog podešavanja. */
    private void enforce() {
        if (eApps == null) return;
        if (!day().equals(eDay)) {
            copyToEnforced();
            return;
        }
        JSONObject na = new JSONObject();
        Set<String> keys = new HashSet<>(keysOf(eApps));
        keys.addAll(keysOf(apps));
        for (String pkg : keys) {
            JSONObject e = eApps.optJSONObject(pkg), d = apps.optJSONObject(pkg);
            boolean lock = (e != null && e.optBoolean("lock", false)) || (d != null && d.optBoolean("lock", false));
            int limit = minLimit(e == null ? 0 : e.optInt("limit", 0), d == null ? 0 : d.optInt("limit", 0));
            if (!lock && limit <= 0) continue;
            try {
                na.put(pkg, new JSONObject().put("lock", lock).put("limit", limit));
            } catch (JSONException ignored) {
            }
        }
        JSONObject ns = new JSONObject();
        keys = new HashSet<>(keysOf(eSites));
        keys.addAll(keysOf(sites));
        for (String dom : keys) {
            JSONObject e = eSites.optJSONObject(dom), d = sites.optJSONObject(dom);
            int a = e == null ? -1 : e.optInt("limit", 0), b = d == null ? -1 : d.optInt("limit", 0);
            int limit = a < 0 ? b : b < 0 ? a : (a == 0 || b == 0) ? 0 : Math.min(a, b);
            try {
                ns.put(dom, new JSONObject().put("limit", limit));
            } catch (JSONException ignored) {
            }
        }
        List<DailySchedule.Rule> nr = new ArrayList<>();
        for (DailySchedule.Rule d : schedules) {
            DailySchedule.Rule e = find(eSchedules, d.id);
            nr.add(e == null ? copy(d) : stricter(e, d));
        }
        for (DailySchedule.Rule e : eSchedules) if (rule(e.id) == null) nr.add(copy(e)); // obrisan od sutra
        eApps = na;
        eSites = ns;
        eSchedules.clear();
        eSchedules.addAll(nr);
        saveEnforced();
    }

    /** Manji od dva dnevna limita; 0 znači bez limita. */
    private static int minLimit(int a, int b) {
        if (a <= 0) return Math.max(0, b);
        if (b <= 0) return a;
        return Math.min(a, b);
    }

    private static DailySchedule.Rule find(List<DailySchedule.Rule> list, String id) {
        for (DailySchedule.Rule r : list) if (r.id.equals(id)) return r;
        return null;
    }

    /** Spoj važećeg i novog podešavanja režima koji ništa ne popušta. */
    private static DailySchedule.Rule stricter(DailySchedule.Rule e, DailySchedule.Rule d) {
        DailySchedule.Rule m = copy(e);
        m.name = d.name;
        if (!e.enabled || covers(d, e)) {
            m.start = d.start;
            m.end = d.end;
        }
        m.enabled = e.enabled || d.enabled;
        m.code = e.code || d.code;
        m.days = e.days | d.days;
        m.apps.addAll(d.apps);
        m.sites.addAll(d.sites);
        return m;
    }

    /** Da li period a obuhvata ceo period b. */
    private static boolean covers(DailySchedule.Rule a, DailySchedule.Rule b) {
        for (int m = 0; m < 24 * 60; m++) {
            if (DailySchedule.contains(b.start, b.end, m) && !DailySchedule.contains(a.start, a.end, m)) return false;
        }
        return true;
    }

    private List<DailySchedule.Rule> enforced() {
        roll();
        return eApps == null ? schedules : eSchedules;
    }

    private JSONObject enforcedApps() {
        roll();
        return eApps == null ? apps : eApps;
    }

    private JSONObject enforcedSites() {
        roll();
        return eSites == null ? sites : eSites;
    }

    synchronized boolean appLockNow(String pkg) {
        JSONObject o = enforcedApps().optJSONObject(pkg);
        return o != null && o.optBoolean("lock", false);
    }

    synchronized int appLimitNow(String pkg) {
        JSONObject o = enforcedApps().optJSONObject(pkg);
        return o == null ? 0 : o.optInt("limit", 0);
    }

    synchronized int siteLimitNow(String domain) {
        JSONObject o = enforcedSites().optJSONObject(domain);
        return o == null ? -1 : o.optInt("limit", 0);
    }

    synchronized String matchSiteNow(String host) {
        String best = null;
        for (String d : keysOf(enforcedSites())) {
            if ((host.equals(d) || host.endsWith("." + d)) && (best == null || d.length() > best.length())) best = d;
        }
        return best;
    }

    /** Popuštanja koja čekaju sutra, opisana rečima; prazno ako ih nema. */
    synchronized List<String> pendingChanges(android.content.pm.PackageManager pm) {
        roll();
        List<String> out = new ArrayList<>();
        if (eApps == null) return out;
        for (String pkg : keysOf(eApps)) {
            String name = pkg;
            try { name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) { }
            boolean el = appLockNow(pkg), dl = appLock(pkg);
            int ei = appLimitNow(pkg), di = appLimit(pkg);
            if (el && !dl) out.add(name + ": bez zaključavanja");
            if (ei != di) out.add(name + ": " + (di <= 0 ? "bez dnevnog limita" : "limit " + di + " min umesto " + ei));
        }
        for (String dom : keysOf(eSites)) {
            int ei = siteLimitNow(dom), di = siteLimit(dom);
            if (ei == di) continue;
            out.add(dom + ": " + (di < 0 ? "skida se sa liste" : di == 0 ? "uvek blokiran" : "limit " + di + " min"
                    + (ei == 0 ? " umesto stalne blokade" : " umesto " + ei)));
        }
        for (DailySchedule.Rule e : eSchedules) {
            DailySchedule.Rule d = rule(e.id);
            if (d == null) {
                out.add("Režim „" + e.name + "“ se briše");
                continue;
            }
            if (e.enabled && !d.enabled) out.add("Režim „" + d.name + "“ se isključuje");
            if (d.enabled && (e.start != d.start || e.end != d.end)) out.add("Režim „" + d.name + "“: " + d.label() + " umesto " + e.label());
            if (e.days != d.days) out.add("Režim „" + d.name + "“: " + d.daysLabel() + " umesto " + e.daysLabel());
            if (e.code && !d.code) out.add("Režim „" + d.name + "“ bez dnevne šifre van perioda");
            int apps = 0, sites = 0;
            for (String a : e.apps) if (!d.apps.contains(a)) apps++;
            for (String x : e.sites) if (!d.sites.contains(x)) sites++;
            if (apps + sites > 0) out.add("Režim „" + d.name + "“: uklanja se " + (apps > 0 ? Ui.count(apps, "aplikacija", "aplikacije", "aplikacija") : "")
                    + (apps > 0 && sites > 0 ? " i " : "") + (sites > 0 ? Ui.count(sites, "sajt", "sajta", "sajtova") : ""));
        }
        return out;
    }

    /** Odustaje od popuštanja koja čekaju sutra: podešavanje se vraća na ono što sada važi. */
    synchronized void cancelPending() {
        roll();
        if (eApps == null) return;
        try {
            JSONObject a = new JSONObject(eApps.toString());
            for (String k : keysOf(apps)) apps.remove(k);
            for (String k : keysOf(a)) apps.put(k, a.get(k));
            JSONObject s = new JSONObject(eSites.toString());
            for (String k : keysOf(sites)) sites.remove(k);
            for (String k : keysOf(s)) sites.put(k, s.get(k));
        } catch (JSONException ignored) {
        }
        schedules.clear();
        for (DailySchedule.Rule r : eSchedules) schedules.add(copy(r));
        sp.edit().putString("apps", apps.toString()).putString("sites", sites.toString())
                .putString("schedules", schedulesJson()).apply();
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
