package com.cuvar.app.data;

import com.cuvar.app.rules.AppRule;
import com.cuvar.app.rules.CodeRegimeMigration;
import com.cuvar.app.rules.ItemKey;
import com.cuvar.app.rules.NameList;
import com.cuvar.app.rules.DailyCode;
import com.cuvar.app.rules.DayLimit;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.rules.DomainRules;
import com.cuvar.app.rules.EnforcedRules;
import com.cuvar.app.rules.ModeHolds;
import com.cuvar.app.rules.RuleMerge;
import com.cuvar.app.rules.UsageCalendar;
import com.cuvar.app.ui.Ui;
import com.cuvar.app.web.Links;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Sva podešavanja i izmereno vreme. Čuva se samo na telefonu. */
public final class Store {
    private static Store instance;

    public static synchronized Store get(Context c) {
        if (instance == null) {
            instance = new Store(c.getApplicationContext());
        }
        return instance;
    }

    public final Focus focus = new Focus(this);
    public final Clock clock = new Clock(this);
    public final Unlocks unlock = new Unlocks(this);
    public final Modes modes = new Modes(this);
    public final UsageLog usageLog = new UsageLog(this);
    public final DayLimits dayLimits = new DayLimits(this);
    public final GuardLog guardLog = new GuardLog(this);
    public final Sessions sessions = new Sessions(this);
    final SharedPreferences sp;
    final JSONObject apps;   // paket -> {lock, limit}
    final JSONObject sites;  // domen -> {limit}
    // Pravila koja servis zaista primenjuje. Pooštravanje važi odmah, a popuštanje tek od sledećeg dana:
    // ovde ostaje strožija verzija dok se dan ne promeni (vidi enforce()).
    JSONObject eApps;
    JSONObject eSites;
    final List<DailySchedule.Rule> eSchedules = new ArrayList<>();
    /** Zatražena popuštanja: podešavanje od pre zahteva važi do sutra u 06:00 (vidi ModeHolds). */
    final List<ModeHolds.Hold> holds = new ArrayList<>();
    final JSONObject links;   // paket -> domen njegovog sajta; "" = razdvojen poznat par (vidi Links)
    final Set<String> eLinks = new HashSet<>(); // parovi "paket|domen" koji sada važe
    List<DailySchedule.Rule> expanded;           // važeći režimi sa povezanim stavkama
    String eDay;
    boolean eNight;
    boolean eProtect;
    /** Pun rad (sa Pristupačnošću) sada važi; false = laki režim. */
    boolean eFull = true;
    /** Aplikacije koje noćna blokada ne zaključava, kako sada važi. */
    final Set<String> eNightFree = new HashSet<>();
    /** Aplikacije van ukupnog dnevnog limita, kako sada važi. */
    final Set<String> eDayFree = new HashSet<>();
    boolean dirty;
    long lastSave;
    final int boot;          // redni broj paljenja telefona, -1 ako nije poznat

    private Store(Context c) {
        sp = c.getSharedPreferences("cuvar", Context.MODE_PRIVATE);
        clock.resolver = c.getContentResolver();
        boot = bootCount(c);
        clock.startClock();
        unlock.unlocks = parse(sp.getString("unlocks", "{}"));
        unlock.emergency = parse(sp.getString("emergency", "{}"));
        focus.loadFocus();
        unlock.loadCodeLockout();
        apps = parse(sp.getString("apps", "{}"));
        sites = parse(sp.getString("sites", "{}"));
        links = parse(sp.getString("links", "{}"));
        usageLog.usage = parse(sp.getString("usage", "{}"));
        usageLog.opens = parse(sp.getString("opens", "{}"));
        sessions.sessions = parse(sp.getString("sessions", "{}"));
        usageLog.dropSiteHistory();
        modes.migrateSchedule();
        modes.loadSchedules();
        modes.workEveryDay();
        usageLog.prune();
        loadEnforced();
        migrateRemovals();
        dayLimits.countOnlyGuarded(c);
        if (sp.contains("pin")) sp.edit().remove("pin").apply(); // PIN više ne postoji, otključava samo dnevna šifra
        if (sp.contains("notifDays")) sp.edit().remove("notifDays").remove("notifSince").apply(); // brojanje notifikacija je uklonjeno
    }

    static JSONObject parse(String s) {
        try {
            return new JSONObject(s == null ? "{}" : s);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static List<String> keysOf(JSONObject o) {
        List<String> out = new ArrayList<>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }

    /** Pouzdano trenutno vreme u milisekundama (vidi Clock). Pravila uzimaju vreme samo odavde. */
    public long now() {
        return clock.now();
    }

    // ---------- Paljenje telefona ----------

    static int bootCount(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ---------- Aplikacije ----------

    /** Pravilo aplikacije iz sačuvanog JSON-a (null = bez pravila). */
    static AppRule appRule(JSONObject o) {
        if (o == null) return AppRule.NONE;
        return new AppRule(o.optBoolean("lock", false), o.optInt("limit", 0), o.optInt("opens", 0), o.optInt("session", 0));
    }

    static JSONObject toJson(AppRule r) throws JSONException {
        return new JSONObject().put("lock", r.lock).put("limit", r.limit).put("opens", r.opens).put("session", r.session);
    }

    /** Podešeno pravilo aplikacije. */
    public synchronized AppRule appRule(String pkg) {
        return appRule(apps.optJSONObject(pkg));
    }

    /** Pravilo aplikacije koje sada važi (bez povezanog sajta). */
    synchronized AppRule appRuleNow(String pkg) {
        return appRule(enforcedApps().optJSONObject(pkg));
    }

    public synchronized boolean appLock(String pkg) {
        return appRule(pkg).lock;
    }

    public synchronized int appLimit(String pkg) {
        return appRule(pkg).limit;
    }

    /** Koliko puta dnevno sme da se otvori aplikacija (0 = bez ograničenja). */
    public synchronized int appOpens(String pkg) {
        return appRule(pkg).opens;
    }

    /** Najduže korišćenje u komadu, u minutima (0 = bez ograničenja). */
    public synchronized int appSession(String pkg) {
        return appRule(pkg).session;
    }

    /** Aplikacije kojima je danas potrošen dnevni limit. */
    public synchronized List<String> appsOverLimit() {
        List<String> out = new ArrayList<>();
        for (String pkg : appKeysNow()) {
            int limit = appLimitNow(pkg);
            int max = appOpensNow(pkg);
            if ((limit > 0 && usedShared(pkg) >= limit * 60000L) || (max > 0 && usageLog.opensToday(ItemKey.app(pkg)) >= max)) out.add(pkg);
        }
        return out;
    }

    /** Sajtovi sa liste kojima je danas potrošen dnevni limit (bez onih koji su uvek blokirani). */
    public synchronized List<String> sitesOverLimit() {
        List<String> out = new ArrayList<>();
        for (String d : siteKeysNow()) {
            int limit = siteLimitNow(d);
            if (limit > 0 && usedShared(ItemKey.site(d)) >= limit * 60000L) out.add(d);
        }
        return out;
    }

    public synchronized boolean hasAppRule(String pkg) {
        return apps.has(pkg);
    }

    /** Aplikacije sa PIN-om ili dnevnim limitom. */
    public synchronized List<String> appList() {
        return keysOf(apps);
    }

    public synchronized List<String> appListNow() {
        return keysOf(enforcedApps());
    }

    /** Vraća false (i ništa ne menja) ako je to popuštanje van 06:00–10:00 (vidi change). */
    public synchronized boolean setApp(String pkg, boolean lock, int limitMin, int maxOpens, int sessionMin) {
        return change(() -> {
            AppRule r = new AppRule(lock, limitMin, maxOpens, sessionMin);
            if (!r.any()) {
                apps.remove(pkg);
            } else {
                try {
                    apps.put(pkg, toJson(r));
                } catch (JSONException ignored) {
                    // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
                }
            }
            sp.edit().putString("apps", apps.toString()).apply();
        });
    }

    // ---------- Zaštita od isključivanja ----------

    /** Podešena zaštita: Čuvar ne da da se otvore njegova podešavanja u Pristupačnosti, podaci o aplikaciji i brisanje. */
    public synchronized boolean protectSelf() {
        return sp.getBoolean("protect", false);
    }

    /** Uključivanje važi odmah; isključivanje se traži 06:00–10:00 i važi sutra (inače false). */
    public synchronized boolean setProtectSelf(boolean on) {
        return change(() -> sp.edit().putBoolean("protect", on).apply());
    }

    /** Da li zaštita sada važi (u lakom režimu ne važi: bez Pristupačnosti ne može ni da radi). */
    public synchronized boolean protectNow() {
        roll();
        return (eApps == null ? protectSelf() : eProtect) && !lightNow();
    }

    // ---------- Laki režim (bez Pristupačnosti) ----------

    /** Podešen laki režim. */
    public synchronized boolean lightWanted() {
        return sp.getBoolean("light", false);
    }

    /** Lak -> pun važi odmah; pun -> lak se traži 06:00–10:00 i važi sutra (inače false). */
    public synchronized boolean setLight(boolean on) {
        return change(() -> sp.edit().putBoolean("light", on).apply());
    }

    /** Da li laki režim sada važi. */
    public synchronized boolean lightNow() {
        roll();
        return eApps == null ? lightWanted() : !eFull;
    }

    // Servis ova pravila pita više puta pri svakoj proveri ekrana, a menjaju se retko: računaju se jednom
    // i brišu kad se važeća pravila promene (saveEnforced). Pre učitavanja važećih pravila (eApps == null) ne pamte se.
    Set<String> cacheAppKeys, cacheSiteKeys, cacheGuarded;
    Boolean cacheSiteRules;

    void clearRuleCache() {
        expanded = null;
        cacheAppKeys = null;
        cacheSiteKeys = null;
        cacheGuarded = null;
        cacheSiteRules = null;
    }

    /** Sve aplikacije koje sada imaju neko pravilo (zaključavanje, limit ili vremenski režim). */
    public synchronized Set<String> guardedApps() {
        roll();
        if (eApps != null && cacheGuarded != null) return new HashSet<>(cacheGuarded);
        Set<String> out = new HashSet<>(appKeysNow());
        for (DailySchedule.Rule r : enforced()) if (r.enabled) out.addAll(r.apps);
        if (eApps != null) cacheGuarded = out;
        return new HashSet<>(out);
    }

    /** Da li postoji ijedno pravilo za sajtove (lista ili uključen režim sa sajtovima). */
    public synchronized boolean hasSiteRules() {
        roll();
        if (eApps != null && cacheSiteRules != null) return cacheSiteRules;
        boolean any = !siteKeysNow().isEmpty();
        for (DailySchedule.Rule r : enforced()) if (r.enabled && !r.sites.isEmpty()) any = true;
        if (eApps != null) cacheSiteRules = any;
        return any;
    }

    // ---------- Pozivi uvek prolaze ----------

    /** Aplikacije za poruke kojima pozivi (glasovni i video) uvek prolaze, ni jedno pravilo ih ne zaustavlja. */
    public static final Set<String> CALL_APPS_DEFAULT = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.whatsapp", "com.whatsapp.w4b", "com.viber.voip", "org.telegram.messenger",
            "org.thoughtcrime.securesms", "com.facebook.orca", "com.facebook.mlite"));

    /** Da li ekran poziva ove aplikacije prolazi kroz sva pravila (noć, limit, režim, pauza). */
    public synchronized boolean callsPass(String pkg) {
        Set<String> s = sp.getStringSet("callsPass", null);
        return s == null ? CALL_APPS_DEFAULT.contains(pkg) : s.contains(pkg);
    }

    /** Važi odmah u oba smera: propušta se samo ekran poziva, poruke ostaju pod pravilima. */
    public synchronized void setCallsPass(String pkg, boolean on) {
        Set<String> s = sp.getStringSet("callsPass", null);
        Set<String> next = new java.util.HashSet<>(s == null ? CALL_APPS_DEFAULT : s);
        if (on) next.add(pkg); else next.remove(pkg);
        sp.edit().putStringSet("callsPass", next).apply();
    }

    // ---------- Noćna blokada ----------

    /**
     * Opšta noćna blokada (sve iz pravila 22–06) je ukinuta 11. 11. 2026: noću važi samo režim
     * „Noćno zaključavanje“. Stara uključena blokada važi još do prvih 06:00 (eNight), kao svako popuštanje.
     */
    public synchronized boolean nightBlock() {
        return false;
    }

    public synchronized boolean nightBlockNow() {
        roll();
        return eApps == null ? nightBlock() : eNight;
    }

    /** Podešene aplikacije bez noćne blokade. */
    public synchronized Set<String> nightFree() {
        return new HashSet<>(sp.getStringSet("nightFree", Collections.emptySet()));
    }

    /** Vraćanje blokade važi odmah; izuzetak se traži 06:00–10:00 i važi sutra (inače false). */
    public synchronized boolean setNightFree(String pkg, boolean free) {
        return change(() -> {
            Set<String> next = nightFree();
            if (free) next.add(pkg); else next.remove(pkg);
            sp.edit().putStringSet("nightFree", next).apply();
        });
    }

    /** Podešene aplikacije (paket) i sajtovi ("site:domen") van ukupnog dnevnog limita. */
    public synchronized Set<String> dayFree() {
        return new HashSet<>(sp.getStringSet("dayFree", Collections.emptySet()));
    }

    /** Rezultat setDayFree: odbijeno van 06:00–10:00. */
    public static final int REFUSED = -1;
    public static final int LATER = 0;
    public static final int NOW = 1;

    /**
     * Skidanje izuzetka od ukupnog limita važi odmah. Izuzetak se traži 06:00–10:00 (inače REFUSED); važi odmah
     * za jednu aplikaciju dnevno (DayLimit.freeNow, dan po pouzdanom satu, NOW), ostali kasnije (LATER).
     */
    public synchronized int setDayFree(String pkg, boolean free) {
        boolean ok = change(() -> {
            Set<String> next = dayFree();
            if (free) next.add(pkg); else next.remove(pkg);
            sp.edit().putStringSet("dayFree", next).apply();
        });
        if (!ok) return REFUSED;
        if (!dayFreeNow(pkg, free)) return LATER;
        // Izuzetak već važi odmah: svi snimci ga sadrže, da ga sledeće jutro presek ne skine do sutra.
        if (free && !holds.isEmpty()) {
            List<ModeHolds.Hold> next = new ArrayList<>();
            for (ModeHolds.Hold h : holds) next.add(new ModeHolds.Hold(h.until, ModeHolds.withDayFree(h.rules, pkg)));
            holds.clear();
            holds.addAll(next);
            saveHolds();
        }
        return NOW;
    }

    private boolean dayFreeNow(String pkg, boolean free) {
        if (!free || eApps == null || eDayFree.contains(pkg)) return true;
        String today = day();
        if (!DayLimit.freeNow(sp.getString("dayFreeNowDay", null), sp.getString("dayFreeNowPkg", null), today, pkg)) return false;
        // I u podešenom je, pa ga enforce() (presek) ne skida.
        eDayFree.add(pkg);
        sp.edit().putString("dayFreeNowDay", today).putString("dayFreeNowPkg", pkg).apply();
        saveEnforced();
        return true;
    }

    /** Aplikacije van ukupnog dnevnog limita, kako sada važi. */
    public synchronized Set<String> dayFreeNow() {
        roll();
        return new HashSet<>(eApps == null ? dayFree() : eDayFree);
    }

    /** Da li je aplikacija sada van ukupnog dnevnog limita. */
    public synchronized boolean dayLimitFree(String pkg) {
        roll();
        return (eApps == null ? dayFree() : eDayFree).contains(pkg);
    }

    /** Da li noćna blokada sada zaključava ovu aplikaciju. */
    public synchronized boolean nightBlocksApp(String pkg) {
        if (!nightActive()) return false;
        roll();
        return !(eApps == null ? nightFree() : eNightFree).contains(pkg);
    }

    /** Da li noćna blokada upravo traje: od 22:00 do 06:00 sve iz pravila je zaključano. */
    public synchronized boolean nightActive() {
        boolean on = nightBlockNow();
        int h = clock.calendarNow().get(Calendar.HOUR_OF_DAY);
        return on && (h >= DailyCode.LOCK_HOUR || h < DailyCode.NIGHT_END_HOUR);
    }

    // ---------- Sajtovi ----------

    public synchronized List<String> siteList() {
        List<String> l = keysOf(sites);
        Collections.sort(l);
        return l;
    }

    public synchronized List<String> siteListNow() {
        List<String> out = keysOf(enforcedSites());
        Collections.sort(out);
        return out;
    }

    /** Limit u minutima; 0 = uvek blokiran; -1 = sajt nije na listi. */
    public synchronized int siteLimit(String domain) {
        JSONObject o = sites.optJSONObject(domain);
        return o == null ? -1 : o.optInt("limit", 0);
    }

    /** Vraća false (i ništa ne menja) ako je to popuštanje van 06:00–10:00. */
    public synchronized boolean setSite(String domain, int limitMin) {
        return change(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("limit", Math.max(0, limitMin));
                sites.put(domain, o);
            } catch (JSONException ignored) {
                // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
            }
            sp.edit().putString("sites", sites.toString()).apply();
        });
    }

    /** Vraća false (i ništa ne menja) van 06:00–10:00. */
    public synchronized boolean removeSite(String domain) {
        return change(() -> {
            sites.remove(domain);
            sp.edit().putString("sites", sites.toString()).apply();
        });
    }

    /**
     * „Ukloni iz pravila“: skida aplikaciju i sajtove iz svih pravila i režima u podešavanju. Kao svako popuštanje,
     * traži se 06:00–10:00 (inače false, ništa se ne menja) i važi sutra; vidi se u „Zakazane promene“.
     */
    public synchronized boolean removeFromRules(String pkg, java.util.Collection<String> domains) {
        return change(() -> removeNoSave(pkg, domains), true);
    }

    private void removeNoSave(String pkg, Collection<String> domains) {
        if (pkg != null) apps.remove(pkg);
        for (String d : domains) sites.remove(d);
        for (DailySchedule.Rule r : modes.schedules) {
            if (pkg != null) r.apps.remove(pkg);
            r.sites.removeAll(domains);
        }
        sp.edit().putString("apps", apps.toString()).putString("sites", sites.toString())
                .putString("schedules", modes.schedulesJson()).apply();
    }

    /** Prelaz: stari zahtevi za uklanjanje (čekali su potvrdu) postaju uklanjanje od sledećih 06:00. */
    private void migrateRemovals() {
        JSONArray a = parseArray(sp.getString("removals", null));
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Set<String> doms = new HashSet<>();
            JSONArray s = o.optJSONArray("sites");
            for (int j = 0; s != null && j < s.length(); j++) {
                String d = s.optString(j, "");
                if (!d.isEmpty()) doms.add(d);
            }
            String pkg = o.optString("pkg", "");
            if (!pkg.isEmpty() || !doms.isEmpty()) {
                // Stari zahtev je već čekao: prihvata se bez provere prozora, uz isto čekanje.
                String p = pkg.isEmpty() ? null : pkg;
                change(() -> removeNoSave(p, doms), false);
            }
        }
        if (sp.contains("removals")) sp.edit().remove("removals").apply();
    }

    // ---------- Dan ----------

    /** Ključ današnjeg dana ("yyyyMMdd") po pouzdanom vremenu, pa pomeranje sata ne vraća dnevne limite. */
    public synchronized String day() {
        Calendar c = clock.calendarNow();
        return UsageCalendar.dayKey(c);
    }

    // ---------- Da li je stavka pod pravilom ----------

    /** Da li je aplikacija u nekom pravilu: zaključana, sa limitom ili u uključenom režimu. */
    public synchronized boolean appGuarded(String pkg) {
        if (appKeysNow().contains(pkg)) return true;
        for (DailySchedule.Rule r : enforced()) if (r.enabled && r.apps.contains(pkg)) return true;
        return false;
    }

    /** Domen sa liste ili iz uključenog režima koji pokriva host, ili null. */
    public synchronized String siteGuarded(String host) {
        String best = matchSiteNow(host);
        for (DailySchedule.Rule r : enforced()) {
            if (!r.enabled) continue;
            String d = DailySchedule.matchDomain(host, r.sites);
            if (d != null && (best == null || d.length() > best.length())) best = d;
        }
        return best;
    }

    /**
     * Da li ukupni dnevni limit troši i zaključava aplikaciju: sopstveno pravilo sada (zaključavanje, limit…),
     * a nije van limita. Aplikacija samo u režimu nije.
     */
    public synchronized boolean dayLimitApp(String pkg) {
        return appKeysNow().contains(pkg) && !dayLimitFree(pkg);
    }

    /** Aplikacije pod ukupnim dnevnim limitom (sopstveno pravilo, bez onih van limita). */
    public synchronized Set<String> dayLimitApps() {
        Set<String> out = new HashSet<>(appKeysNow());
        out.removeAll(dayFreeNow());
        return out;
    }

    /** Sajtovi pod ukupnim dnevnim limitom: lista sajtova i povezani sajtovi stavki sa pravilom, bez sajtova van limita (za procenu). */
    Set<String> dayLimitSites() {
        Set<String> out = new HashSet<>(siteKeysNow());
        Set<String> free = dayFreeSet();
        out.removeIf(d -> free.contains(ItemKey.site(d)));
        return out;
    }

    /** Izuzeci od ukupnog limita kako sada važe, bez kopije (za proveru u servisu). */
    private Set<String> dayFreeSet() {
        roll();
        return eApps == null ? dayFree() : eDayFree;
    }

    /** Domen pod ukupnim limitom koji pokriva host, ili null: sa liste sajtova. Sajt samo u režimu nije. */
    public synchronized String dayLimitSite(String host) {
        // Sajt van ukupnog limita ("site:domen" u dayFree) ga ne troši i ne zaključava se; roditelj sa liste i dalje važi.
        return DomainRules.firstNotFree(matchingSitesNow(host), dayFreeSet());
    }

    // ---------- Pravila koja važe sada (popuštanje od sutra) ----------

    void loadEnforced() {
        migrateCodeConfigured();
        loadHolds();
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
        eFull = sp.getBoolean("eFull", true);
        eNightFree.clear();
        eNightFree.addAll(sp.getStringSet("eNightFree", Collections.emptySet()));
        eDayFree.clear();
        eDayFree.addAll(sp.getStringSet("eDayFree", Collections.emptySet()));
        Modes.parseSchedules(sp.getString("eSchedules", "[]"), eSchedules);
        eLinks.clear();
        JSONArray el = parseArray(sp.getString("eLinks", null));
        if (el == null) eLinks.addAll(linkPairs());
        for (int i = 0; el != null && i < el.length(); i++) eLinks.add(el.optString(i));
        if (migrateCodeRegimes(eSchedules, eApps, eSites, sp.getStringSet("eCodeFree", Collections.emptySet()), eLinks)) {
            saveEnforced();
        }
        if (sp.contains("eCodeFree")) sp.edit().remove("eCodeFree").apply();
        enforce();
    }

    /**
     * Jednom (i kad god se nađe režim sa šifrom): režim više ne zaključava van svog perioda. Ono što je
     * zaključavao postaje „Zaključaj“ (aplikacija) ili „uvek blokiran“ (sajt), posebno u podešenom i u
     * važećem stanju, pa se ništa ne olakšava i ništa se ne pojavljuje kao zakazano popuštanje.
     */
    private boolean migrateCodeRegimes(List<DailySchedule.Rule> rules, JSONObject appsJson, JSONObject sitesJson,
                                    Set<String> codeFree, Collection<String> pairs) {
        CodeRegimeMigration.Result m = CodeRegimeMigration.migrate(rules, codeFree, keysOf(sitesJson),
                pkg -> Links.domainsOf(pairs, pkg));
        if (!m.changed) return false;
        try {
            for (String pkg : m.lockApps) {
                AppRule r = appRule(appsJson.optJSONObject(pkg));
                appsJson.put(pkg, toJson(new AppRule(true, r.limit, r.opens, r.session)));
            }
            for (String d : m.blockSites) sitesJson.put(d, new JSONObject().put("limit", 0));
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku
        }
        return true;
    }

    /** Podešeno stanje: migracija režima sa šifrom (vidi migrateCodeRegimes), pre prvog enforce(). */
    private void migrateCodeConfigured() {
        Set<String> free = new HashSet<>(sp.getStringSet("codeFree", Collections.emptySet()));
        if (migrateCodeRegimes(modes.schedules, apps, sites, free, linkPairs())) {
            sp.edit().putString("apps", apps.toString()).putString("sites", sites.toString())
                    .putString("schedules", modes.schedulesJson()).apply();
        }
        if (sp.contains("codeFree")) sp.edit().remove("codeFree").apply();
    }

    void copyToEnforced() {
        // Staro važeće stanje pre prepisivanja: režim koji upravo traje ostaje do kraja svog perioda,
        // a zatražena popuštanja čekaju do svog dana (ModeHolds).
        List<DailySchedule.Rule> old = eApps == null ? new ArrayList<>() : new ArrayList<>(eSchedules);
        Calendar now = clock.calendarNow();
        String day = rulesDay();
        setNow(ModeHolds.morning(wantedRules(), old, holds, day, Modes.minuteOf(now), Modes.dayOf(now)));
        List<ModeHolds.Hold> live = ModeHolds.live(holds, day);
        if (live.size() != holds.size()) {
            holds.clear();
            holds.addAll(live);
            saveHolds();
        }
        eDay = day;
        saveEnforced();
    }

    void saveEnforced() {
        sp.edit().putString("eApps", eApps.toString()).putString("eSites", eSites.toString())
                .putString("eSchedules", Modes.schedulesJson(eSchedules)).putString("eDay", eDay)
                .putBoolean("eNight", eNight).putBoolean("eProtect", eProtect).putBoolean("eFull", eFull)
                .putStringSet("eNightFree", new HashSet<>(eNightFree))
                .putStringSet("eDayFree", new HashSet<>(eDayFree))
                .putString("eLinks", new JSONArray(eLinks).toString()).apply();
        clearRuleCache();
    }

    private void loadHolds() {
        holds.clear();
        JSONArray a = parseArray(sp.getString("holds", null));
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            JSONObject r = o == null ? null : o.optJSONObject("rules");
            String until = o == null ? "" : o.optString("until", "");
            if (r == null || until.length() != 8) continue; // oštećen zapis se preskače
            holds.add(new ModeHolds.Hold(until, rulesFromJson(r)));
        }
    }

    private void saveHolds() {
        JSONArray a = new JSONArray();
        for (ModeHolds.Hold h : holds) {
            try {
                a.put(new JSONObject().put("until", h.until).put("rules", rulesJson(h.rules)));
            } catch (JSONException ignored) {
                // put sa ispravnim ključem i vrednošću ne baca grešku
            }
        }
        sp.edit().putString("holds", a.toString()).apply();
    }

    private static JSONObject rulesJson(EnforcedRules r) throws JSONException {
        JSONObject a = new JSONObject(), s = new JSONObject();
        for (Map.Entry<String, AppRule> e : r.apps.entrySet()) a.put(e.getKey(), toJson(e.getValue()));
        for (Map.Entry<String, Integer> e : r.sites.entrySet()) s.put(e.getKey(), new JSONObject().put("limit", e.getValue()));
        return new JSONObject().put("apps", a).put("sites", s)
                .put("schedules", new JSONArray(Modes.schedulesJson(r.schedules)))
                .put("night", r.night).put("protect", r.protect).put("full", r.full)
                .put("nightFree", new JSONArray(r.nightFree)).put("dayFree", new JSONArray(r.dayFree))
                .put("links", new JSONArray(r.links));
    }

    private static EnforcedRules rulesFromJson(JSONObject o) {
        JSONObject a = o.optJSONObject("apps"), s = o.optJSONObject("sites");
        List<DailySchedule.Rule> schedules = new ArrayList<>();
        JSONArray sch = o.optJSONArray("schedules");
        Modes.parseSchedules(sch == null ? "[]" : sch.toString(), schedules);
        return new EnforcedRules(appMap(a == null ? new JSONObject() : a), siteMap(s == null ? new JSONObject() : s),
                schedules, o.optBoolean("night", false), o.optBoolean("protect", false),
                o.optBoolean("full", true), // stari hold (pre lakog režima) = pun rad
                stringSet(o.optJSONArray("nightFree")), stringSet(o.optJSONArray("dayFree")),
                stringSet(o.optJSONArray("links")));
    }

    private static Set<String> stringSet(JSONArray a) {
        Set<String> out = new HashSet<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String v = a.optString(i, "");
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    /** Da li sada (06:00–10:00 po pouzdanom satu) može da se zatraži popuštanje. */
    public synchronized boolean loosenAllowed() {
        return ModeHolds.inWindow(Modes.minuteOf(clock.calendarNow()));
    }

    /** Podešavanje pre izmene, da se vrati kad se popuštanje odbije. */
    private final class Configured {
        final String apps0 = apps.toString(), sites0 = sites.toString(), links0 = links.toString();
        final List<DailySchedule.Rule> schedules0 = modes.schedules();
        final boolean protect0 = sp.getBoolean("protect", false);
        final boolean light0 = sp.getBoolean("light", false);
        final boolean night0 = sp.getBoolean("night", false);
        final boolean hasNight0 = sp.contains("night");
        final Set<String> nightFree0 = nightFree(), dayFree0 = dayFree();

        void restore() {
            replace(apps, parse(apps0));
            replace(sites, parse(sites0));
            replace(links, parse(links0));
            modes.schedules.clear();
            modes.schedules.addAll(schedules0);
            SharedPreferences.Editor ed = sp.edit().putString("apps", apps0).putString("sites", sites0)
                    .putString("links", links0).putString("schedules", modes.schedulesJson())
                    .putBoolean("protect", protect0).putBoolean("light", light0)
                    .putStringSet("nightFree", nightFree0).putStringSet("dayFree", dayFree0);
            if (hasNight0) ed.putBoolean("night", night0); else ed.remove("night");
            ed.apply();
        }
    }

    private static void replace(JSONObject target, JSONObject from) {
        for (String k : keysOf(target)) target.remove(k);
        try {
            for (String k : keysOf(from)) target.put(k, from.get(k));
        } catch (JSONException ignored) {
            // kopija ispravnog JSON-a ne baca grešku
        }
    }

    /** Svaka izmena podešavanja pravila: popuštanje se traži 06:00–10:00 (inače false i ništa se ne menja). */
    boolean change(Runnable edit) {
        return change(edit, true);
    }

    /**
     * Izmena (bez enforce) pa provera: ako je podešeno sada blaže od starog, to je popuštanje. Van prozora se
     * staro vraća; inače se staro čuva kao hold do sutra u 06:00. Pooštravanje važi odmah.
     */
    synchronized boolean change(Runnable edit, boolean checkWindow) {
        EnforcedRules before = wantedRules();
        Configured saved = new Configured();
        edit.run();
        if (EnforcedRules.loosens(before, wantedRules())) {
            if (checkWindow && !loosenAllowed()) {
                saved.restore();
                return false;
            }
            holds.add(new ModeHolds.Hold(ModeHolds.until(clock.now(), clock.zone()), before));
            saveHolds();
        }
        enforce();
        return true;
    }

    /** Sačuvan niz, ili null kad ga nema ili je oštećen. */
    static JSONArray parseArray(String s) {
        if (s == null) return null;
        try {
            return new JSONArray(s);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Dan za odložena popuštanja: menja se u 06:00, kad se završi noćna blokada, a ne u ponoć dok ona traje. */
    String rulesDay() {
        return UsageCalendar.rulesDay(clock.now(), clock.zone(), DailyCode.NIGHT_END_HOUR);
    }

    /** U 06:00 važi tačno ono što je podešeno, i sva odložena popuštanja stupaju na snagu. */
    void roll() {
        if (eApps == null) return;
        if (!rulesDay().equals(eDay)) copyToEnforced();
    }

    /** Posle svake izmene: ono što važi sada postaje strožije od starog i novog podešavanja (vidi EnforcedRules). */
    void enforce() {
        if (eApps == null) return;
        if (!rulesDay().equals(eDay)) {
            copyToEnforced();
            return;
        }
        setNow(EnforcedRules.tighten(nowRules(), wantedRules(), false));
        saveEnforced();
    }

    /** Ono što sada važi. */
    EnforcedRules nowRules() {
        return new EnforcedRules(appMap(eApps), siteMap(eSites), eSchedules, eNight, eProtect, eFull, eNightFree, eDayFree, eLinks);
    }

    /** Ono što je podešeno. */
    EnforcedRules wantedRules() {
        return new EnforcedRules(appMap(apps), siteMap(sites), modes.schedules, nightBlock(), protectSelf(), !lightWanted(),
                nightFree(), dayFree(), new HashSet<>(linkPairs()));
    }

    private void setNow(EnforcedRules r) {
        JSONObject na = new JSONObject(), ns = new JSONObject();
        try {
            for (Map.Entry<String, AppRule> e : r.apps.entrySet()) na.put(e.getKey(), toJson(e.getValue()));
            for (Map.Entry<String, Integer> e : r.sites.entrySet()) ns.put(e.getKey(), new JSONObject().put("limit", e.getValue()));
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku
        }
        eApps = na;
        eSites = ns;
        eSchedules.clear();
        for (DailySchedule.Rule s : r.schedules) eSchedules.add(DailySchedule.copy(s));
        eNight = r.night;
        eProtect = r.protect;
        eFull = r.full;
        eNightFree.clear();
        eNightFree.addAll(r.nightFree);
        eDayFree.clear();
        eDayFree.addAll(r.dayFree);
        eLinks.clear();
        eLinks.addAll(r.links);
    }

    /** Pravila aplikacija iz JSON-a; stavka koja nije objekat (oštećena) se ne računa. */
    static Map<String, AppRule> appMap(JSONObject o) {
        Map<String, AppRule> out = new HashMap<>();
        for (String k : keysOf(o)) {
            JSONObject v = o.optJSONObject(k);
            if (v != null) out.put(k, appRule(v));
        }
        return out;
    }

    /** Limiti sajtova iz JSON-a (0 = uvek blokiran); stavka koja nije objekat se ne računa. */
    static Map<String, Integer> siteMap(JSONObject o) {
        Map<String, Integer> out = new HashMap<>();
        for (String k : keysOf(o)) {
            JSONObject v = o.optJSONObject(k);
            if (v != null) out.put(k, v.optInt("limit", 0));
        }
        return out;
    }

    /** Važeći režimi, uz aplikaciju i njen povezan sajt (i obrnuto). */
    List<DailySchedule.Rule> enforced() {
        roll();
        if (eApps == null) return modes.schedules;
        if (expanded == null) expanded = Links.expand(eSchedules, eLinks);
        return expanded;
    }

    /** Važeći režimi tačno kako su podešeni, za prikaz. */
    List<DailySchedule.Rule> enforcedRaw() {
        roll();
        return eApps == null ? modes.schedules : eSchedules;
    }

    JSONObject enforcedApps() {
        roll();
        return eApps == null ? apps : eApps;
    }

    JSONObject enforcedSites() {
        roll();
        return eSites == null ? sites : eSites;
    }

    int rawSite(String domain) {
        JSONObject o = enforcedSites().optJSONObject(domain);
        return o == null ? -1 : o.optInt("limit", 0);
    }

    Set<String> linksNow() {
        roll();
        return eApps == null ? new HashSet<>(linkPairs()) : eLinks;
    }

    /** Zaključana i kad je njen povezan sajt (ili poddomen) uvek blokiran. */
    public synchronized boolean appLockNow(String pkg) {
        return groupLock(pkg, true);
    }

    /** Najmanji limit aplikacije i njenog povezanog sajta (sa poddomenima). */
    public synchronized int appLimitNow(String pkg) {
        return groupLimit(pkg, true);
    }

    JSONObject appsOf(boolean now) {
        return now ? enforcedApps() : apps;
    }

    JSONObject sitesOf(boolean now) {
        return now ? enforcedSites() : sites;
    }

    Collection<String> pairsOf(boolean now) {
        return now ? linksNow() : linkPairs();
    }

    /** Sajtovi sa pravilom koje pokriva povezan sajt aplikacije (npr. youtube.com i m.youtube.com). */
    List<String> coveredSites(String pkg, boolean now) {
        List<String> out = new ArrayList<>();
        List<String> bases = Links.domainsOf(pairsOf(now), pkg);
        if (bases.isEmpty()) return out;
        for (String k : keysOf(sitesOf(now))) for (String base : bases) if (Links.covers(base, k)) { out.add(k); break; }
        return out;
    }

    boolean groupLock(String pkg, boolean now) {
        if (appRule(appsOf(now).optJSONObject(pkg)).lock) return true;
        for (String k : coveredSites(pkg, now)) if (sitesOf(now).optJSONObject(k).optInt("limit", 0) == 0) return true;
        return false;
    }

    int groupLimit(String pkg, boolean now) {
        int limit = appRule(appsOf(now).optJSONObject(pkg)).limit;
        for (String k : coveredSites(pkg, now)) limit = RuleMerge.stricterLimit(limit, sitesOf(now).optJSONObject(k).optInt("limit", 0));
        return limit;
    }

    /** Sajt dobija i pravilo cele stavke: aplikacije, njenog sajta i poddomena. */
    int groupSite(String domain, boolean now) {
        JSONObject o = sitesOf(now).optJSONObject(domain);
        int limit = o == null ? -1 : o.optInt("limit", 0);
        for (String pkg : Links.appsOf(pairsOf(now), domain)) {
            if (groupLock(pkg, now)) return 0;
            int a = groupLimit(pkg, now);
            if (a > 0) limit = limit == 0 ? 0 : limit < 0 ? a : Math.min(limit, a);
        }
        return limit;
    }

    public synchronized int appOpensNow(String pkg) {
        return appRuleNow(pkg).opens;
    }

    public synchronized int appSessionNow(String pkg) {
        return appRuleNow(pkg).session;
    }

    /** Sajt nasleđuje zaključavanje (kao stalnu blokadu sa šifrom) i limit povezane aplikacije. */
    public synchronized int siteLimitNow(String domain) {
        return groupSite(domain, true);
    }

    /** Sajtovi sa sopstvenim pravilom i povezani sajtovi stavki koje imaju pravilo. */
    Set<String> siteKeysNow() {
        roll();
        if (eApps != null && cacheSiteKeys != null) return cacheSiteKeys;
        Set<String> out = new HashSet<>(keysOf(enforcedSites()));
        for (String p : linksNow()) {
            String pkg = Links.pkgOf(p);
            if (groupLock(pkg, true) || groupLimit(pkg, true) > 0) out.add(Links.domainOf(p));
        }
        Set<String> result = Collections.unmodifiableSet(out);
        if (eApps != null) cacheSiteKeys = result;
        return result;
    }

    /** Aplikacije sa sopstvenim pravilom i aplikacije čiji sajt (ili poddomen) ima pravilo. */
    Set<String> appKeysNow() {
        roll();
        if (eApps != null && cacheAppKeys != null) return cacheAppKeys;
        Set<String> out = new HashSet<>(keysOf(enforcedApps()));
        for (String p : linksNow()) {
            String pkg = Links.pkgOf(p);
            if (!coveredSites(pkg, true).isEmpty()) out.add(pkg);
        }
        Set<String> result = Collections.unmodifiableSet(out);
        if (eApps != null) cacheAppKeys = result;
        return result;
    }

    /**
     * Vreme danas, zajedno za celu stavku ("paket" ili "site:domen"): aplikacija i povezan sajt.
     * Povezan sajt beleži i vreme na poddomenima, pa se poddomeni ne sabiraju posebno.
     */
    public synchronized long usedShared(String key) {
        Set<String> pairs = linksNow();
        String pkg = key;
        if (ItemKey.isSite(key)) {
            List<String> owners = Links.appsOf(pairs, ItemKey.name(key));
            if (owners.isEmpty()) return usageLog.usedToday(key);
            pkg = owners.get(0);
        }
        List<String> bases = Links.domainsOf(pairs, pkg);
        if (bases.isEmpty()) return usageLog.usedToday(pkg);
        long site = 0;
        for (String d : bases) site = Math.max(site, usageLog.usedToday(ItemKey.site(d)));
        for (String d : coveredSites(pkg, true)) site = Math.max(site, usageLog.usedToday(ItemKey.site(d)));
        return usageLog.usedToday(pkg) + site;
    }

    public synchronized String matchSiteNow(String host) {
        List<String> matches = matchingSitesNow(host);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** Svaki roditeljski limit mora da se proveri i dobije vreme, i uz posebno pravilo poddomena. */
    public synchronized List<String> matchingSitesNow(String host) {
        return DomainRules.matching(host, siteKeysNow());
    }

    // ---------- Aplikacija i sajt kao jedna stavka ----------

    /** Podešeni parovi "paket|domen": poznati parovi, osim razdvojenih, i oni koje je korisnik povezao. */
    List<String> linkPairs() {
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
    public synchronized String linkedSite(String pkg) {
        List<String> d = Links.domainsOf(linkPairs(), pkg);
        return d.isEmpty() ? null : d.get(0);
    }

    /** Aplikacije povezane sa sajtom (podešeno). */
    public synchronized List<String> linkedApps(String domain) {
        return Links.appsOf(linkPairs(), domain);
    }

    /** Podešeno zaključavanje cele stavke (važi od sledećih 06:00). */
    public synchronized boolean appLockLinked(String pkg) {
        return groupLock(pkg, false);
    }

    public synchronized int appLimitLinked(String pkg) {
        return groupLimit(pkg, false);
    }

    public synchronized int siteLimitLinked(String domain) {
        return groupSite(domain, false);
    }

    /** Podešeni sajtovi sa pravilom koje pokriva povezan sajt aplikacije. */
    public synchronized List<String> linkedSiteRules(String pkg) {
        return coveredSites(pkg, false);
    }

    /**
     * Zbir podešenih dnevnih limita aplikacija i sajtova, svaka stavka jednom (aplikacija sa povezanim
     * sajtom je jedna stavka). Stavka skipPkg / skipDomain (ona koja se menja) i aplikacije van ukupnog
     * limita (dayFree, i sajtovi "site:domen") se ne računaju.
     */
    public synchronized int limitsTotal(String skipPkg, String skipDomain) {
        Set<String> pkgs = new HashSet<>(keysOf(apps));
        List<String> pairs = linkPairs();
        List<String> siteKeys = keysOf(sites);
        for (String d : siteKeys) pkgs.addAll(Links.appsOf(pairs, d));
        Set<String> counted = new HashSet<>();
        Set<String> free = dayFree();
        int total = 0;
        for (String pkg : pkgs) {
            // Izuzeta aplikacija ne troši ukupni limit; njen sajt u pregledaču troši, pa se broji kao sajt.
            if (free.contains(pkg)) continue;
            List<String> covered = coveredSites(pkg, false);
            counted.addAll(covered);
            if (pkg.equals(skipPkg) || (skipDomain != null && covered.contains(skipDomain))) continue;
            if (groupLock(pkg, false)) continue;
            int l = groupLimit(pkg, false);
            if (l > 0) total += l;
        }
        for (String d : siteKeys) {
            if (counted.contains(d) || d.equals(skipDomain) || free.contains(ItemKey.site(d))) continue;
            boolean nested = false;
            // Poddomen izuzetog roditelja se broji sam.
            for (String parent : siteKeys) nested |= d.endsWith("." + parent) && !free.contains(ItemKey.site(parent));
            if (nested) continue;
            int l = sites.optJSONObject(d) == null ? 0 : sites.optJSONObject(d).optInt("limit", 0);
            if (l > 0) total += l;
        }
        return total;
    }

    /** Najmanji podešeni limit (> 0) roditeljskih sajtova domena koji nisu van ukupnog limita, ili 0. */
    public synchronized int parentSiteLimit(String domain) {
        Map<String, Integer> limits = new HashMap<>();
        Set<String> free = dayFree();
        for (String d : keysOf(sites)) {
            // Roditelj van ukupnog limita se ne sabira (limitsTotal), pa poddomen ispod njega nije "u njemu".
            if (free.contains(ItemKey.site(d))) continue;
            limits.put(d, sites.optJSONObject(d) == null ? 0 : sites.optJSONObject(d).optInt("limit", 0));
        }
        return DayLimit.parentLimit(domain, limits);
    }

    /** Granica za zbir limita aplikacija i sajtova. */
    public synchronized int limitBudget() {
        return DayLimit.budget(dayLimits.dayLimit());
    }

    /** Povezivanje važi odmah; razdvajanje se traži 06:00–10:00 i važi sutra (inače false). */
    public synchronized boolean setLink(String pkg, String domain) {
        return change(() -> {
            try {
                links.put(pkg, domain == null ? "" : domain);
            } catch (JSONException ignored) {
                // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
            }
            sp.edit().putString("links", links.toString()).apply();
        });
    }

    /** Naziv aplikacije, ili domen za ključ "site:domen". */
    private static String itemName(android.content.pm.PackageManager pm, String key) {
        return ItemKey.isSite(key) ? ItemKey.name(key) : AppLabels.of(pm, key);
    }

    /** Početak poslednjeg reda pendingChanges: kad najkasnije važe zatražena popuštanja. */
    public static final String PENDING_UNTIL = "Zatražena popuštanja važe najkasnije od ";

    /** Popuštanja koja čekaju, opisana rečima (poslednji red: do kada se čeka); prazno ako ih nema. */
    public synchronized List<String> pendingChanges(android.content.pm.PackageManager pm) {
        roll();
        List<String> out = new ArrayList<>();
        if (eApps == null) return out;
        if (eProtect && !protectSelf()) out.add("Zaštita Čuvara od isključivanja se isključuje");
        if (eFull && lightWanted()) out.add("Laki režim (bez Pristupačnosti) počinje");
        for (String pkg : nightFree()) {
            if (eNightFree.contains(pkg)) continue;
            String name = AppLabels.of(pm, pkg);
            out.add(name + ": bez noćne blokade");
        }
        for (String pkg : dayFree()) {
            if (eDayFree.contains(pkg)) continue;
            String name = itemName(pm, pkg);
            out.add(name + ": van ukupnog dnevnog limita");
        }
        List<String> want = linkPairs();
        for (String p : eLinks) {
            if (want.contains(p)) continue;
            String name = AppLabels.of(pm, Links.pkgOf(p));
            out.add(name + " i " + Links.domainOf(p) + " se razdvajaju");
        }
        for (String pkg : keysOf(eApps)) {
            String name = AppLabels.of(pm, pkg);
            for (String change : AppRule.relaxations(appRuleNow(pkg), appRule(pkg))) out.add(name + ": " + change);
        }
        for (String dom : keysOf(eSites)) {
            String change = EnforcedRules.siteRelaxation(dom, rawSite(dom), siteLimit(dom));
            if (change != null) out.add(change);
        }
        Set<String> described = new HashSet<>();
        List<DailySchedule.Rule> segments = pendingSegments();
        for (DailySchedule.Rule e : segments) {
            if (!described.add(e.id)) continue;
            describeMode(pm, e, segments, out);
        }
        String until = null;
        for (ModeHolds.Hold h : ModeHolds.live(holds, rulesDay())) {
            if (until == null || h.until.compareTo(until) > 0) until = h.until;
        }
        if (!out.isEmpty() && until != null) {
            out.add(PENDING_UNTIL + until.substring(6, 8) + ". " + until.substring(4, 6)
                    + "." + " u 06:00");
        }
        return out;
    }

    /** Popuštanja jednog režima (svi njegovi važeći segmenti prema podešenom). */
    private void describeMode(android.content.pm.PackageManager pm, DailySchedule.Rule e,
                              List<DailySchedule.Rule> segments, List<String> out) {
        DailySchedule.Rule d = modes.rule(e.id);
        if (d == null) {
            out.add("Režim „" + e.name + "“ se briše");
            return;
        }
        boolean enabled = false, periodRemoved = false;
        Set<String> removedApps = new HashSet<>(), removedSites = new HashSet<>();
        List<String> periods = new ArrayList<>();
        for (DailySchedule.Rule segment : segments) {
            if (!segment.id.equals(e.id)) continue;
            enabled |= segment.enabled;
            periodRemoved |= !DailySchedule.coversWeek(d, segment);
            String period = segment.label() + " " + segment.daysLabel();
            if (segment.enabled && !periods.contains(period)) periods.add(period);
            for (String a : segment.apps) if (!d.apps.contains(a)) removedApps.add(a);
            for (String x : segment.sites) if (!d.sites.contains(x)) removedSites.add(x);
        }
        if (enabled && !d.enabled) out.add("Režim „" + d.name + "“ se isključuje");
        if (d.enabled && periodRemoved) out.add("Režim „" + d.name + "“: " + d.label() + " " + d.daysLabel()
                + " umesto " + String.join(", ", periods));
        if (!removedApps.isEmpty() || !removedSites.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String a : removedApps) names.add(AppLabels.of(pm, a));
            names.addAll(removedSites);
            out.add("Režim „" + d.name + "“: uklanja se " + NameList.join(names, " i ", " i još "));
        }
    }

    /** Važeći segmenti režima bez zadržanih delova koji su već prošli (ModeHolds.expiredCarry); samo za prikaz. */
    private List<DailySchedule.Rule> pendingSegments() {
        Calendar now = clock.calendarNow();
        int m = Modes.minuteOf(now), d = Modes.dayOf(now);
        List<ModeHolds.Hold> live = ModeHolds.live(holds, rulesDay());
        List<DailySchedule.Rule> out = new ArrayList<>();
        for (DailySchedule.Rule r : eSchedules) if (!ModeHolds.expiredCarry(r, modes.schedules, live, m, d)) out.add(r);
        return out;
    }

    /** Odustaje od popuštanja koja čekaju sutra: podešavanje se vraća na ono što sada važi. */
    public synchronized void cancelPending() {
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
            // kopija ispravnog JSON-a ne baca grešku; inače ostaje podešavanje kakvo je bilo
        }
        modes.schedules.clear();
        Set<String> ids = new HashSet<>();
        // Ceo eSchedules: prošao zadržani deo postaje strožiji jednodnevni režim, ali se pravi režim nikad ne gubi.
        for (DailySchedule.Rule r : eSchedules) {
            String id = r.id;
            while (!ids.add(id)) id = Modes.newScheduleId();
            modes.schedules.add(DailySchedule.copy(r, id));
        }
        List<String> want = linkPairs();
        try {
            for (String p : eLinks) if (!want.contains(p)) links.put(Links.pkgOf(p), Links.domainOf(p));
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
        }
        sp.edit().putString("apps", apps.toString()).putString("sites", sites.toString()).putString("links", links.toString())
                .putString("schedules", modes.schedulesJson()).putBoolean("night", eNight).putBoolean("protect", eProtect).putBoolean("light", !eFull)
                .putStringSet("nightFree", new HashSet<>(eNightFree))
                .putStringSet("dayFree", new HashSet<>(eDayFree))
                .apply();
        // Podešeno sada sadrži sve što važi, pa zatražena popuštanja otpadaju.
        holds.clear();
        saveHolds();
        copyToEnforced();
    }

    // ---------- Čuvanje ----------

    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        sessions.pruneSessions();
        sp.edit().putString("usage", usageLog.usage.toString()).putString("sessions", sessions.sessions.toString()).apply();
        dirty = false;
        lastSave = SystemClock.elapsedRealtime();
    }
}
