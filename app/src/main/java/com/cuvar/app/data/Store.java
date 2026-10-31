package com.cuvar.app.data;

import com.cuvar.app.rules.AppRule;
import com.cuvar.app.rules.ItemKey;
import com.cuvar.app.rules.DailyCode;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.rules.DomainRules;
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
    final JSONObject links;   // paket -> domen njegovog sajta; "" = razdvojen poznat par (vidi Links)
    final Set<String> eLinks = new HashSet<>(); // parovi "paket|domen" koji sada važe
    List<DailySchedule.Rule> expanded;           // važeći režimi sa povezanim stavkama
    String eDay;
    boolean eNight;
    boolean eProtect;
    /** Aplikacije koje noćna blokada ne zaključava, kako sada važi. */
    final Set<String> eNightFree = new HashSet<>();
    final Set<String> eCodeFree = new HashSet<>();
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

    public synchronized void setApp(String pkg, boolean lock, int limitMin, int maxOpens, int sessionMin) {
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
        enforce();
    }

    // ---------- Zaštita od isključivanja ----------

    /** Podešena zaštita: Čuvar ne da da se otvore njegova podešavanja u Pristupačnosti, podaci o aplikaciji i brisanje. */
    public synchronized boolean protectSelf() {
        return sp.getBoolean("protect", false);
    }

    /** Uključivanje važi odmah, isključivanje tek sledećeg jutra u 06:00. */
    public synchronized void setProtectSelf(boolean on) {
        sp.edit().putBoolean("protect", on).apply();
        enforce();
    }

    /** Da li zaštita sada važi. */
    public synchronized boolean protectNow() {
        roll();
        return eApps == null ? protectSelf() : eProtect;
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

    /** Podešena noćna blokada (uključivanje važi odmah, isključivanje tek sledećeg jutra u 06:00). */
    public synchronized boolean nightBlock() {
        return sp.getBoolean("night", true);
    }

    public synchronized boolean nightBlockNow() {
        roll();
        return eApps == null ? nightBlock() : eNight;
    }

    public synchronized void setNightBlock(boolean on) {
        sp.edit().putBoolean("night", on).apply();
        enforce();
    }

    /** Podešene aplikacije bez noćne blokade. */
    public synchronized Set<String> nightFree() {
        return new HashSet<>(sp.getStringSet("nightFree", Collections.emptySet()));
    }

    /** Izuzetak od noćne blokade važi tek od sutra u 06:00, a vraćanje blokade odmah. */
    public synchronized void setNightFree(String pkg, boolean free) {
        Set<String> next = nightFree();
        if (free) next.add(pkg); else next.remove(pkg);
        sp.edit().putStringSet("nightFree", next).apply();
        enforce();
    }

    /** Aplikacije koje van režima ne traže dnevnu šifru, iako je režim traži. */
    public synchronized Set<String> codeFree() {
        return new HashSet<>(sp.getStringSet("codeFree", Collections.emptySet()));
    }

    /** Oslobađanje važi tek od sutra u 06:00, a vraćanje šifre odmah. */
    public synchronized void setCodeFree(String pkg, boolean free) {
        Set<String> next = codeFree();
        if (free) next.add(pkg); else next.remove(pkg);
        sp.edit().putStringSet("codeFree", next).apply();
        enforce();
    }

    Set<String> codeFreeNow() {
        roll();
        return eApps == null ? codeFree() : eCodeFree;
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

    public synchronized void setSite(String domain, int limitMin) {
        try {
            JSONObject o = new JSONObject();
            o.put("limit", Math.max(0, limitMin));
            sites.put(domain, o);
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
        }
        sp.edit().putString("sites", sites.toString()).apply();
        enforce();
    }

    public synchronized void removeSite(String domain) {
        sites.remove(domain);
        sp.edit().putString("sites", sites.toString()).apply();
        enforce();
    }

    /** PRIVREMENO (31.10.2026, na zahtev): skidanje aplikacije ili sajta iz pravila i režima važi odmah, a ne od sutra u 06:00. */
    public static final boolean REMOVE_NOW = true;

    /** Skida aplikaciju i sajtove iz svih pravila i režima, i iz onoga što važi sada (i usred režima). */
    public synchronized void removeNow(String pkg, java.util.Collection<String> domains) {
        if (pkg != null) {
            apps.remove(pkg);
            if (eApps != null) eApps.remove(pkg);
        }
        for (String d : domains) {
            sites.remove(d);
            if (eSites != null) eSites.remove(d);
        }
        for (List<DailySchedule.Rule> list : java.util.Arrays.asList(modes.schedules, eSchedules)) {
            for (DailySchedule.Rule r : list) {
                if (pkg != null) r.apps.remove(pkg);
                r.sites.removeAll(domains);
            }
        }
        sp.edit().putString("apps", apps.toString()).putString("sites", sites.toString())
                .putString("schedules", modes.schedulesJson()).apply();
        if (eApps != null) saveEnforced();
        enforce();
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

    // ---------- Pravila koja važe sada (popuštanje od sutra) ----------

    void loadEnforced() {
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
        eNightFree.clear();
        eNightFree.addAll(sp.getStringSet("eNightFree", Collections.emptySet()));
        eCodeFree.clear();
        eCodeFree.addAll(sp.getStringSet("eCodeFree", Collections.emptySet()));
        Modes.parseSchedules(sp.getString("eSchedules", "[]"), eSchedules);
        eLinks.clear();
        JSONArray el = parseArray(sp.getString("eLinks", null));
        if (el == null) eLinks.addAll(linkPairs());
        for (int i = 0; el != null && i < el.length(); i++) eLinks.add(el.optString(i));
        enforce();
    }

    void copyToEnforced() {
        eApps = parse(apps.toString());
        eSites = parse(sites.toString());
        eSchedules.clear();
        for (DailySchedule.Rule r : modes.schedules) eSchedules.add(Modes.copy(r));
        eNight = nightBlock();
        eProtect = protectSelf();
        eNightFree.clear();
        eNightFree.addAll(nightFree());
        eCodeFree.clear();
        eCodeFree.addAll(codeFree());
        eLinks.clear();
        eLinks.addAll(linkPairs());
        eDay = rulesDay();
        saveEnforced();
    }

    void saveEnforced() {
        sp.edit().putString("eApps", eApps.toString()).putString("eSites", eSites.toString())
                .putString("eSchedules", Modes.schedulesJson(eSchedules)).putString("eDay", eDay)
                .putBoolean("eNight", eNight).putBoolean("eProtect", eProtect)
                .putStringSet("eNightFree", new HashSet<>(eNightFree))
                .putStringSet("eCodeFree", new HashSet<>(eCodeFree))
                .putString("eLinks", new JSONArray(eLinks).toString()).apply();
        clearRuleCache();
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

    /** Posle svake izmene: ono što važi sada postaje strožije od starog i novog podešavanja. */
    void enforce() {
        if (eApps == null) return;
        if (!rulesDay().equals(eDay)) {
            copyToEnforced();
            return;
        }
        eNight = eNight || nightBlock();
        eProtect = eProtect || protectSelf();
        Set<String> free = RuleMerge.stricterExempt(eNightFree, nightFree());
        eNightFree.clear();
        eNightFree.addAll(free);
        Set<String> codeFree = RuleMerge.stricterExempt(eCodeFree, codeFree());
        eCodeFree.clear();
        eCodeFree.addAll(codeFree);
        eLinks.addAll(linkPairs());
        JSONObject na = new JSONObject();
        Set<String> keys = new HashSet<>(keysOf(eApps));
        keys.addAll(keysOf(apps));
        for (String pkg : keys) {
            JSONObject d = apps.optJSONObject(pkg);
            if (REMOVE_NOW && d == null) continue;
            AppRule r = AppRule.stricter(appRule(eApps.optJSONObject(pkg)), appRule(d));
            if (!r.any()) continue;
            try {
                na.put(pkg, toJson(r));
            } catch (JSONException ignored) {
                // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
            }
        }
        JSONObject ns = new JSONObject();
        keys = new HashSet<>(keysOf(eSites));
        keys.addAll(keysOf(sites));
        for (String dom : keys) {
            JSONObject e = eSites.optJSONObject(dom), d = sites.optJSONObject(dom);
            if (REMOVE_NOW && d == null) continue;
            int a = e == null ? RuleMerge.NO_SITE : e.optInt("limit", 0), b = d == null ? RuleMerge.NO_SITE : d.optInt("limit", 0);
            int limit = RuleMerge.stricterSite(a, b);
            try {
                ns.put(dom, new JSONObject().put("limit", limit));
            } catch (JSONException ignored) {
                // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
            }
        }
        List<DailySchedule.Rule> nr = DailySchedule.tighten(eSchedules, modes.schedules);
        if (REMOVE_NOW) {
            // Stavka skinuta iz režima ne čeka 06:00.
            for (DailySchedule.Rule r : nr) {
                DailySchedule.Rule d = modes.rule(r.id);
                if (d == null) continue;
                r.apps.retainAll(d.apps);
                r.sites.retainAll(d.sites);
            }
        }
        eApps = na;
        eSites = ns;
        eSchedules.clear();
        eSchedules.addAll(nr);
        saveEnforced();
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

    /** Povezivanje važi odmah, a razdvajanje tek sutra od 06:00. */
    public synchronized void setLink(String pkg, String domain) {
        try {
            links.put(pkg, domain == null ? "" : domain);
        } catch (JSONException ignored) {
            // put sa ispravnim ključem i vrednošću ne baca grešku; podaci ostaju kakvi su bili
        }
        sp.edit().putString("links", links.toString()).apply();
        enforce();
    }

    /** Popuštanja koja čekaju sutra, opisana rečima; prazno ako ih nema. */
    public synchronized List<String> pendingChanges(android.content.pm.PackageManager pm) {
        roll();
        List<String> out = new ArrayList<>();
        if (eApps == null) return out;
        if (eNight && !nightBlock()) out.add("Noćna blokada se isključuje");
        if (eProtect && !protectSelf()) out.add("Zaštita Čuvara od isključivanja se isključuje");
        for (String pkg : nightFree()) {
            if (eNightFree.contains(pkg)) continue;
            String name = AppLabels.of(pm, pkg);
            out.add(name + ": bez noćne blokade");
        }
        for (String pkg : codeFree()) {
            if (eCodeFree.contains(pkg)) continue;
            String name = AppLabels.of(pm, pkg);
            out.add(name + ": van režima bez dnevne šifre");
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
            int ei = rawSite(dom), di = siteLimit(dom);
            if (ei == di) continue;
            out.add(dom + ": " + (di < 0 ? "skida se sa liste" : di == 0 ? "uvek blokiran" : "limit " + di + " min"
                    + (ei == 0 ? " umesto stalne blokade" : " umesto " + ei)));
        }
        Set<String> described = new HashSet<>();
        for (DailySchedule.Rule e : eSchedules) {
            if (!described.add(e.id)) continue;
            DailySchedule.Rule d = modes.rule(e.id);
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
                .putString("schedules", modes.schedulesJson()).putBoolean("night", eNight).putBoolean("protect", eProtect)
                .putStringSet("nightFree", new HashSet<>(eNightFree))
                .putStringSet("codeFree", new HashSet<>(eCodeFree))
                .apply();
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
