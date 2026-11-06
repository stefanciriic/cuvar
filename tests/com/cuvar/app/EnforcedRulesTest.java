package com.cuvar.app;

import com.cuvar.app.rules.AppRule;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.rules.EnforcedRules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Srce pravila: pooštravanje važi odmah, popuštanje tek u 06:00; potpuno skidanje stavke (removeNow) odmah. */
public final class EnforcedRulesTest {
    private static int checks;

    public static void main(String[] args) {
        apps();
        sites();
        switches();
        exemptions();
        links();
        schedules();
        inputsUnchanged();
        siteRelaxation();
        System.out.println("EnforcedRulesTest: " + checks + " provera prošlo.");
    }

    private static void apps() {
        AppRule now = new AppRule(false, 30, 0, 0);
        // Kraći limit važi odmah, duži čeka.
        check(tight(apps("yt", now), apps("yt", new AppRule(false, 20, 0, 0))).apps.get("yt").limit == 20, "Kraći limit odmah");
        check(tight(apps("yt", now), apps("yt", new AppRule(false, 60, 0, 0))).apps.get("yt").limit == 30, "Duži limit čeka");
        check(tight(apps("yt", now), apps("yt", new AppRule(false, 0, 0, 0))).apps.get("yt").limit == 30, "Bez limita čeka");
        // Zaključavanje: uključivanje odmah, isključivanje čeka.
        check(tight(apps("yt", now), apps("yt", new AppRule(true, 30, 0, 0))).apps.get("yt").lock, "Zaključavanje odmah");
        check(tight(apps("yt", new AppRule(true, 0, 0, 0)), apps("yt", new AppRule(false, 0, 0, 0))).apps.get("yt").lock,
                "Otključavanje čeka");
        // Otvaranja i najduže u komadu se spajaju deo po deo.
        AppRule r = tight(apps("yt", new AppRule(false, 0, 5, 20)), apps("yt", new AppRule(false, 0, 3, 40))).apps.get("yt");
        check(r.opens == 3 && r.session == 20, "Deo po deo: manje otvaranja odmah, duži komad čeka");
        // Nova aplikacija odmah dobija pravilo.
        check(tight(none(), apps("ig", new AppRule(false, 15, 0, 0))).apps.get("ig").limit == 15, "Nova aplikacija odmah");
        // Pravilo bez ograničenja se ne čuva.
        check(!tight(none(), apps("ig", AppRule.NONE)).apps.containsKey("ig"), "Prazno pravilo se ne čuva");
        // Potpuno skidanje aplikacije: sa removeNow odmah, bez njega čeka.
        check(!EnforcedRules.tighten(apps("yt", now), none(), true).apps.containsKey("yt"), "Skidanje aplikacije odmah (removeNow)");
        check(EnforcedRules.tighten(apps("yt", now), none(), false).apps.get("yt").limit == 30, "Skidanje aplikacije čeka bez removeNow");
        // Postaviti 0 nije skidanje: aplikacija je i dalje na listi, pa popuštanje čeka.
        check(EnforcedRules.tighten(apps("yt", now), apps("yt", AppRule.NONE), true).apps.get("yt").limit == 30,
                "Nula nije skidanje: čeka i sa removeNow");
    }

    private static void sites() {
        check(tight(sites("x.com", 30), sites("x.com", 10)).sites.get("x.com") == 10, "Kraći limit sajta odmah");
        check(tight(sites("x.com", 30), sites("x.com", 60)).sites.get("x.com") == 30, "Duži limit sajta čeka");
        check(tight(sites("x.com", 30), sites("x.com", 0)).sites.get("x.com") == 0, "Stalna blokada odmah");
        check(tight(sites("x.com", 0), sites("x.com", 30)).sites.get("x.com") == 0, "Skidanje stalne blokade čeka");
        check(tight(none(), sites("x.com", 0)).sites.get("x.com") == 0, "Nov sajt odmah");
        check(!EnforcedRules.tighten(sites("x.com", 0), none(), true).sites.containsKey("x.com"), "Skidanje sajta odmah (removeNow)");
        check(EnforcedRules.tighten(sites("x.com", 0), none(), false).sites.get("x.com") == 0, "Skidanje sajta čeka bez removeNow");
    }

    private static void switches() {
        check(tight(rules(false, false), rules(true, false)).night, "Noćna blokada: uključivanje odmah");
        check(tight(rules(true, false), rules(false, false)).night, "Noćna blokada: isključivanje čeka");
        check(tight(rules(false, false), rules(false, true)).protect, "Zaštita: uključivanje odmah");
        check(tight(rules(false, true), rules(false, false)).protect, "Zaštita: isključivanje čeka");
        check(!tight(rules(false, false), rules(false, false)).night, "Isključeno ostaje isključeno");
    }

    private static void exemptions() {
        Set<String> yt = set("yt");
        EnforcedRules freeNow = with(yt, set(), set()), freeWanted = with(set(), set(), set());
        check(tight(freeNow, freeWanted).nightFree.isEmpty(), "Vraćanje noćne blokade aplikaciji odmah");
        check(tight(freeWanted, freeNow).nightFree.isEmpty(), "Novi izuzetak od noći čeka");
        check(tight(freeNow, freeNow).nightFree.equals(yt), "Postojeći izuzetak ostaje");
        check(tight(with(set(), yt, set()), with(set(), set(), set())).codeFree.isEmpty(), "Vraćanje šifre odmah");
        check(tight(with(set(), set(), set()), with(set(), yt, set())).codeFree.isEmpty(), "Oslobađanje od šifre čeka");
    }

    private static void links() {
        EnforcedRules r = tight(with(set(), set(), set("yt|youtube.com")), with(set(), set(), set("ig|instagram.com")));
        check(r.links.equals(set("yt|youtube.com", "ig|instagram.com")), "Novo povezivanje odmah, razdvajanje čeka");
    }

    private static void schedules() {
        DailySchedule.Rule work = rule("m1", true, 9 * 60, 17 * 60, set("yt"), set("x.com"));
        // Nov režim važi odmah.
        EnforcedRules r = tight(none(), mode(work));
        check(r.schedules.size() == 1 && r.schedules.get(0).enabled, "Nov režim odmah");
        // Isključivanje režima čeka.
        DailySchedule.Rule off = rule("m1", false, 9 * 60, 17 * 60, set("yt"), set("x.com"));
        r = tight(mode(work), mode(off));
        check(anyEnabled(r.schedules, "m1"), "Isključivanje režima čeka");
        // Kraći period: stari (širi) i dalje važi.
        DailySchedule.Rule shorter = rule("m1", true, 10 * 60, 12 * 60, set("yt"), set("x.com"));
        r = tight(mode(work), mode(shorter));
        check(covers(r.schedules, "m1", 9 * 60, 17 * 60), "Skraćen period čeka");
        // Duži period važi odmah.
        DailySchedule.Rule longer = rule("m1", true, 8 * 60, 18 * 60, set("yt"), set("x.com"));
        r = tight(mode(work), mode(longer));
        check(covers(r.schedules, "m1", 8 * 60, 18 * 60), "Produžen period odmah");
        // Aplikacija skinuta iz režima: sa removeNow odmah.
        DailySchedule.Rule fewer = rule("m1", true, 9 * 60, 17 * 60, set(), set("x.com"));
        r = EnforcedRules.tighten(mode(work), mode(fewer), true);
        check(!anyHas(r.schedules, "m1", "yt"), "Skidanje aplikacije iz režima odmah (removeNow)");
        r = EnforcedRules.tighten(mode(work), mode(fewer), false);
        check(anyHas(r.schedules, "m1", "yt"), "Skidanje aplikacije iz režima čeka bez removeNow");
        // Dodata aplikacija važi odmah.
        DailySchedule.Rule more = rule("m1", true, 9 * 60, 17 * 60, set("yt", "ig"), set("x.com"));
        check(anyHas(tight(mode(work), mode(more)).schedules, "m1", "ig"), "Dodata aplikacija u režim odmah");
        // Obrisan režim čeka (i sa removeNow: brisanje celog režima je popuštanje).
        check(anyEnabled(EnforcedRules.tighten(mode(work), none(), true).schedules, "m1"), "Brisanje režima čeka");
    }

    private static void inputsUnchanged() {
        DailySchedule.Rule work = rule("m1", true, 9 * 60, 17 * 60, set("yt"), set());
        EnforcedRules now = new EnforcedRules(map("yt", new AppRule(false, 30, 0, 0)), new HashMap<>(), list(work),
                true, false, set(), set(), set());
        DailySchedule.Rule fewer = rule("m1", true, 9 * 60, 17 * 60, set(), set());
        EnforcedRules wanted = mode(fewer);
        EnforcedRules.tighten(now, wanted, true);
        check(now.schedules.get(0).apps.contains("yt"), "Ulaz se ne menja");
        check(work.apps.contains("yt"), "Ni pravilo iz kog je napravljen");
        boolean immutable = false;
        try {
            now.apps.put("x", AppRule.NONE);
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }
        check(immutable, "Mape se ne mogu menjati spolja");
    }

    private static void siteRelaxation() {
        check(EnforcedRules.siteRelaxation("x.com", 30, 30) == null, "Bez promene nema poruke");
        check("x.com: skida se sa liste".equals(EnforcedRules.siteRelaxation("x.com", 30, -1)), "Skidanje sa liste");
        check("x.com: limit 60 min umesto 30".equals(EnforcedRules.siteRelaxation("x.com", 30, 60)), "Duži limit");
        check("x.com: limit 60 min umesto stalne blokade".equals(EnforcedRules.siteRelaxation("x.com", 0, 60)), "Umesto stalne blokade");
    }

    // ---------- pomoćno ----------

    private static EnforcedRules tight(EnforcedRules now, EnforcedRules wanted) {
        // Isto kao u aplikaciji: skidanje čeka (uklanjanje ide kroz potvrdu, Removals).
        return EnforcedRules.tighten(now, wanted, false);
    }

    private static EnforcedRules none() {
        return rules(false, false);
    }

    private static EnforcedRules rules(boolean night, boolean protect) {
        return new EnforcedRules(new HashMap<>(), new HashMap<>(), new ArrayList<>(), night, protect, set(), set(), set());
    }

    private static EnforcedRules apps(String pkg, AppRule r) {
        return new EnforcedRules(map(pkg, r), new HashMap<>(), new ArrayList<>(), false, false, set(), set(), set());
    }

    private static EnforcedRules sites(String dom, int limit) {
        Map<String, Integer> s = new HashMap<>();
        s.put(dom, limit);
        return new EnforcedRules(new HashMap<>(), s, new ArrayList<>(), false, false, set(), set(), set());
    }

    private static EnforcedRules with(Set<String> nightFree, Set<String> codeFree, Set<String> links) {
        return new EnforcedRules(new HashMap<>(), new HashMap<>(), new ArrayList<>(), false, false, nightFree, codeFree, links);
    }

    private static EnforcedRules mode(DailySchedule.Rule r) {
        return new EnforcedRules(new HashMap<>(), new HashMap<>(), list(r), false, false, set(), set(), set());
    }

    private static DailySchedule.Rule rule(String id, boolean enabled, int start, int end, Set<String> apps, Set<String> sites) {
        DailySchedule.Rule r = new DailySchedule.Rule(id, "Režim", enabled, start, end);
        r.apps.addAll(apps);
        r.sites.addAll(sites);
        return r;
    }

    private static boolean anyEnabled(List<DailySchedule.Rule> rules, String id) {
        for (DailySchedule.Rule r : rules) if (r.id.equals(id) && r.enabled) return true;
        return false;
    }

    private static boolean anyHas(List<DailySchedule.Rule> rules, String id, String app) {
        for (DailySchedule.Rule r : rules) if (r.id.equals(id) && r.enabled && r.apps.contains(app)) return true;
        return false;
    }

    /** Da li je svaki minut od start do end (ponedeljak) pokriven nekim uključenim delom režima. */
    private static boolean covers(List<DailySchedule.Rule> rules, String id, int start, int end) {
        for (int m = start; m < end; m++) {
            boolean any = false;
            for (DailySchedule.Rule r : rules) if (r.id.equals(id) && r.enabled && r.active(m, 0)) any = true;
            if (!any) return false;
        }
        return true;
    }

    private static Map<String, AppRule> map(String k, AppRule v) {
        Map<String, AppRule> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    private static List<DailySchedule.Rule> list(DailySchedule.Rule... r) {
        return new ArrayList<>(Arrays.asList(r));
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
