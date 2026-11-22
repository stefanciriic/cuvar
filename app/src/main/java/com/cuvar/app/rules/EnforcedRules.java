package com.cuvar.app.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sva pravila na jednom mestu: ono što sada važi ili ono što je podešeno.
 * tighten() je srce aplikacije: pooštravanje važi odmah, popuštanje tek kad se u 06:00 podešeno prepiše preko važećeg.
 */
public final class EnforcedRules {
    /** paket -> pravilo aplikacije */
    public final Map<String, AppRule> apps;
    /** domen -> limit u minutima, 0 = uvek blokiran (domen bez pravila nije u mapi) */
    public final Map<String, Integer> sites;
    public final List<DailySchedule.Rule> schedules;
    public final boolean night;
    public final boolean protect;
    /** pun rad sa Pristupačnošću; false = laki režim (strože = true) */
    public final boolean full;
    /** aplikacije bez noćne blokade */
    public final Set<String> nightFree;
    /** aplikacije van ukupnog dnevnog limita */
    public final Set<String> dayFree;
    /** parovi "paket|domen" (aplikacija i njen sajt idu zajedno) */
    public final Set<String> links;

    public EnforcedRules(Map<String, AppRule> apps, Map<String, Integer> sites, List<DailySchedule.Rule> schedules,
                         boolean night, boolean protect, boolean full, Set<String> nightFree, Set<String> dayFree,
                         Set<String> links) {
        this.apps = Collections.unmodifiableMap(new HashMap<>(apps));
        this.sites = Collections.unmodifiableMap(new HashMap<>(sites));
        List<DailySchedule.Rule> copies = new ArrayList<>();
        for (DailySchedule.Rule r : schedules) copies.add(DailySchedule.copy(r));
        this.schedules = Collections.unmodifiableList(copies);
        this.night = night;
        this.protect = protect;
        this.full = full;
        this.nightFree = Collections.unmodifiableSet(new HashSet<>(nightFree));
        this.dayFree = Collections.unmodifiableSet(new HashSet<>(dayFree));
        this.links = Collections.unmodifiableSet(new HashSet<>(links));
    }

    /**
     * Ono što važi posle izmene podešavanja: strožije od onoga što sada važi i novog podešavanja, deo po deo.
     * removeNow: aplikacija, sajt ili stavka režima potpuno skinuta iz podešavanja odmah prestaje da važi.
     * Aplikacija prosleđuje false: i „Ukloni iz pravila“ važi tek od 06:00. Ulazi se ne menjaju.
     */
    public static EnforcedRules tighten(EnforcedRules now, EnforcedRules wanted, boolean removeNow) {
        Map<String, AppRule> apps = new HashMap<>();
        Set<String> keys = new HashSet<>(now.apps.keySet());
        keys.addAll(wanted.apps.keySet());
        for (String pkg : keys) {
            AppRule d = wanted.apps.get(pkg);
            if (removeNow && d == null) continue;
            AppRule r = AppRule.stricter(now.apps.get(pkg), d);
            if (r.any()) apps.put(pkg, r);
        }

        Map<String, Integer> sites = new HashMap<>();
        keys = new HashSet<>(now.sites.keySet());
        keys.addAll(wanted.sites.keySet());
        for (String dom : keys) {
            Integer d = wanted.sites.get(dom);
            if (removeNow && d == null) continue;
            Integer e = now.sites.get(dom);
            sites.put(dom, RuleMerge.stricterSite(e == null ? RuleMerge.NO_SITE : e, d == null ? RuleMerge.NO_SITE : d));
        }

        List<DailySchedule.Rule> schedules = DailySchedule.tighten(now.schedules, wanted.schedules);
        if (removeNow) {
            // Stavka skinuta iz režima ne čeka 06:00.
            for (DailySchedule.Rule r : schedules) {
                DailySchedule.Rule d = byId(wanted.schedules, r.id);
                if (d == null) continue;
                r.apps.retainAll(d.apps);
                r.sites.retainAll(d.sites);
            }
        }

        Set<String> links = new HashSet<>(now.links);
        links.addAll(wanted.links);
        return new EnforcedRules(apps, sites, schedules,
                now.night || wanted.night,
                now.protect || wanted.protect,
                now.full || wanted.full,
                RuleMerge.stricterExempt(now.nightFree, wanted.nightFree),
                RuleMerge.stricterExempt(now.dayFree, wanted.dayFree),
                links);
    }

    /** Da li bi after bilo blaže od before u bilo čemu (tada je to popuštanje: samo 06–10, važi sutra). */
    public static boolean loosens(EnforcedRules before, EnforcedRules after) {
        for (Map.Entry<String, AppRule> e : before.apps.entrySet()) {
            AppRule a = after.apps.get(e.getKey());
            if (a == null) a = AppRule.NONE;
            AppRule s = AppRule.stricter(e.getValue(), a);
            if (s.lock != a.lock || s.limit != a.limit || s.opens != a.opens || s.session != a.session) return true;
        }
        for (Map.Entry<String, Integer> e : before.sites.entrySet()) {
            Integer v = after.sites.get(e.getKey());
            int a = v == null ? RuleMerge.NO_SITE : v;
            if (RuleMerge.stricterSite(a, e.getValue()) != a) return true;
        }
        for (DailySchedule.Rule r : before.schedules) {
            if (ModeHolds.loosens(r, byId(after.schedules, r.id))) return true;
        }
        if ((before.night && !after.night) || (before.protect && !after.protect)
                || (before.full && !after.full)) return true;
        if (!before.nightFree.containsAll(after.nightFree) || !before.dayFree.containsAll(after.dayFree)) return true;
        return !after.links.containsAll(before.links);
    }

    /** Popuštanje limita sajta rečima, ili null kad se ništa ne popušta (wanted: NO_SITE = skida se sa liste). */
    public static String siteRelaxation(String domain, int now, int wanted) {
        if (now == wanted) return null;
        return domain + ": " + (wanted < 0 ? "skida se sa liste" : wanted == 0 ? "uvek blokiran" : "limit " + wanted + " min"
                + (now == 0 ? " umesto stalne blokade" : " umesto " + now));
    }

    /** Režim sa tim id-jem, ili null. */
    public static DailySchedule.Rule byId(List<DailySchedule.Rule> rules, String id) {
        for (DailySchedule.Rule r : rules) if (r.id.equals(id)) return r;
        return null;
    }
}
