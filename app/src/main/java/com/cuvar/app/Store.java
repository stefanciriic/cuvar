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
import java.util.Collection;
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
    private final JSONObject opens;  // dan -> {"app:paket" ili "try:ključ": koliko puta}
    private final JSONObject sessions; // paket -> {u: ms u komadu, l: poslednji put viđena, b: pauza do}
    private final List<DailySchedule.Rule> schedules = new ArrayList<>();
    // Pravila koja servis zaista primenjuje. Pooštravanje važi odmah, a popuštanje tek od sledećeg dana:
    // ovde ostaje strožija verzija dok se dan ne promeni (vidi enforce()).
    private JSONObject eApps;
    private JSONObject eSites;
    private final List<DailySchedule.Rule> eSchedules = new ArrayList<>();
    private final JSONObject links;   // paket -> domen njegovog sajta; "" = razdvojen poznat par (vidi Links)
    private final Set<String> eLinks = new HashSet<>(); // parovi "paket|domen" koji sada važe
    private List<DailySchedule.Rule> expanded;           // važeći režimi sa povezanim stavkama
    private String eDay;
    private boolean eNight;
    private boolean eProtect;
    private boolean dirty;
    private long lastSave;
    private int fails;
    private long blockedUntil;
    private final int boot;          // redni broj paljenja telefona, -1 ako nije poznat
    private final JSONObject unlocks; // "app:paket" / "site:domen" -> kada je otključano PIN-om
    private final JSONObject emergency; // hitno otključavanje: koliko je danas iskorišćeno i šta je otključano
    private final Set<String> focusApps = new HashSet<>();
    private final Set<String> focusSites = new HashSet<>();
    private long focusUntil;
    private boolean stampMoved;        // elapsed() je posle restarta pomerio žig, treba ga sačuvati
    private final android.content.ContentResolver resolver;
    private long clockOff;             // pouzdano vreme = vreme od paljenja + clockOff
    private long clockSaved;           // kada je pouzdano vreme poslednji put sačuvano (vreme od paljenja)
    private long skewSaved;            // koliko je sat telefona bio pomeren pri poslednjem čuvanju
    private TimeZone zone;             // vremenska zona zapamćena u ovom paljenju telefona

    private Store(Context c) {
        sp = c.getSharedPreferences("cuvar", Context.MODE_PRIVATE);
        resolver = c.getContentResolver();
        boot = bootCount(c);
        startClock();
        unlocks = parse(sp.getString("unlocks", "{}"));
        emergency = parse(sp.getString("emergency", "{}"));
        loadFocus();
        loadCodeLockout();
        apps = parse(sp.getString("apps", "{}"));
        sites = parse(sp.getString("sites", "{}"));
        links = parse(sp.getString("links", "{}"));
        usage = parse(sp.getString("usage", "{}"));
        opens = parse(sp.getString("opens", "{}"));
        sessions = parse(sp.getString("sessions", "{}"));
        migrateSchedule();
        loadSchedules();
        workEveryDay();
        prune();
        loadEnforced();
        if (sp.contains("pin")) sp.edit().remove("pin").apply(); // PIN više ne postoji, otključava samo dnevna šifra
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

    // ---------- Ručni Fokus režim ----------

    private void loadFocus() {
        JSONObject o = parse(sp.getString("focus", "{}"));
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

    private void saveFocus() {
        try {
            JSONObject o = new JSONObject();
            o.put("until", focusUntil);
            o.put("apps", new JSONArray(focusApps));
            o.put("sites", new JSONArray(focusSites));
            sp.edit().putString("focus", o.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    synchronized boolean focusActive() {
        if (focusUntil <= now()) {
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

    synchronized long focusLeft() {
        return focusActive() ? Math.max(0L, focusUntil - now()) : 0L;
    }

    synchronized Set<String> focusApps() {
        return new HashSet<>(focusApps);
    }

    synchronized Set<String> focusSites() {
        return new HashSet<>(focusSites);
    }

    synchronized boolean focusHasSites() {
        return focusActive() && !focusSites.isEmpty();
    }

    synchronized boolean focusAllowsApp(String pkg) {
        return focusActive() && focusApps.contains(pkg);
    }

    synchronized boolean focusAllowsSite(String host) {
        return focusActive() && host != null && DailySchedule.matchDomain(host, focusSites) != null;
    }

    synchronized void startFocus(long durationMs, Collection<String> apps, Collection<String> sites) {
        focusApps.clear();
        focusApps.addAll(apps);
        focusSites.clear();
        for (String site : sites) {
            String host = hostOf(site);
            if (host != null && host.contains(".")) focusSites.add(host);
        }
        focusUntil = now() + Math.max(1L, durationMs);
        saveFocus();
    }

    synchronized void stopFocus() {
        focusUntil = 0L;
        focusApps.clear();
        focusSites.clear();
        saveFocus();
    }

    // ---------- Dnevna šifra: provera ----------

    /** Vraća pokušaje i kratku blokadu i posle restartovanja procesa. */
    private void loadCodeLockout() {
        JSONObject o = parse(sp.getString("codeLockout", "{}"));
        fails = Math.max(0, o.optInt("fails", 0));
        long now = now();
        long wall = o.optLong("wall", 0L);
        if (wall <= now) {
            blockedUntil = 0L;
            return;
        }
        if (boot != -1 && o.optInt("boot", -2) == boot) {
            blockedUntil = Math.max(0L, o.optLong("el", 0L));
        } else {
            blockedUntil = SystemClock.elapsedRealtime() + (wall - now);
        }
    }

    private void saveCodeLockout() {
        long now = now();
        long left = Math.max(0L, blockedUntil - SystemClock.elapsedRealtime());
        try {
            JSONObject o = new JSONObject();
            o.put("fails", fails);
            o.put("wall", now + left);
            o.put("el", SystemClock.elapsedRealtime() + left);
            o.put("boot", boot);
            sp.edit().putString("codeLockout", o.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** Vraća null ako je dnevna šifra tačna, inače poruku. Važi samo od 17:00 do 22:00; 5 grešaka donosi 30 s čekanja. */
    synchronized String tryCode(String code) {
        long now = SystemClock.elapsedRealtime();
        if (blockedUntil > 0L && now >= blockedUntil) {
            blockedUntil = 0L;
            saveCodeLockout();
        }
        if (now < blockedUntil) {
            return "Previše pokušaja. Sačekaj " + ((blockedUntil - now) / 1000 + 1) + " s";
        }
        if (!dailyCodeVisible()) {
            return "Dnevna šifra važi od " + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00";
        }
        if (dailyCode().equals(code)) {
            fails = 0;
            saveCodeLockout();
            return null;
        }
        fails++;
        if (fails >= 5) {
            fails = 0;
            blockedUntil = now + 30000L;
            saveCodeLockout();
            return "Previše pokušaja. Sačekaj 30 s";
        }
        saveCodeLockout();
        return "Pogrešna dnevna šifra";
    }

    // ---------- Otključavanje sa pauzom ----------

    /** Koliko se dugo sme koristiti posle otključavanja šifrom. */
    static final long UNLOCK_USE_MS = 5 * 60000L;
    /** Koliko posle toga nema nikakvog otključavanja. */
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
     * Posle restarta meri se pouzdanim vremenom (vidi now()), pa ni pomeranje sata pa restart ne pomaže.
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
            since = now() - o.optLong("wall", 0L);
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
                Calendar midnight = calendarNow(); // pouzdano vreme, ne sat telefona
                midnight.add(Calendar.DAY_OF_MONTH, 1);
                midnight.set(Calendar.HOUR_OF_DAY, 0);
                midnight.set(Calendar.MINUTE, 0);
                midnight.set(Calendar.SECOND, 0);
                midnight.set(Calendar.MILLISECOND, 0);
                o.put("day", stamp());
                o.put("toMidnight", midnight.getTimeInMillis() - now());
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

    /** Vremenski žig koji se meri isto kao otključavanje: od paljenja telefona, a posle restarta po pouzdanom vremenu. */
    private JSONObject stamp() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("wall", now());
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
            since = now() - t.optLong("wall", 0L);
            if (since < 0) {
                since = ifBack;
            }
            try {
                t.put("wall", now() - since);
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
            o.put("wall", now() - since);
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

    /** Koliko puta dnevno sme da se otvori aplikacija (0 = bez ograničenja). */
    synchronized int appOpens(String pkg) {
        JSONObject o = apps.optJSONObject(pkg);
        return o == null ? 0 : o.optInt("opens", 0);
    }

    /** Najduže korišćenje u komadu, u minutima (0 = bez ograničenja). */
    synchronized int appSession(String pkg) {
        JSONObject o = apps.optJSONObject(pkg);
        return o == null ? 0 : o.optInt("session", 0);
    }

    /** Aplikacije kojima je danas potrošen dnevni limit. */
    synchronized List<String> appsOverLimit() {
        List<String> out = new ArrayList<>();
        for (String pkg : appKeysNow()) {
            int limit = appLimitNow(pkg);
            int max = appOpensNow(pkg);
            if ((limit > 0 && usedShared(pkg) >= limit * 60000L) || (max > 0 && opensToday("app:" + pkg) >= max)) out.add(pkg);
        }
        return out;
    }

    /** Sajtovi sa liste kojima je danas potrošen dnevni limit (bez onih koji su uvek blokirani). */
    synchronized List<String> sitesOverLimit() {
        List<String> out = new ArrayList<>();
        for (String d : siteKeysNow()) {
            int limit = siteLimitNow(d);
            if (limit > 0 && usedShared("site:" + d) >= limit * 60000L) out.add(d);
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

    synchronized List<String> appListNow() {
        return keysOf(enforcedApps());
    }

    synchronized int appRuleCount() {
        return apps.length();
    }

    synchronized void setApp(String pkg, boolean lock, int limitMin) {
        setApp(pkg, lock, limitMin, appOpens(pkg));
    }

    synchronized void setApp(String pkg, boolean lock, int limitMin, int maxOpens) {
        setApp(pkg, lock, limitMin, maxOpens, appSession(pkg));
    }

    synchronized void setApp(String pkg, boolean lock, int limitMin, int maxOpens, int sessionMin) {
        if (!lock && limitMin <= 0 && maxOpens <= 0 && sessionMin <= 0) {
            apps.remove(pkg);
        } else {
            try {
                JSONObject o = new JSONObject();
                o.put("lock", lock);
                o.put("limit", Math.max(0, limitMin));
                o.put("opens", Math.max(0, maxOpens));
                o.put("session", Math.max(0, sessionMin));
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
        return DailySchedule.copy(r);
    }

    /** Kopije režima, da ih ekran i servis ne menjaju mimo Store-a. */
    synchronized List<DailySchedule.Rule> schedules() {
        List<DailySchedule.Rule> out = new ArrayList<>();
        for (DailySchedule.Rule r : schedules) out.add(copy(r));
        return out;
    }

    /** Važeći periodi; jedan izmenjen režim do jutra može imati više segmenata sa istim id-em. */
    synchronized List<DailySchedule.Rule> schedulesNow() {
        List<DailySchedule.Rule> out = new ArrayList<>();
        for (DailySchedule.Rule r : enforcedRaw()) out.add(copy(r));
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
        Calendar now = calendarNow();
        for (DailySchedule.Rule r : enforced()) {
            if (r.id.equals(id) && r.active(minuteOf(now), dayOf(now))) return true;
        }
        return false;
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
        for (DailySchedule.Rule r : enforcedRaw()) {
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

    /** Šifra se prikazuje i važi samo od 17:00 do 22:00. */
    synchronized boolean dailyCodeVisible() {
        int h = calendarNow().get(Calendar.HOUR_OF_DAY);
        return h >= DailyCode.CHANGE_HOUR && h < DailyCode.LOCK_HOUR;
    }

    // ---------- Zaštita od isključivanja ----------

    /** Podešena zaštita: Čuvar ne da da se otvore njegova podešavanja u Pristupačnosti, podaci o aplikaciji i brisanje. */
    synchronized boolean protectSelf() {
        return sp.getBoolean("protect", false);
    }

    /** Uključivanje važi odmah, isključivanje tek sledećeg jutra u 06:00. */
    synchronized void setProtectSelf(boolean on) {
        sp.edit().putBoolean("protect", on).apply();
        enforce();
    }

    /** Da li zaštita sada važi. */
    synchronized boolean protectNow() {
        roll();
        return eApps == null ? protectSelf() : eProtect;
    }

    /** Da li postoji ijedno pravilo za sajtove (lista ili uključen režim sa sajtovima). */
    /** Sve aplikacije koje sada imaju neko pravilo (zaključavanje, limit ili vremenski režim). */
    synchronized Set<String> guardedApps() {
        Set<String> out = appKeysNow();
        for (DailySchedule.Rule r : enforced()) if (r.enabled) out.addAll(r.apps);
        return out;
    }

    synchronized boolean hasSiteRules() {
        if (!siteKeysNow().isEmpty()) return true;
        for (DailySchedule.Rule r : enforced()) if (r.enabled && !r.sites.isEmpty()) return true;
        return false;
    }

    // ---------- Noćna blokada ----------

    /** Podešena noćna blokada (uključivanje važi odmah, isključivanje tek sledećeg jutra u 06:00). */
    synchronized boolean nightBlock() {
        return sp.getBoolean("night", true);
    }

    synchronized boolean nightBlockNow() {
        roll();
        return eApps == null ? nightBlock() : eNight;
    }

    synchronized void setNightBlock(boolean on) {
        sp.edit().putBoolean("night", on).apply();
        enforce();
    }

    /** Da li noćna blokada upravo traje: od 22:00 do 06:00 sve iz pravila je zaključano. */
    synchronized boolean nightActive() {
        boolean on = nightBlockNow();
        int h = calendarNow().get(Calendar.HOUR_OF_DAY);
        return on && (h >= DailyCode.LOCK_HOUR || h < DailyCode.NIGHT_END_HOUR);
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
     * i ne donosi novu šifru ranije. Pamti se i koliko je sat telefona ručno pomeren u odnosu na
     * pouzdano vreme, pa ni pomeranje sata pa restart ne pomaže: posle paljenja se ta razlika oduzme.
     * Kad je uključeno automatsko vreme (sa mreže), sat telefona je pouzdan i prati se.
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
            long base = autoTime() ? wall : wall - o.optLong("skew", 0L);
            clockOff = Math.max(base, last) - el;
            // Ručno izabrana zona ne važi ni posle restarta; prati se samo automatska zona.
            zone = autoZone() || !o.has("zone") ? TimeZone.getDefault() : TimeZone.getTimeZone(o.optString("zone"));
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
            skewSaved = System.currentTimeMillis() - (el + clockOff);
            o.put("skew", skewSaved);
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
        long skew = wall - (el + clockOff);
        if (el + clockOff < SANE_WALL && wall >= SANE_WALL) {
            clockOff = wall - el; // telefon je upaljen pre nego što je dobio tačno vreme
            saveClock();
        } else if (Math.abs(skew) > 60000L && autoTime()) {
            clockOff = wall - el; // automatsko vreme je uključeno: sat telefona je tačan
            saveClock();
        } else if (el - clockSaved > 60000L || Math.abs(skew - skewSaved) > 30000L) {
            saveClock(); // sat je ručno pomeren: odmah se pamti, da restart ne pomogne
        }
        return el + clockOff;
    }

    private long autoTimeAt = -1;
    private boolean autoTime;

    /** Da li telefon uzima vreme sa mreže (provera najviše jednom u 10 s). */
    private boolean autoTime() {
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

    synchronized List<String> siteListNow() {
        List<String> out = keysOf(enforcedSites());
        Collections.sort(out);
        return out;
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

    private void prune() {
        List<String> days = keysOf(usage);
        Collections.sort(days);
        while (days.size() > 14) {
            usage.remove(days.remove(0));
            dirty = true;
        }
    }

    synchronized void addUsage(String key, long ms) {
        if (ms <= 0) return;
        long end = now();
        addUsageBetween(key, end - ms, end);
    }

    /** Interval po pouzdanom vremenu: deo pre ponoći pripada prethodnom danu. */
    synchronized void addUsageBetween(String key, long from, long to) {
        if (from >= to) return;
        for (Map.Entry<String, Long> part : UsageCalendar.split(from, to, zone()).entrySet()) {
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

    /** Da li danas još može da se poveća limit (jednom dnevno, samo kad je uključen i još nije potrošen). */
    synchronized boolean canRaiseDayLimit() {
        String d = day();
        return dayLimit() > 0 && !d.equals(sp.getString("dayLimitRaised", null)) && !d.equals(sp.getString("dayLimitHit", null));
    }

    /** Da li je ukupni limit danas već potrošen; tada je sve zaključano do ponoći i povećanje ne pomaže. */
    synchronized boolean dayLimitHitToday() {
        return day().equals(sp.getString("dayLimitHit", null));
    }

    /** Servis beleži da je limit potrošen, pa se posle toga danas više ne može povećati. */
    /** Zašto bi aplikacija bez pravila bila zaključana do jutra čim dobije pravilo, ili null. */
    synchronized String lockedOnceGuarded() {
        if (nightActive()) return "Sada traje noćna blokada";
        if (dayLimit() > 0 && dayLimitHitToday()) return "Ukupni dnevni limit je danas potrošen";
        return null;
    }

    synchronized void markDayLimitHit() {
        String d = day();
        if (!d.equals(sp.getString("dayLimitHit", null))) sp.edit().putString("dayLimitHit", d).apply();
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
        if (appKeysNow().contains(pkg)) return true;
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
        eNight = sp.getBoolean("eNight", true);
        eProtect = sp.getBoolean("eProtect", false);
        parseSchedules(sp.getString("eSchedules", "[]"), eSchedules);
        eLinks.clear();
        JSONArray el = parseArray(sp.getString("eLinks", null));
        if (el == null) eLinks.addAll(linkPairs());
        for (int i = 0; el != null && i < el.length(); i++) eLinks.add(el.optString(i));
        enforce();
    }

    private void copyToEnforced() {
        eApps = parse(apps.toString());
        eSites = parse(sites.toString());
        eSchedules.clear();
        for (DailySchedule.Rule r : schedules) eSchedules.add(copy(r));
        eNight = nightBlock();
        eProtect = protectSelf();
        eLinks.clear();
        eLinks.addAll(linkPairs());
        eDay = rulesDay();
        saveEnforced();
    }

    private void saveEnforced() {
        sp.edit().putString("eApps", eApps.toString()).putString("eSites", eSites.toString())
                .putString("eSchedules", schedulesJson(eSchedules)).putString("eDay", eDay)
                .putBoolean("eNight", eNight).putBoolean("eProtect", eProtect)
                .putString("eLinks", new JSONArray(eLinks).toString()).apply();
        expanded = null;
    }

    private static JSONArray parseArray(String s) {
        if (s == null) return null;
        try {
            return new JSONArray(s);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Dan za odložena popuštanja: menja se u 06:00, kad se završi noćna blokada, a ne u ponoć dok ona traje. */
    private String rulesDay() {
        return UsageCalendar.rulesDay(now(), zone(), DailyCode.NIGHT_END_HOUR);
    }

    /** U 06:00 važi tačno ono što je podešeno, i sva odložena popuštanja stupaju na snagu. */
    private void roll() {
        if (eApps == null) return;
        if (!rulesDay().equals(eDay)) copyToEnforced();
    }

    /** Posle svake izmene: ono što važi sada postaje strožije od starog i novog podešavanja. */
    private void enforce() {
        if (eApps == null) return;
        if (!rulesDay().equals(eDay)) {
            copyToEnforced();
            return;
        }
        eNight = eNight || nightBlock();
        eProtect = eProtect || protectSelf();
        eLinks.addAll(linkPairs());
        JSONObject na = new JSONObject();
        Set<String> keys = new HashSet<>(keysOf(eApps));
        keys.addAll(keysOf(apps));
        for (String pkg : keys) {
            JSONObject e = eApps.optJSONObject(pkg), d = apps.optJSONObject(pkg);
            boolean lock = (e != null && e.optBoolean("lock", false)) || (d != null && d.optBoolean("lock", false));
            int limit = minLimit(e == null ? 0 : e.optInt("limit", 0), d == null ? 0 : d.optInt("limit", 0));
            int max = minLimit(e == null ? 0 : e.optInt("opens", 0), d == null ? 0 : d.optInt("opens", 0));
            int ses = minLimit(e == null ? 0 : e.optInt("session", 0), d == null ? 0 : d.optInt("session", 0));
            if (!lock && limit <= 0 && max <= 0 && ses <= 0) continue;
            try {
                na.put(pkg, new JSONObject().put("lock", lock).put("limit", limit).put("opens", max).put("session", ses));
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
        List<DailySchedule.Rule> nr = DailySchedule.tighten(eSchedules, schedules);
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

    /** Važeći režimi, uz aplikaciju i njen povezan sajt (i obrnuto). */
    private List<DailySchedule.Rule> enforced() {
        roll();
        if (eApps == null) return schedules;
        if (expanded == null) expanded = Links.expand(eSchedules, eLinks);
        return expanded;
    }

    /** Važeći režimi tačno kako su podešeni, za prikaz. */
    private List<DailySchedule.Rule> enforcedRaw() {
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

    private boolean rawLock(String pkg) {
        JSONObject o = enforcedApps().optJSONObject(pkg);
        return o != null && o.optBoolean("lock", false);
    }

    private int rawLimit(String pkg) {
        JSONObject o = enforcedApps().optJSONObject(pkg);
        return o == null ? 0 : o.optInt("limit", 0);
    }

    private int rawSite(String domain) {
        JSONObject o = enforcedSites().optJSONObject(domain);
        return o == null ? -1 : o.optInt("limit", 0);
    }

    private Set<String> linksNow() {
        roll();
        return eApps == null ? new HashSet<>(linkPairs()) : eLinks;
    }

    /** Zaključana i kad je njen povezan sajt (ili poddomen) uvek blokiran. */
    synchronized boolean appLockNow(String pkg) {
        return groupLock(pkg, true);
    }

    /** Najmanji limit aplikacije i njenog povezanog sajta (sa poddomenima). */
    synchronized int appLimitNow(String pkg) {
        return groupLimit(pkg, true);
    }

    private JSONObject appsOf(boolean now) {
        return now ? enforcedApps() : apps;
    }

    private JSONObject sitesOf(boolean now) {
        return now ? enforcedSites() : sites;
    }

    private Collection<String> pairsOf(boolean now) {
        return now ? linksNow() : linkPairs();
    }

    /** Sajtovi sa pravilom koje pokriva povezan sajt aplikacije (npr. youtube.com i m.youtube.com). */
    private List<String> coveredSites(String pkg, boolean now) {
        List<String> out = new ArrayList<>();
        List<String> bases = Links.domainsOf(pairsOf(now), pkg);
        if (bases.isEmpty()) return out;
        for (String k : keysOf(sitesOf(now))) for (String base : bases) if (Links.covers(base, k)) { out.add(k); break; }
        return out;
    }

    private boolean groupLock(String pkg, boolean now) {
        JSONObject o = appsOf(now).optJSONObject(pkg);
        if (o != null && o.optBoolean("lock", false)) return true;
        for (String k : coveredSites(pkg, now)) if (sitesOf(now).optJSONObject(k).optInt("limit", 0) == 0) return true;
        return false;
    }

    private int groupLimit(String pkg, boolean now) {
        JSONObject o = appsOf(now).optJSONObject(pkg);
        int limit = o == null ? 0 : o.optInt("limit", 0);
        for (String k : coveredSites(pkg, now)) limit = minLimit(limit, sitesOf(now).optJSONObject(k).optInt("limit", 0));
        return limit;
    }

    /** Sajt dobija i pravilo cele stavke: aplikacije, njenog sajta i poddomena. */
    private int groupSite(String domain, boolean now) {
        JSONObject o = sitesOf(now).optJSONObject(domain);
        int limit = o == null ? -1 : o.optInt("limit", 0);
        for (String pkg : Links.appsOf(pairsOf(now), domain)) {
            if (groupLock(pkg, now)) return 0;
            int a = groupLimit(pkg, now);
            if (a > 0) limit = limit == 0 ? 0 : limit < 0 ? a : Math.min(limit, a);
        }
        return limit;
    }

    synchronized int appOpensNow(String pkg) {
        JSONObject o = enforcedApps().optJSONObject(pkg);
        return o == null ? 0 : o.optInt("opens", 0);
    }

    synchronized int appSessionNow(String pkg) {
        JSONObject o = enforcedApps().optJSONObject(pkg);
        return o == null ? 0 : o.optInt("session", 0);
    }

    /** Sajt nasleđuje zaključavanje (kao stalnu blokadu sa šifrom) i limit povezane aplikacije. */
    synchronized int siteLimitNow(String domain) {
        return groupSite(domain, true);
    }

    /** Sajtovi sa sopstvenim pravilom i povezani sajtovi stavki koje imaju pravilo. */
    private Set<String> siteKeysNow() {
        Set<String> out = new HashSet<>(keysOf(enforcedSites()));
        for (String p : linksNow()) {
            String pkg = Links.pkgOf(p);
            if (groupLock(pkg, true) || groupLimit(pkg, true) > 0) out.add(Links.domainOf(p));
        }
        return out;
    }

    /** Aplikacije sa sopstvenim pravilom i aplikacije čiji sajt (ili poddomen) ima pravilo. */
    private Set<String> appKeysNow() {
        Set<String> out = new HashSet<>(keysOf(enforcedApps()));
        for (String p : linksNow()) {
            String pkg = Links.pkgOf(p);
            if (!coveredSites(pkg, true).isEmpty()) out.add(pkg);
        }
        return out;
    }

    /**
     * Vreme danas, zajedno za celu stavku ("paket" ili "site:domen"): aplikacija i povezan sajt.
     * Povezan sajt beleži i vreme na poddomenima, pa se poddomeni ne sabiraju posebno.
     */
    synchronized long usedShared(String key) {
        Set<String> pairs = linksNow();
        String pkg = key;
        if (key.startsWith("site:")) {
            List<String> owners = Links.appsOf(pairs, key.substring(5));
            if (owners.isEmpty()) return usedToday(key);
            pkg = owners.get(0);
        }
        List<String> bases = Links.domainsOf(pairs, pkg);
        if (bases.isEmpty()) return usedToday(pkg);
        long site = 0;
        for (String d : bases) site = Math.max(site, usedToday("site:" + d));
        for (String d : coveredSites(pkg, true)) site = Math.max(site, usedToday("site:" + d));
        return usedToday(pkg) + site;
    }

    synchronized String matchSiteNow(String host) {
        List<String> matches = matchingSitesNow(host);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** Svaki roditeljski limit mora da se proveri i dobije vreme, i uz posebno pravilo poddomena. */
    synchronized List<String> matchingSitesNow(String host) {
        return DomainRules.matching(host, siteKeysNow());
    }

    // ---------- Aplikacija i sajt kao jedna stavka ----------

    /** Podešeni parovi "paket|domen": poznati parovi, osim razdvojenih, i oni koje je korisnik povezao. */
    private List<String> linkPairs() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : Links.KNOWN.entrySet()) {
            if (!links.has(e.getKey())) out.add(Links.pair(e.getKey(), e.getValue()));
        }
        for (String pkg : keysOf(links)) {
            String d = links.optString(pkg, "");
            if (!d.isEmpty()) out.add(Links.pair(pkg, d));
        }
        return out;
    }

    /** Sajt povezan sa aplikacijom (podešeno), ili null. */
    synchronized String linkedSite(String pkg) {
        List<String> d = Links.domainsOf(linkPairs(), pkg);
        return d.isEmpty() ? null : d.get(0);
    }

    /** Aplikacije povezane sa sajtom (podešeno). */
    synchronized List<String> linkedApps(String domain) {
        return Links.appsOf(linkPairs(), domain);
    }

    /** Podešeno zaključavanje cele stavke (važi od sledećih 06:00). */
    synchronized boolean appLockLinked(String pkg) {
        return groupLock(pkg, false);
    }

    synchronized int appLimitLinked(String pkg) {
        return groupLimit(pkg, false);
    }

    synchronized int siteLimitLinked(String domain) {
        return groupSite(domain, false);
    }

    /** Podešeni sajtovi sa pravilom koje pokriva povezan sajt aplikacije. */
    synchronized List<String> linkedSiteRules(String pkg) {
        return coveredSites(pkg, false);
    }

    /** Povezivanje važi odmah, a razdvajanje tek sutra od 06:00. */
    synchronized void setLink(String pkg, String domain) {
        try {
            links.put(pkg, domain == null ? "" : domain);
        } catch (JSONException ignored) {
        }
        sp.edit().putString("links", links.toString()).apply();
        enforce();
    }

    /** Popuštanja koja čekaju sutra, opisana rečima; prazno ako ih nema. */
    synchronized List<String> pendingChanges(android.content.pm.PackageManager pm) {
        roll();
        List<String> out = new ArrayList<>();
        if (eApps == null) return out;
        if (eNight && !nightBlock()) out.add("Noćna blokada se isključuje");
        if (eProtect && !protectSelf()) out.add("Zaštita Čuvara od isključivanja se isključuje");
        List<String> want = linkPairs();
        for (String p : eLinks) {
            if (want.contains(p)) continue;
            String name = Links.pkgOf(p);
            try { name = pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString(); } catch (Exception ignored) { }
            out.add(name + " i " + Links.domainOf(p) + " se razdvajaju");
        }
        for (String pkg : keysOf(eApps)) {
            String name = pkg;
            try { name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) { }
            boolean el = rawLock(pkg), dl = appLock(pkg);
            int ei = rawLimit(pkg), di = appLimit(pkg);
            if (el && !dl) out.add(name + ": bez zaključavanja");
            if (ei != di) out.add(name + ": " + (di <= 0 ? "bez dnevnog limita" : "limit " + di + " min umesto " + ei));
            int eo = appOpensNow(pkg), dop = appOpens(pkg);
            if (eo != dop) out.add(name + ": " + (dop <= 0 ? "bez ograničenja otvaranja" : "najviše " + dop + " otvaranja umesto " + eo));
            int es = appSessionNow(pkg), ds = appSession(pkg);
            if (es != ds) out.add(name + ": " + (ds <= 0 ? "bez ograničenja u komadu" : "najviše " + ds + " min u komadu umesto " + es));
        }
        for (String dom : keysOf(eSites)) {
            int ei = rawSite(dom), di = siteLimit(dom);
            if (ei == di) continue;
            out.add(dom + ": " + (di < 0 ? "skida se sa liste" : di == 0 ? "uvek blokiran" : "limit " + di + " min"
                    + (ei == 0 ? " umesto stalne blokade" : " umesto " + ei)));
        }
        Set<String> described = new HashSet<>();
        for (DailySchedule.Rule e : eSchedules) {
            if (!described.add(e.id)) continue;
            DailySchedule.Rule d = rule(e.id);
            if (d == null) {
                out.add("Režim „" + e.name + "“ se briše");
                continue;
            }
            boolean enabled = false, code = false, periodRemoved = false;
            Set<String> removedApps = new HashSet<>(), removedSites = new HashSet<>();
            List<String> periods = new ArrayList<>();
            for (DailySchedule.Rule segment : eSchedules) {
                if (!segment.id.equals(e.id)) continue;
                enabled |= segment.enabled;
                code |= segment.enabled && segment.code;
                periodRemoved |= !DailySchedule.coversWeek(d, segment);
                String period = segment.label() + " " + segment.daysLabel();
                if (segment.enabled && !periods.contains(period)) periods.add(period);
                for (String a : segment.apps) if (!d.apps.contains(a)) removedApps.add(a);
                for (String x : segment.sites) if (!d.sites.contains(x)) removedSites.add(x);
            }
            if (enabled && !d.enabled) out.add("Režim „" + d.name + "“ se isključuje");
            if (d.enabled && periodRemoved) out.add("Režim „" + d.name + "“: " + d.label() + " " + d.daysLabel()
                    + " umesto " + String.join(", ", periods));
            if (code && !d.code) out.add("Režim „" + d.name + "“ bez dnevne šifre van perioda");
            int apps = removedApps.size(), sites = removedSites.size();
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
        Set<String> ids = new HashSet<>();
        for (DailySchedule.Rule r : eSchedules) {
            String id = r.id;
            while (!ids.add(id)) id = newScheduleId();
            schedules.add(DailySchedule.copy(r, id));
        }
        List<String> want = linkPairs();
        try {
            for (String p : eLinks) if (!want.contains(p)) links.put(Links.pkgOf(p), Links.domainOf(p));
        } catch (JSONException ignored) {
        }
        sp.edit().putString("apps", apps.toString()).putString("sites", sites.toString()).putString("links", links.toString())
                .putString("schedules", schedulesJson()).putBoolean("night", eNight).putBoolean("protect", eProtect).apply();
        copyToEnforced();
    }

    // ---------- Kad Čuvar nije radio ----------

    private long beatSaved;

    /** Servis radi; čuva se najviše jednom u minutu, ili odmah kad force. */
    synchronized void guardBeat(boolean force) {
        long el = SystemClock.elapsedRealtime();
        if (!force && el - beatSaved < 60000L) return;
        saveBeat(false);
    }

    /** Servis je ugašen (isključen u Pristupačnosti ili zaustavljen). */
    synchronized void guardStopped() {
        saveBeat(true);
    }

    private void saveBeat(boolean stopped) {
        long el = SystemClock.elapsedRealtime();
        try {
            JSONObject o = new JSONObject();
            o.put("t", now());
            o.put("el", el);
            o.put("boot", boot);
            o.put("stopped", stopped);
            sp.edit().putString("beat", o.toString()).apply();
        } catch (JSONException ignored) {
        }
        beatSaved = el;
    }

    /**
     * Pri pokretanju servisa: ako je posle poslednjeg znaka života prošlo vreme u kome Čuvar nije radio,
     * to se zapisuje. Safe Mode se vidi po tome što je telefon u međuvremenu paljen više puta.
     */
    synchronized void guardStarted() {
        JSONObject b = parse(sp.getString("beat", "{}"));
        long el = SystemClock.elapsedRealtime();
        long t = now();
        if (b.has("t")) {
            int lastBoot = b.optInt("boot", -1);
            long lastT = b.optLong("t");
            if (boot != -1 && lastBoot == boot) {
                long gap = el - b.optLong("el", el);
                if (gap > 2 * 60000L) {
                    logGap(lastT, t, b.optBoolean("stopped") ? "isključen u Pristupačnosti" : "zaustavljen (verovatno zbog baterije)");
                }
            } else {
                long bootAt = t - el;
                if (boot != -1 && lastBoot != -1 && boot - lastBoot > 1) {
                    logGap(lastT, bootAt, "telefon je paljen bez Čuvara, na primer u Safe Mode-u");
                }
                if (el > 3 * 60000L) {
                    logGap(bootAt, t, "posle paljenja telefona nije radio " + Ui.fmt(el));
                }
            }
        }
        saveBeat(false);
    }

    private void logGap(long from, long to, String what) {
        JSONArray a;
        try {
            a = new JSONArray(sp.getString("gaps", "[]"));
        } catch (JSONException e) {
            a = new JSONArray();
        }
        try {
            a.put(new JSONObject().put("from", from).put("to", to).put("what", what));
        } catch (JSONException ignored) {
        }
        while (a.length() > 20) a.remove(0);
        sp.edit().putString("gaps", a.toString()).apply();
    }

    /** Zapisi iz poslednjih n dana, najnoviji prvi, npr. "pet 9.10. 14:05–14:40: isključen u Pristupačnosti". */
    synchronized List<String> guardGaps(int days) {
        List<String> out = new ArrayList<>();
        JSONArray a;
        try {
            a = new JSONArray(sp.getString("gaps", "[]"));
        } catch (JSONException e) {
            return out;
        }
        long since = now() - days * 86400000L;
        SimpleDateFormat f = new SimpleDateFormat("EEE d.M. HH:mm",
                new Locale.Builder().setLanguage("sr").setScript("Latn").build());
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(zone());
        hm.setTimeZone(zone());
        for (int i = a.length() - 1; i >= 0; i--) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || o.optLong("to") < since) continue;
            out.add(f.format(new Date(o.optLong("from"))) + "–" + hm.format(new Date(o.optLong("to")))
                    + ": " + o.optString("what"));
        }
        return out;
    }

    // ---------- Broj otvaranja i pokušaja ----------

    /** Koliko je danas puta otvoreno ("app:paket") ili pokušano ("try:ključ"). */
    synchronized int opensToday(String key) {
        JSONObject d = opens.optJSONObject(day());
        return d == null ? 0 : d.optInt(key, 0);
    }

    /** Broji jedno otvaranje ili pokušaj; vraća novi broj za danas. */
    synchronized int countOpen(String key) {
        String k = day();
        JSONObject d = opens.optJSONObject(k);
        try {
            if (d == null) {
                d = new JSONObject();
                opens.put(k, d);
                List<String> days = keysOf(opens);
                Collections.sort(days);
                while (days.size() > 14) opens.remove(days.remove(0));
            }
            d.put(key, d.optInt(key, 0) + 1);
        } catch (JSONException ignored) {
        }
        sp.edit().putString("opens", opens.toString()).apply();
        return d.optInt(key, 0);
    }

    // ---------- Korišćenje u komadu ----------

    /** Koliko traje obavezna pauza posle najdužeg dozvoljenog korišćenja u komadu. */
    static final long SESSION_BREAK_MS = 15 * 60000L;

    /**
     * Dodaje vreme u komadu za aplikaciju sa tim pravilom. Izlazak kraći od pauze ne prekida komad,
     * inače bi se ograničenje zaobišlo kratkim izlaskom na početni ekran. Vraća koliko je ostalo do pauze
     * (0 kad pauza upravo počinje), ili -1 ako aplikacija nema ovo pravilo.
     */
    synchronized long addSession(String pkg, long ms) {
        int cap = appSessionNow(pkg);
        if (cap <= 0) return -1;
        long now = now();
        JSONObject o = sessions.optJSONObject(pkg);
        try {
            if (o == null) {
                o = new JSONObject();
                sessions.put(pkg, o);
            }
            if (o.optLong("b", 0) > now) return 0;
            if (now - o.optLong("l", 0) > SESSION_BREAK_MS) o.put("u", 0L);
            long u = o.optLong("u", 0) + ms;
            o.put("l", now);
            if (u >= cap * 60000L) {
                o.put("u", 0L);
                o.put("b", now + SESSION_BREAK_MS);
                sp.edit().putString("sessions", sessions.toString()).apply();
                return 0;
            }
            o.put("u", u);
            dirty = true;
            return cap * 60000L - u;
        } catch (JSONException e) {
            return -1;
        }
    }

    /** Koliko je ostalo obavezne pauze posle korišćenja u komadu (0 ako je nema). */
    synchronized long sessionBreakLeft(String pkg) {
        JSONObject o = sessions.optJSONObject(pkg);
        if (o == null) return 0;
        long left = o.optLong("b", 0) - now();
        return left > 0 && left <= SESSION_BREAK_MS ? left : 0;
    }

    /** Ne zadržava sesije za aplikacije koje više nemaju pravilo. */
    private void pruneSessions() {
        long now = now();
        for (String pkg : keysOf(sessions)) {
            JSONObject o = sessions.optJSONObject(pkg);
            if (o == null) {
                sessions.remove(pkg);
                continue;
            }
            boolean hasRule = appSession(pkg) > 0 || appSessionNow(pkg) > 0;
            boolean onBreak = o.optLong("b", 0L) > now;
            long last = o.optLong("l", 0L);
            if (!hasRule && !onBreak && (last <= 0L || now - last > SESSION_BREAK_MS)) sessions.remove(pkg);
        }
    }

    /** Aplikacije koje su sada na obaveznoj pauzi. */
    synchronized List<String> appsOnBreak() {
        List<String> out = new ArrayList<>();
        for (String pkg : keysOf(sessions)) if (sessionBreakLeft(pkg) > 0) out.add(pkg);
        return out;
    }

    synchronized void flush() {
        if (!dirty) {
            return;
        }
        pruneSessions();
        sp.edit().putString("usage", usage.toString()).putString("sessions", sessions.toString()).apply();
        dirty = false;
        lastSave = SystemClock.elapsedRealtime();
    }
}
