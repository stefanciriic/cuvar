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
    /** aplikacije bez noćne blokade */
    public final Set<String> nightFree;
    /** aplikacije kojima režim van perioda ne traži dnevnu šifru */
    public final Set<String> codeFree;
    /** aplikacije van ukupnog dnevnog limita */
    public final Set<String> dayFree;
    /** parovi "paket|domen" (aplikacija i njen sajt idu zajedno) */
    public final Set<String> links;

    public EnforcedRules(Map<String, AppRule> apps, Map<String, Integer> sites, List<DailySchedule.Rule> schedules,
                         boolean night, boolean protect, Set<String> nightFree, Set<String> codeFree, Set<String> dayFree,
                         Set<String> links) {
        this.apps = Collections.unmodifiableMap(new HashMap<>(apps));
        this.sites = Collections.unmodifiableMap(new HashMap<>(sites));
        List<DailySchedule.Rule> copies = new ArrayList<>();
        for (DailySchedule.Rule r : schedules) copies.add(DailySchedule.copy(r));
        this.schedules = Collections.unmodifiableList(copies);
        this.night = night;
        this.protect = protect;
        this.nightFree = Collections.unmodifiableSet(new HashSet<>(nightFree));
        this.codeFree = Collections.unmodifiableSet(new HashSet<>(codeFree));
        this.dayFree = Collections.unmodifiableSet(new HashSet<>(dayFree));
        this.links = Collections.unmodifiableSet(new HashSet<>(links));
    }

    /**
     * Ono što važi posle izmene podešavanja: strožije od onoga što sada važi i novog podešavanja, deo po deo.
     * removeNow: aplikacija, sajt ili stavka režima potpuno skinuta iz podešavanja odmah prestaje da važi.
     * Aplikacija prosleđuje false (uklanjanje ide kroz potvrdu, vidi Removals). Ulazi se ne menjaju.
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
                RuleMerge.stricterExempt(now.nightFree, wanted.nightFree),
                RuleMerge.stricterExempt(now.codeFree, wanted.codeFree),
                RuleMerge.stricterExempt(now.dayFree, wanted.dayFree),
                links);
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
