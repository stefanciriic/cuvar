package com.cuvar.app;

import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.rules.DailySchedule.Rule;
import com.cuvar.app.rules.AppRule;
import com.cuvar.app.rules.EnforcedRules;
import com.cuvar.app.rules.ModeHolds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Izlazak iz režima: samo 06–10, važi sutra, a režim preko 06:00 ostaje do kraja perioda. */
public final class ModeHoldsTest {
    private static int checks;
    private static final String YT = "com.google.android.youtube";

    public static void main(String[] args) {
        loosens();
        window();
        until();
        carry();
        morningHold();
        morningOverSix();
        loosensAll();
        morningAll();
        System.out.println("ModeHoldsTest: " + checks + " provera prošlo.");
    }

    private static Rule rule(int start, int end) {
        Rule r = new Rule("a", "Radno vreme", true, start, end);
        r.apps.add(YT);
        r.sites.add("youtube.com");
        return r;
    }

    private static void loosens() {
        Rule old = rule(9 * 60, 17 * 60);
        Rule c = DailySchedule.copy(old);
        c.apps.remove(YT);
        check(ModeHolds.loosens(old, c), "Uklonjena aplikacija");
        c = DailySchedule.copy(old);
        c.sites.remove("youtube.com");
        check(ModeHolds.loosens(old, c), "Uklonjen sajt");
        c = DailySchedule.copy(old);
        c.enabled = false;
        check(ModeHolds.loosens(old, c), "Isključen režim");
        check(ModeHolds.loosens(old, null), "Obrisan režim");
        c = DailySchedule.copy(old);
        c.end = 16 * 60;
        check(ModeHolds.loosens(old, c), "Kraći period");
        c = DailySchedule.copy(old);
        c.days = DailySchedule.WORK_DAYS;
        check(ModeHolds.loosens(old, c), "Manje dana");

        c = DailySchedule.copy(old);
        c.apps.add("com.instagram.android");
        check(!ModeHolds.loosens(old, c), "Dodavanje nije izlazak");
        c = DailySchedule.copy(old);
        c.name = "Posao";
        check(!ModeHolds.loosens(old, c), "Preimenovanje nije izlazak");
        c = DailySchedule.copy(old);
        c.start = 8 * 60;
        check(!ModeHolds.loosens(old, c), "Duži period nije izlazak");
        Rule empty = new Rule("b", "Prazan", true, 9 * 60, 17 * 60);
        check(!ModeHolds.loosens(empty, null), "Prazan režim ne blokira ništa");
        Rule off = DailySchedule.copy(old);
        off.enabled = false;
        Rule offLess = DailySchedule.copy(off);
        offLess.apps.clear();
        check(!ModeHolds.loosens(off, offLess), "Isključen režim može da se menja");
        check(!ModeHolds.loosens(null, old), "Nov režim");
    }

    private static void window() {
        check(!ModeHolds.inWindow(5 * 60 + 59), "05:59 van prozora");
        check(ModeHolds.inWindow(6 * 60), "06:00 u prozoru");
        check(ModeHolds.inWindow(9 * 60 + 59), "09:59 u prozoru");
        check(!ModeHolds.inWindow(10 * 60), "10:00 van prozora");
        check(!ModeHolds.inWindow(17 * 60 + 1), "17:01 van prozora");
    }

    private static long at(TimeZone z, int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance(z);
        c.clear();
        c.set(y, m - 1, d, h, min);
        return c.getTimeInMillis();
    }

    private static void until() {
        TimeZone bg = TimeZone.getTimeZone("Europe/Belgrade");
        eq(ModeHolds.until(at(bg, 2026, 11, 16, 6, 0), bg), "20261117", "06:00 -> sutra");
        eq(ModeHolds.until(at(bg, 2026, 11, 16, 9, 59), bg), "20261117", "09:59 -> sutra");
        // 29.3.2026 traje 23 h, 25.10.2026 traje 25 h.
        eq(ModeHolds.until(at(bg, 2026, 3, 28, 6, 0), bg), "20260329", "Dan od 23 h");
        eq(ModeHolds.until(at(bg, 2026, 3, 29, 6, 0), bg), "20260330", "Zahtev na dan od 23 h");
        eq(ModeHolds.until(at(bg, 2026, 3, 29, 9, 59), bg), "20260330", "Zahtev u 09:59 na dan od 23 h");
        eq(ModeHolds.until(at(bg, 2026, 10, 24, 6, 0), bg), "20261025", "Dan od 25 h");
        eq(ModeHolds.until(at(bg, 2026, 10, 25, 6, 0), bg), "20261026", "Zahtev na dan od 25 h");
        eq(ModeHolds.until(at(bg, 2026, 10, 25, 9, 59), bg), "20261026", "Zahtev u 09:59 na dan od 25 h");
    }

    private static void carry() {
        int m630 = 6 * 60 + 30, tue = 1;
        Rule night = rule(23 * 60, 8 * 60);
        Rule c = ModeHolds.carry(night, m630, tue);
        check(c != null && c.days == 1 && c.active(m630, tue), "23–08 u 06:30: ostaje za ponedeljak");
        check(!c.active(m630, tue + 1), "Sledećeg jutra više ne traje");
        check(!c.active(23 * 60 + 30, tue), "Večeras ne počinje ponovo");
        check(ModeHolds.carry(rule(9 * 60, 17 * 60), m630, tue) == null, "09–17 u 06:30 ne traje");
        Rule early = ModeHolds.carry(rule(5 * 60, 9 * 60), m630, tue);
        check(early != null && early.days == 1 << tue, "05–09 u 06:30: isti dan");
        Rule off = rule(23 * 60, 8 * 60);
        off.enabled = false;
        check(ModeHolds.carry(off, m630, tue) == null, "Isključen režim ne traje");
        // Samo period koji je trajao u 05:59 (roll može da stigne kasno, kad se telefon probudi).
        check(ModeHolds.carry(rule(9 * 60, 17 * 60), 9 * 60 + 30, tue) == null, "Telefon spava do 09:30, 09–17: nema");
        check(ModeHolds.carry(rule(9 * 60, 17 * 60), 10 * 60, tue) == null, "09–17 u 10:00: nema");
        check(ModeHolds.carry(rule(6 * 60, 9 * 60), 6 * 60, tue) == null, "06–09 u 06:00: nema");
        check(ModeHolds.carry(rule(6 * 60, 9 * 60), 7 * 60, tue) == null, "06–09 u 07:00: nema");
        check(ModeHolds.carry(night, 9 * 60 + 30, tue) == null, "23–08 u 09:30: nema");
        Rule seven = ModeHolds.carry(night, 7 * 60, tue);
        check(seven != null && seven.days == 1, "23–08 u 07:00: ima, za ponedeljak");
        Rule longDay = ModeHolds.carry(rule(4 * 60, 2 * 60), 10 * 60, tue);
        check(longDay != null && longDay.days == 1 << tue, "04–02 u 10:00: isti period od 04:00");
        check(ModeHolds.carry(night, 2 * 60, tue) != null, "Pre 06:00 dovoljno je da traje");

        // Prošao zadržani deo: ne čeka ništa, osim ako ga drži živ snimak.
        check(!ModeHolds.expiredCarry(seven, new ArrayList<>(), new ArrayList<>(), 7 * 60, tue), "Dok traje, nije prošao");
        check(ModeHolds.expiredCarry(seven, new ArrayList<>(), new ArrayList<>(), 8 * 60, tue), "Posle 08:00 je prošao");
        check(!ModeHolds.expiredCarry(seven, new ArrayList<>(), Collections.singletonList(hold("20261118", night)), 8 * 60, tue),
                "Snimak ga drži: nije prošao");
        check(!ModeHolds.expiredCarry(night, new ArrayList<>(), new ArrayList<>(), 10 * 60, tue), "Ceo režim (svi dani) nije zadržan deo");
        // Pravi jednodnevni režim (subota 10–14) bez hold-ova, van perioda: podešeni ga pokriva, nije prošao carry.
        Rule saturday = rule(10 * 60, 14 * 60);
        saturday.days = 1 << 5;
        check(!ModeHolds.expiredCarry(saturday, Collections.singletonList(DailySchedule.copy(saturday)), new ArrayList<>(),
                16 * 60, 5), "Jednodnevni režim iz podešenog ostaje");
        check(!ModeHolds.expiredCarry(saturday, Collections.singletonList(DailySchedule.copy(saturday)), new ArrayList<>(),
                11 * 60, 1), "Jednodnevni režim van svog dana (utorak) ostaje");
        Rule wider = DailySchedule.copy(saturday);
        wider.apps.add("com.instagram.android");
        check(ModeHolds.expiredCarry(wider, Collections.singletonList(DailySchedule.copy(saturday)), new ArrayList<>(),
                16 * 60, 5), "Segment sa aplikacijom koje u podešenom nema");
    }

    private static void morningHold() {
        Rule old = rule(9 * 60, 17 * 60);
        Rule now = DailySchedule.copy(old);
        now.apps.remove(YT);
        now.sites.clear();
        List<ModeHolds.Hold> holds = Collections.singletonList(hold("20261117", old));
        List<Rule> desired = Collections.singletonList(now);
        // Dan zahteva u 06:00 (09–17 tada ne traje): zadržano staro pravilo i dalje blokira.
        List<Rule> d1 = m(desired, Collections.singletonList(old), holds, "20261116", 6 * 60, 1);
        check(DailySchedule.blockingApp(d1, YT, 10 * 60, 1) != null, "Danas je YouTube i dalje u režimu");
        check(ModeHolds.live(holds, "20261116").size() == 1, "Zadržano do sutra");
        // Sutra u 06:00: oslobođen.
        List<Rule> d2 = m(desired, d1, ModeHolds.live(holds, "20261117"), "20261117", 6 * 60, 2);
        check(DailySchedule.blockingApp(d2, YT, 10 * 60, 2) == null, "Sutra je YouTube slobodan");
        check(ModeHolds.live(holds, "20261117").isEmpty(), "Zadržano ističe na until");
        check(d2.size() == 1, "Ostaje samo podešen režim");

        // Obrisan režim ostaje do until.
        List<Rule> gone = m(new ArrayList<>(), Collections.singletonList(old), holds, "20261116", 6 * 60, 1);
        check(DailySchedule.blockingApp(gone, YT, 10 * 60, 1) != null, "Obrisan režim važi do sutra");
    }

    private static void morningOverSix() {
        Rule old = rule(23 * 60, 8 * 60);
        Rule now = DailySchedule.copy(old);
        now.apps.remove(YT);
        // Hold je već istekao (until = danas): ostaje samo deo koji upravo traje.
        List<ModeHolds.Hold> holds = Collections.singletonList(hold("20261117", old));
        int tue = 1, m630 = 6 * 60 + 30;
        List<Rule> d = m(Collections.singletonList(now), Collections.singletonList(old), holds,
                "20261117", m630, tue);
        check(DailySchedule.blockingApp(d, YT, 7 * 60 + 59, tue) != null, "23–08: do 08:00 i dalje blokira");
        check(DailySchedule.blockingApp(d, YT, 8 * 60, tue) == null, "Od 08:00 slobodno");
        check(DailySchedule.blockingApp(d, YT, 23 * 60 + 30, tue) == null, "Večeras slobodno");
        check(DailySchedule.blockingApp(d, YT, m630, tue + 1) == null, "Sutra u 06:30 slobodno");
        List<Rule> next = m(Collections.singletonList(now), d, ModeHolds.live(holds, "20261118"),
                "20261118", m630, tue + 1);
        check(DailySchedule.blockingApp(next, YT, m630, tue + 1) == null, "Sledećeg jutra ga nema");
        check(next.size() == 1, "Sledećeg jutra samo podešen režim");
        check(next.get(0).apps.isEmpty(), "Podešen režim je bez YouTube-a");
        // Ostale aplikacije i dalje u režimu.
        Rule both = rule(23 * 60, 8 * 60);
        both.apps.add("com.instagram.android");
        Rule left = DailySchedule.copy(both);
        left.apps.remove(YT);
        List<Rule> m = m(Arrays.asList(left), Arrays.asList(both), new ArrayList<>(), "20261117", m630, tue);
        check(DailySchedule.blockingApp(m, "com.instagram.android", 23 * 60 + 30, tue) != null, "Ostatak režima važi večeras");
    }

    private static EnforcedRules er(Map<String, AppRule> apps, Map<String, Integer> sites, List<Rule> schedules,
                                    boolean protect, Set<String> free, Set<String> links) {
        return new EnforcedRules(apps, sites, schedules, false, protect, true, free, free, links);
    }

    private static EnforcedRules lightOf(EnforcedRules r) {
        return new EnforcedRules(r.apps, r.sites, r.schedules, r.night, r.protect, false, r.nightFree, r.dayFree, r.links);
    }

    private static EnforcedRules er(List<Rule> schedules) {
        return er(new HashMap<>(), new HashMap<>(), schedules, false, new HashSet<>(), new HashSet<>());
    }

    private static ModeHolds.Hold hold(String until, Rule r) {
        return new ModeHolds.Hold(until, er(Collections.singletonList(r)));
    }

    private static List<Rule> m(List<Rule> desired, List<Rule> old, List<ModeHolds.Hold> holds, String day, int min, int wd) {
        return ModeHolds.morning(er(desired), old, holds, day, min, wd).schedules;
    }

    private static Map<String, AppRule> app(AppRule r) {
        Map<String, AppRule> out = new HashMap<>();
        if (r != null) out.put(YT, r);
        return out;
    }

    private static Map<String, Integer> site(Integer limit) {
        Map<String, Integer> out = new HashMap<>();
        if (limit != null) out.put("youtube.com", limit);
        return out;
    }

    private static Set<String> set(String... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    private static void loosensAll() {
        List<Rule> none = new ArrayList<>();
        AppRule lock = new AppRule(true, 0, 0, 0), limit30 = new AppRule(false, 30, 0, 0);
        EnforcedRules base = er(app(new AppRule(true, 30, 5, 20)), site(30), none, true, set("a"), set("p|d"));
        check(!EnforcedRules.loosens(base, base), "Bez izmene nije popuštanje");
        check(EnforcedRules.loosens(base, er(app(new AppRule(false, 30, 5, 20)), site(30), none, true, set("a"), set("p|d"))),
                "Skinuto zaključavanje");
        check(EnforcedRules.loosens(base, er(app(new AppRule(true, 60, 5, 20)), site(30), none, true, set("a"), set("p|d"))),
                "Veći limit");
        check(!EnforcedRules.loosens(base, er(app(new AppRule(true, 15, 5, 20)), site(30), none, true, set("a"), set("p|d"))),
                "Manji limit je pooštravanje");
        check(EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 0, 20)), site(30), none, true, set("a"), set("p|d"))),
                "Bez ograničenja otvaranja");
        check(EnforcedRules.loosens(base, er(app(null), site(30), none, true, set("a"), set("p|d"))), "Aplikacija skinuta");
        check(EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 5, 20)), site(null), none, true, set("a"), set("p|d"))),
                "Sajt skinut");
        check(EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 5, 20)), site(60), none, true, set("a"), set("p|d"))),
                "Veći limit sajta");
        check(!EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 5, 20)), site(0), none, true, set("a"), set("p|d"))),
                "Blokiran sajt je pooštravanje");
        check(EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 5, 20)), site(30), none, false, set("a"), set("p|d"))),
                "Zaštita isključena");
        check(EnforcedRules.loosens(base, lightOf(base)), "Laki režim");
        check(!EnforcedRules.loosens(lightOf(base), base), "Povratak na pun rad je pooštravanje");
        check(EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 5, 20)), site(30), none, true, set("a", "b"), set("p|d"))),
                "Nov izuzetak");
        check(!EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 5, 20)), site(30), none, true, set(), set("p|d"))),
                "Skinut izuzetak je pooštravanje");
        check(EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 5, 20)), site(30), none, true, set("a"), set())),
                "Razdvojen par");
        check(!EnforcedRules.loosens(base, er(app(new AppRule(true, 30, 5, 20)), site(30), none, true, set("a"), set("p|d", "q|e"))),
                "Povezivanje je pooštravanje");
        check(!EnforcedRules.loosens(er(app(null), site(null), none, false, set(), set()), er(app(lock), site(0), none, true, set(), set())),
                "Novo pravilo nije popuštanje");
        check(EnforcedRules.loosens(er(app(lock), site(null), none, false, set(), set()), er(app(limit30), site(null), none, false, set(), set())),
                "Zaključavanje zamenjeno limitom");
        Rule r = rule(9 * 60, 17 * 60);
        Rule shorter = DailySchedule.copy(r);
        shorter.end = 16 * 60;
        check(EnforcedRules.loosens(er(Collections.singletonList(r)), er(Collections.singletonList(shorter))), "Kraći režim");
        check(EnforcedRules.loosens(er(Collections.singletonList(r)), er(none)), "Obrisan režim");
        check(!EnforcedRules.loosens(er(none), er(Collections.singletonList(r))), "Nov režim");
    }

    private static void morningAll() {
        AppRule lock = new AppRule(true, 0, 0, 0);
        List<Rule> none = new ArrayList<>();
        EnforcedRules old = er(app(lock), site(0), none, true, set(), set("p|d"));
        EnforcedRules desired = er(app(null), site(null), none, false, set("a"), set());
        List<ModeHolds.Hold> holds = Collections.singletonList(new ModeHolds.Hold("20261117", old));
        EnforcedRules d1 = ModeHolds.morning(desired, none, holds, "20261116", 6 * 60, 1);
        check(d1.apps.get(YT) != null && d1.apps.get(YT).lock, "Danas je i dalje zaključana");
        check(Integer.valueOf(0).equals(d1.sites.get("youtube.com")), "Danas je sajt i dalje blokiran");
        check(d1.protect && d1.dayFree.isEmpty() && d1.links.contains("p|d"), "Sutra zaštita, bez izuzetka, par ostaje");
        EnforcedRules d2 = ModeHolds.morning(desired, none, holds, "20261117", 6 * 60, 2);
        check(d2.apps.isEmpty() && d2.sites.isEmpty() && !d2.protect, "Sutra važi podešeno");
        check(d2.dayFree.contains("a") && d2.links.isEmpty(), "Sutra izuzetak i razdvajanje");
        check(ModeHolds.morning(desired, none, new ArrayList<>(), "20261116", 6 * 60, 1) == desired, "Bez zadržanog: podešeno");
        // Izuzetak od ukupnog limita koji je odmah važio (freeNow) ostaje i sledećeg jutra.
        EnforcedRules before = er(app(null), site(null), none, false, set(), set());
        EnforcedRules wanted = er(app(null), site(null), none, false, set("a"), set());
        List<ModeHolds.Hold> fh = Collections.singletonList(new ModeHolds.Hold("20261117", ModeHolds.withDayFree(before, "a")));
        check(ModeHolds.morning(wanted, none, fh, "20261116", 6 * 60, 1).dayFree.contains("a"), "freeNow ostaje sutra");
        check(!before.dayFree.contains("a"), "withDayFree ne menja ulaz");
        // Laki režim: danas pun rad iz zadržanog, sutra laki; withDayFree i carry ga ne menjaju.
        List<ModeHolds.Hold> lh = Collections.singletonList(new ModeHolds.Hold("20261117", before));
        check(ModeHolds.morning(lightOf(before), none, lh, "20261116", 6 * 60, 1).full, "Laki čeka sutra");
        check(!ModeHolds.morning(lightOf(before), none, lh, "20261117", 6 * 60, 2).full, "Sutra laki");
        check(!ModeHolds.withDayFree(lightOf(before), "a").full, "withDayFree čuva laki");
        Rule night = rule(23 * 60, 8 * 60);
        check(!ModeHolds.morning(lightOf(before), Collections.singletonList(night), new ArrayList<>(), "20261117",
                6 * 60 + 30, 2).full, "Zadržan režim čuva laki");
    }

    private static void eq(String got, String want, String what) {
        check(want.equals(got), what + ": " + got + " umesto " + want);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
