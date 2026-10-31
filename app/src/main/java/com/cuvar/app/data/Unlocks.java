package com.cuvar.app.data;

import com.cuvar.app.rules.CodeAttempts;
import com.cuvar.app.rules.DailyCode;
import com.cuvar.app.rules.UnlockStreak;
import com.cuvar.app.rules.UnlockTimer;

import android.os.SystemClock;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** Dnevna šifra, otključavanje sa pauzom, hitno otključavanje i brojanje otključavanja. */
public final class Unlocks {
    private final Store store;

    Unlocks(Store store) {
        this.store = store;
    }

    final CodeAttempts codeTries = new CodeAttempts();
    JSONObject unlocks; // "app:paket" / "site:domen" -> kada je otključano PIN-om
    JSONObject emergency; // hitno otključavanje: koliko je danas iskorišćeno i šta je otključano
    boolean stampMoved;        // elapsed() je posle restarta pomerio žig, treba ga sačuvati

    /** Vraća pokušaje i kratku blokadu i posle restartovanja procesa. */
    void loadCodeLockout() {
        JSONObject o = Store.parse(store.sp.getString("codeLockout", "{}"));
        codeTries.fails = Math.max(0, o.optInt("fails", 0));
        boolean sameBoot = store.boot != -1 && o.optInt("boot", -2) == store.boot;
        codeTries.blockedUntil = CodeAttempts.restore(o.optLong("wall", 0L), o.optLong("el", 0L), sameBoot,
                store.now(), SystemClock.elapsedRealtime());
    }

    void saveCodeLockout() {
        long now = store.now();
        long left = codeTries.waitLeft(SystemClock.elapsedRealtime());
        try {
            JSONObject o = new JSONObject();
            o.put("fails", codeTries.fails);
            o.put("wall", now + left);
            o.put("el", SystemClock.elapsedRealtime() + left);
            o.put("boot", store.boot);
            store.sp.edit().putString("codeLockout", o.toString()).apply();
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
        }
    }

    /** Vraća null ako je dnevna šifra tačna, inače poruku. Važi samo od 17:00 do 22:00; 5 grešaka donosi 30 s čekanja. */
    public String tryCode(String code) {
        synchronized (store) {
            long now = SystemClock.elapsedRealtime();
            if (codeTries.expire(now)) saveCodeLockout();
            long wait = codeTries.waitLeft(now);
            if (wait > 0) {
                return "Previše pokušaja. Sačekaj " + CodeAttempts.waitSeconds(wait) + " s";
            }
            if (!dailyCodeVisible()) {
                return "Dnevna šifra važi od " + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00";
            }
            if (dailyCode().equals(code)) {
                codeTries.success();
                saveCodeLockout();
                return null;
            }
            boolean blocked = codeTries.fail(now);
            saveCodeLockout();
            return blocked ? "Previše pokušaja. Sačekaj " + CodeAttempts.WAIT_MS / 1000 + " s" : "Pogrešna dnevna šifra";
        }
    }

    /** Koliko se dugo sme koristiti posle otključavanja šifrom. */
    public static final long UNLOCK_USE_MS = UnlockTimer.USE_MS;

    /** Koliko posle toga nema nikakvog otključavanja. */
    public static final long UNLOCK_COOLDOWN_MS = UnlockTimer.COOLDOWN_MS;

    /** Pamti da je stavka upravo otključana; ponovno otključavanje dok traje pauza ne pomera vreme. */
    public void startUnlock(String key) {
        synchronized (store) {
            if (sinceUnlock(key) >= 0) {
                return;
            }
            saveUnlock(key, 0);
            countUnlock();
        }
    }

    /** Upisuje jedno otključavanje (šifrom ili hitno) u današnji dan; čuva se 70 dana. */
    void countUnlock() {
        unlockSince();
        JSONObject o = Store.parse(store.sp.getString("unlockDays", "{}"));
        String d = store.day();
        try {
            o.put(d, o.optInt(d, 0) + 1);
        } catch (JSONException e) {
            return;
        }
        List<String> days = Store.keysOf(o);
        Collections.sort(days);
        while (days.size() > 70) o.remove(days.remove(0));
        store.sp.edit().putString("unlockDays", o.toString()).apply();
    }

    /** Koliko je puta danas otključano (šifrom ili hitno). */
    public int unlocksToday() {
        synchronized (store) {
            Integer n = unlockDays().get(store.day());
            return n == null ? 0 : n;
        }
    }

    /** Broj otključavanja po danu ("yyyyMMdd"). */
    public Map<String, Integer> unlockDays() {
        synchronized (store) {
            JSONObject o = Store.parse(store.sp.getString("unlockDays", "{}"));
            Map<String, Integer> out = new HashMap<>();
            for (String k : Store.keysOf(o)) out.put(k, o.optInt(k, 0));
            return out;
        }
    }

    /** Dan od kada se broje otključavanja; prvi poziv ga postavlja na danas. */
    public String unlockSince() {
        synchronized (store) {
            String s = store.sp.getString("unlockSince", null);
            if (s == null) {
                s = store.day();
                store.sp.edit().putString("unlockSince", s).apply();
            }
            return s;
        }
    }

    public int unlockStreak() {
        synchronized (store) {
            return UnlockStreak.current(unlockDays(), store.day(), unlockSince());
        }
    }

    /** Koliko je još ostalo od 5 minuta korišćenja (0 ako nije otključano). */
    public long unlockLeft(String key) {
        synchronized (store) {
            return UnlockTimer.useLeft(sinceUnlock(key));
        }
    }

    /**
     * Koliko je još ostalo do kraja pauze u kojoj se ne može otključati (0 ako pauze nema).
     * Pauza važi za sve: dok traje otključavanje ili pauza bilo čega, ništa drugo se ne otključava PIN-om.
     */
    public long cooldownLeft(String key) {
        synchronized (store) {
            long left = 0;
            for (String k : Store.keysOf(unlocks)) {
                left = Math.max(left, UnlockTimer.cooldownFor(sinceUnlock(k), k.equals(key)));
            }
            return left;
        }
    }

    /** Koliko još traju otključavanje i pauza bilo čega; dotle se postojeća pravila ne mogu menjati. */
    public long unlockBusyLeft() {
        synchronized (store) {
            long left = 0;
            for (String k : Store.keysOf(unlocks)) {
                left = Math.max(left, UnlockTimer.busyLeft(sinceUnlock(k)));
            }
            return left;
        }
    }

    /** Najduže preostalo vreme korišćenja posle otključavanja bilo čega (0 ako ništa nije otključano). */
    public long unlockUseLeft() {
        synchronized (store) {
            long left = 0;
            for (String k : Store.keysOf(unlocks)) {
                left = Math.max(left, unlockLeft(k));
            }
            return left;
        }
    }

    /**
     * Koliko je prošlo od otključavanja, ili -1 ako je i pauza završena.
     * U istom paljenju telefona meri se vremenom od paljenja, pa pomeranje sata ne pomaže.
     * Posle restarta meri se pouzdanim vremenom (vidi now()), pa ni pomeranje sata pa restart ne pomaže.
     */
    long sinceUnlock(String key) {
        JSONObject o = unlocks.optJSONObject(key);
        if (o == null) {
            return -1;
        }
        long since;
        if (store.boot != -1 && o.optInt("boot", -2) == store.boot) {
            since = SystemClock.elapsedRealtime() - o.optLong("el", 0L);
        } else {
            since = UnlockTimer.sinceAfterRestart(store.now(), o.optLong("wall", 0L));
            saveUnlock(key, since); // od sada opet meri vremenom od paljenja
        }
        since = Math.max(0L, since);
        if (UnlockTimer.finished(since)) {
            unlocks.remove(key);
            store.sp.edit().putString("unlocks", unlocks.toString()).apply();
            return -1;
        }
        return since;
    }

    /** Koliko traje hitno otključavanje. Jedini izuzetak od pauze posle otključavanja. */
    public static final long EMERGENCY_MS = 10 * 60000L;

    /** Koliko puta dnevno sme hitno otključavanje; brojač se vraća u ponoć. */
    public static final int EMERGENCY_PER_DAY = 1;

    /** Da li danas još ima hitnog otključavanja. */
    public boolean emergencyAvailable() {
        synchronized (store) {
            return emergency().optInt("used", 0) < EMERGENCY_PER_DAY;
        }
    }

    /** Koliko je još ostalo od hitnog otključavanja ove stavke (0 ako ga nema). */
    public long emergencyLeft(String key) {
        synchronized (store) {
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
    }

    /** Troši jedno hitno otključavanje za stavku; vraća false ako ga danas više nema. */
    public boolean startEmergency(String key) {
        synchronized (store) {
            if (!emergencyAvailable()) {
                return false;
            }
            JSONObject o = emergency();
            try {
                if (!o.has("day")) {
                    // Pamti se koliko je ostalo do ponoći, pa pomeranje sata ne donosi novi dan ranije.
                    Calendar midnight = store.clock.calendarNow(); // pouzdano vreme, ne sat telefona
                    midnight.add(Calendar.DAY_OF_MONTH, 1);
                    midnight.set(Calendar.HOUR_OF_DAY, 0);
                    midnight.set(Calendar.MINUTE, 0);
                    midnight.set(Calendar.SECOND, 0);
                    midnight.set(Calendar.MILLISECOND, 0);
                    o.put("day", stamp());
                    o.put("toMidnight", midnight.getTimeInMillis() - store.now());
                }
                o.put("used", o.optInt("used", 0) + 1);
                o.put("key", key);
                o.put("at", stamp());
            } catch (JSONException e) {
                return false;
            }
            saveEmergency();
            countUnlock();
            return true;
        }
    }

    /** Zapis o hitnom otključavanju; posle ponoći brojač kreće ispočetka, a započeto otključavanje traje. */
    JSONObject emergency() {
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

    void saveEmergency() {
        store.sp.edit().putString("emergency", emergency.toString()).apply();
    }

    /** Vremenski žig koji se meri isto kao otključavanje: od paljenja telefona, a posle restarta po pouzdanom vremenu. */
    JSONObject stamp() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("wall", store.now());
        o.put("el", SystemClock.elapsedRealtime());
        o.put("boot", store.boot);
        return o;
    }

    /**
     * Koliko je prošlo od žiga; posle restarta žig se pomera da opet meri od paljenja.
     * Ako je sat u međuvremenu vraćen unazad, uzima se ifBack.
     */
    long elapsed(JSONObject t, long ifBack) {
        stampMoved = false;
        long since;
        if (store.boot != -1 && t.optInt("boot", -2) == store.boot) {
            since = SystemClock.elapsedRealtime() - t.optLong("el", 0L);
        } else {
            since = store.now() - t.optLong("wall", 0L);
            if (since < 0) {
                since = ifBack;
            }
            try {
                t.put("wall", store.now() - since);
                t.put("el", SystemClock.elapsedRealtime() - since);
                t.put("boot", store.boot);
                stampMoved = true;
            } catch (JSONException ignored) {
                // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
            }
        }
        return Math.max(0L, since);
    }

    void saveUnlock(String key, long since) {
        try {
            JSONObject o = new JSONObject();
            o.put("wall", store.now() - since);
            o.put("el", SystemClock.elapsedRealtime() - since);
            o.put("boot", store.boot);
            unlocks.put(key, o);
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
        }
        store.sp.edit().putString("unlocks", unlocks.toString()).apply();
    }

    /** Šifra se prikazuje i važi samo od 17:00 do 22:00. */
    public boolean dailyCodeVisible() {
        synchronized (store) {
            int h = store.clock.calendarNow().get(Calendar.HOUR_OF_DAY);
            return h >= DailyCode.CHANGE_HOUR && h < DailyCode.LOCK_HOUR;
        }
    }

    /** Šifra koja važi sada (od 17:00 do 17:00 sledećeg dana, po pouzdanom vremenu). */
    public String dailyCode() {
        synchronized (store) {
            return DailyCode.code(codeKey(), DailyCode.dayKey(store.now(), store.clock.zone()));
        }
    }

    /** Tajni ključ za dnevnu šifru, nasumičan i samo na ovom telefonu (bez rezervne kopije). */
    byte[] codeKey() {
        String hex = store.sp.getString("codeKey", null);
        if (hex == null || hex.length() != 64) {
            byte[] k = new byte[32];
            new SecureRandom().nextBytes(k);
            StringBuilder sb = new StringBuilder();
            for (byte b : k) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            hex = sb.toString();
            store.sp.edit().putString("codeKey", hex).apply();
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
