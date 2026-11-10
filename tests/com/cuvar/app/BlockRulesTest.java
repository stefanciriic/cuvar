package com.cuvar.app;

import com.cuvar.app.rules.BlockKind;
import com.cuvar.app.rules.BlockKind.Block;
import com.cuvar.app.rules.BlockRules;
import com.cuvar.app.rules.DailySchedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Koja blokada važi za aplikaciju napred i za druge prozore, i kojim redom. */
public final class BlockRulesTest {
    private static int checks;

    /** Izmišljeno stanje pravila. */
    static final class Fake implements BlockRules.Facts {
        Set<String> exempt = new HashSet<>(Collections.singletonList("com.dialer"));
        Set<String> guarded = new HashSet<>();
        boolean dayReached, night, focus, hasSiteRules, hardSite;
        Set<String> nightFree = new HashSet<>();
        Set<String> focusApps = new HashSet<>();
        Set<String> focusSites = new HashSet<>();
        Set<String> onBreak = new HashSet<>();
        Set<String> locked = new HashSet<>();
        Set<String> unknownBrowsers = new HashSet<>();
        Set<String> cloners = new HashSet<>();
        Map<String, Integer> appLimits = new HashMap<>();
        Map<String, Long> usedApps = new HashMap<>();
        Map<String, Integer> siteLimits = new HashMap<>(); // domen -> 0 (uvek) ili minuti
        Map<String, Long> usedSites = new HashMap<>();
        List<DailySchedule.Rule> activeRules = new ArrayList<>();
        List<DailySchedule.Rule> codeRules = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        @Override public boolean exempt(String pkg) { return exempt.contains(pkg); }
        @Override public boolean guarded(String pkg) { return guarded.contains(pkg); }
        @Override public boolean dayLimitReached() { return dayReached; }
        @Override public boolean nightActive() { return night; }
        @Override public boolean nightBlocksApp(String pkg) { return night && guarded.contains(pkg) && !nightFree.contains(pkg); }
        @Override public String siteGuarded(String host) { return DailySchedule.matchDomain(host, siteLimits.keySet()); }
        @Override public boolean focusActive() { return focus; }
        @Override public boolean focusAllowsApp(String pkg) { return focus && focusApps.contains(pkg); }
        @Override public boolean focusAllowsSite(String host) { return focus && DailySchedule.matchDomain(host, focusSites) != null; }
        @Override public boolean onBreak(String pkg) { return onBreak.contains(pkg); }
        @Override public DailySchedule.Rule scheduleApp(String pkg) {
            for (DailySchedule.Rule r : activeRules) if (r.apps.contains(pkg)) return r;
            return null;
        }
        @Override public DailySchedule.Rule scheduleSite(String host) {
            for (DailySchedule.Rule r : activeRules) if (DailySchedule.matchDomain(host, r.sites) != null) return r;
            return null;
        }
        @Override public DailySchedule.Rule codeApp(String pkg) { return DailySchedule.codeApp(codeRules, pkg); }
        @Override public DailySchedule.Rule codeSite(String host) { return DailySchedule.codeSite(codeRules, host); }
        @Override public boolean appLock(String pkg) { return locked.contains(pkg); }
        @Override public int appLimit(String pkg) { return appLimits.getOrDefault(pkg, 0); }
        @Override public long usedApp(String pkg) { return usedApps.getOrDefault(pkg, 0L); }
        @Override public List<String> matchingSites(String host) {
            List<String> out = new ArrayList<>();
            if (host == null) return out;
            for (String d : siteLimits.keySet()) if (host.equals(d) || host.endsWith("." + d)) out.add(d);
            Collections.sort(out);
            return out;
        }
        @Override public int siteLimit(String domain) { return siteLimits.getOrDefault(domain, -1); }
        @Override public long usedSite(String domain) { return usedSites.getOrDefault(domain, 0L); }
        @Override public boolean unknownBrowser(String pkg) { return unknownBrowsers.contains(pkg); }
        @Override public boolean cloner(String pkg) { return cloners.contains(pkg); }
        @Override public boolean hasSiteRules() { return hasSiteRules; }
        @Override public boolean hardSiteBlock() { return hardSite; }
        @Override public void warnApp(String pkg, int limit, long used) { warnings.add("app:" + pkg); }
        @Override public void warnSite(String domain, int limit, long used) { warnings.add("site:" + domain); }
    }

    public static void main(String[] args) {
        nothing();
        exemptNeverBlocked();
        priority();
        appRules();
        schedules();
        sites();
        unseenBrowsers();
        focus();
        windows();
        picking();
        System.out.println("BlockRulesTest: " + checks + " provera prošlo.");
    }

    private static BlockRules.Front app(String pkg) {
        return new BlockRules.Front(pkg, null, false, false, false, false);
    }

    private static BlockRules.Front browser(String pkg, String host) {
        return new BlockRules.Front(pkg, host, true, false, false, false);
    }

    private static void nothing() {
        Fake f = new Fake();
        check(BlockRules.front(f, app("com.game")).isEmpty(), "Bez pravila nema blokade");
        check(BlockRules.front(f, browser("com.android.chrome", "example.com")).isEmpty(), "Sajt bez pravila");
        check(BlockRules.windows(f, Collections.singletonList(new BlockRules.Window("com.game", null, false, false)), k -> false).isEmpty(),
                "Drugi prozor bez pravila");
        check(f.warnings.isEmpty(), "Bez upozorenja");
    }

    /** Telefon se nikad ne blokira dnevnim limitom, noću ni fokusom. */
    private static void exemptNeverBlocked() {
        Fake f = new Fake();
        f.guarded.add("com.dialer");
        f.dayReached = true;
        f.night = true;
        f.focus = true;
        check(BlockRules.front(f, app("com.dialer")).isEmpty(), "Telefon napred");
        check(BlockRules.appOnly(f, "com.dialer", k -> false) == null, "Telefon u drugom prozoru");
        check(BlockRules.windows(f, Collections.singletonList(new BlockRules.Window("com.dialer", null, false, false)), k -> false).isEmpty(),
                "Telefon bez fokus blokade u drugom prozoru");
    }

    /** Redosled: dnevni limit, noć, fokus, otvaranja, pauza, režim, pa zaključavanje. */
    private static void priority() {
        Fake f = new Fake();
        f.guarded.add("com.game");
        f.dayReached = true;
        f.night = true;
        f.focus = true;
        f.onBreak.add("com.game");
        f.locked.add("com.game");
        DailySchedule.Rule rule = rule("r", "com.game");
        f.activeRules.add(rule);
        List<Block> b = BlockRules.front(f, new BlockRules.Front("com.game", null, false, false, true, false));
        check(kinds(b).equals(Arrays.asList(BlockKind.DAY, BlockKind.NIGHT, BlockKind.FOCUS, BlockKind.OPENS,
                BlockKind.BREAK, BlockKind.SCHEDULE)), "Redosled blokada: " + kinds(b));
        check(b.get(5).rule == rule, "Režim se prikazuje uz blokadu");

        f.nightFree.add("com.game");
        check(!kinds(BlockRules.front(f, app("com.game"))).contains(BlockKind.NIGHT), "Aplikacija bez noćne blokade");
        f.guarded.add("com.android.chrome");
        f.nightFree.add("com.android.chrome");
        f.siteLimits.put("youtube.com", 30);
        check(kinds(BlockRules.front(f, browser("com.android.chrome", "youtube.com"))).contains(BlockKind.NIGHT),
                "Pregledač bez noćne blokade ne otvara sajt iz pravila noću");
        f.guarded.remove("com.game");
        List<Integer> k = kinds(BlockRules.front(f, app("com.game")));
        check(!k.contains(BlockKind.DAY) && !k.contains(BlockKind.NIGHT), "Dnevni limit i noć samo za aplikacije pod pravilima");
    }

    private static void appRules() {
        Fake f = new Fake();
        f.locked.add("com.game");
        check(only(BlockRules.front(f, app("com.game")), BlockKind.LOCK, "app:com.game", true), "Zaključana aplikacija");

        f.appLimits.put("com.game", 30);
        f.usedApps.put("com.game", 10 * 60000L);
        check(only(BlockRules.front(f, app("com.game")), BlockKind.LOCK, "app:com.game", true), "Limit nije potrošen");
        check(f.warnings.equals(Collections.singletonList("app:com.game")), "Upozorenje pred limit");

        f.warnings.clear();
        f.usedApps.put("com.game", 30 * 60000L);
        check(only(BlockRules.front(f, app("com.game")), BlockKind.TIME, "app:com.game", true), "Potrošen limit je tvrd");
        check(f.warnings.isEmpty(), "Bez upozorenja posle limita");

        Fake c = new Fake();
        c.codeRules.add(codeRule("com.chat", "x.com"));
        check(only(BlockRules.front(c, app("com.chat")), BlockKind.CODE, "app:com.chat", true), "Van režima traži šifru");

        Fake k = new Fake();
        k.cloners.add("com.clone");
        check(only(BlockRules.front(k, app("com.clone")), BlockKind.CLONER, "app:com.clone", true), "Aplikacija za kloniranje");
    }

    /** Aktivan režim zamenjuje zaključavanje i limit aplikacije (i ne upozorava). */
    private static void schedules() {
        Fake f = new Fake();
        f.locked.add("com.game");
        f.appLimits.put("com.game", 30);
        f.codeRules.add(codeRule("com.game", null));
        f.activeRules.add(rule("r", "com.game"));
        check(kinds(BlockRules.front(f, app("com.game"))).equals(Collections.singletonList(BlockKind.SCHEDULE)), "Samo režim");
        check(f.warnings.isEmpty(), "Režim bez upozorenja za limit");

        Fake s = new Fake();
        DailySchedule.Rule r = rule("r", null);
        r.sites.add("youtube.com");
        s.activeRules.add(r);
        s.codeRules.add(codeRule(null, "youtube.com"));
        List<Block> b = BlockRules.front(s, browser("com.android.chrome", "m.youtube.com"));
        check(b.size() == 1 && b.get(0).kind == BlockKind.SCHEDULE && b.get(0).key.equals("site:youtube.com"),
                "Režim sajta, bez šifre dok traje");
        s.activeRules.clear();
        b = BlockRules.front(s, browser("com.android.chrome", "m.youtube.com"));
        check(only(b, BlockKind.CODE, "site:youtube.com", true), "Van režima sajt traži šifru");
    }

    private static void sites() {
        Fake f = new Fake();
        f.siteLimits.put("youtube.com", 0);
        f.siteLimits.put("reddit.com", 20);
        check(only(BlockRules.front(f, browser("com.android.chrome", "www.youtube.com")), BlockKind.SITE, "site:youtube.com", true),
                "Uvek blokiran sajt");
        check(BlockRules.front(f, browser("com.android.chrome", "reddit.com")).isEmpty(), "Sajt sa limitom");
        check(f.warnings.equals(Collections.singletonList("site:reddit.com")), "Upozorenje za sajt");
        f.usedSites.put("reddit.com", 20 * 60000L);
        check(only(BlockRules.front(f, browser("com.android.chrome", "old.reddit.com")), BlockKind.SITE_TIME, "site:reddit.com", true),
                "Potrošen limit sajta");
        check(BlockRules.front(f, new BlockRules.Front("com.game", "reddit.com", false, false, false, false)).isEmpty(),
                "Bez pregledača nema pravila sajta");

        Fake d = new Fake();
        d.siteLimits.put("youtube.com", 60);
        d.dayReached = true;
        d.night = true;
        List<Block> b = BlockRules.front(d, browser("com.android.chrome", "youtube.com"));
        check(kinds(b).equals(Arrays.asList(BlockKind.DAY, BlockKind.NIGHT)) && b.get(0).key.equals("site:youtube.com"),
                "Dnevni limit i noć za sajt pod pravilom: " + kinds(b));
        check(BlockRules.front(d, new BlockRules.Front("com.android.chrome", null, true, false, false, false)).isEmpty(),
                "Bez vidljive adrese nema blokade sajta");
    }

    private static void unseenBrowsers() {
        Fake f = new Fake();
        f.hasSiteRules = true;
        check(only(BlockRules.front(f, new BlockRules.Front("com.insta", null, true, true, false, false)),
                BlockKind.INAPP, "app:com.insta", true), "Pregledač u aplikaciji bez adrese");
        check(BlockRules.front(f, new BlockRules.Front("com.insta", "example.com", true, true, false, false)).isEmpty(),
                "Pregledač u aplikaciji sa adresom");
        check(only(BlockRules.front(f, new BlockRules.Front("com.android.chrome", null, true, false, false, true)),
                BlockKind.ADDRESS, "app:com.android.chrome", true), "Adresa još nije potvrđena");
        f.unknownBrowsers.add("org.unknown");
        check(only(BlockRules.front(f, app("org.unknown")), BlockKind.BROWSER, "app:org.unknown", true), "Nepoznat pregledač");
        f.hardSite = true;
        check(only(BlockRules.front(f, app("org.unknown")), BlockKind.UNSEEN_SITE, "app:org.unknown", true),
                "Dok je sajt tvrdo blokiran, nepoznat pregledač se ne otključava");
        f.hasSiteRules = false;
        check(BlockRules.front(f, new BlockRules.Front("com.insta", null, true, true, false, true)).isEmpty(),
                "Bez pravila za sajtove adresa nije bitna");
    }

    private static void focus() {
        Fake f = new Fake();
        f.focus = true;
        f.focusApps.add("com.notes");
        f.focusSites.add("docs.google.com");
        check(BlockRules.front(f, app("com.notes")).isEmpty(), "Dozvoljena aplikacija u fokusu");
        check(only(BlockRules.front(f, app("com.game")), BlockKind.FOCUS, "app:com.game", false), "Ostale aplikacije u fokusu");
        check(BlockRules.front(f, browser("com.android.chrome", "docs.google.com")).isEmpty(), "Dozvoljen sajt u fokusu");
        check(only(BlockRules.front(f, browser("com.android.chrome", "youtube.com")), BlockKind.FOCUS, "app:com.android.chrome", false),
                "Drugi sajt u fokusu");
        check(only(BlockRules.front(f, new BlockRules.Front("com.android.chrome", "docs.google.com", false, false, false, false)),
                BlockKind.FOCUS, "app:com.android.chrome", false), "Sajt se gleda samo u pregledaču");
    }

    private static void windows() {
        Fake f = new Fake();
        f.guarded.add("com.game");
        f.dayReached = true;
        f.locked.add("com.game");
        check(only(BlockRules.windows(f, Collections.singletonList(new BlockRules.Window("com.game", null, false, false)), k -> true),
                BlockKind.DAY, "app:com.game", false), "Drugi prozor: prva po redu, tvrda važi i otključana");

        Fake l = new Fake();
        l.locked.add("com.game");
        check(BlockRules.appOnly(l, "com.game", k -> k.equals("app:com.game")) == null, "Otključan drugi prozor");
        check(BlockRules.appOnly(l, "com.game", k -> false).kind == BlockKind.LOCK, "Zaključan drugi prozor");

        Fake s = new Fake();
        s.siteLimits.put("youtube.com", 60);
        s.night = true;
        s.hasSiteRules = true;
        s.focus = true;
        s.focusApps.add("com.android.chrome");
        List<Block> b = BlockRules.windows(s, Arrays.asList(
                new BlockRules.Window("com.android.chrome", "youtube.com", false, false),
                new BlockRules.Window("org.browser", null, true, false),
                new BlockRules.Window("com.insta", null, false, true)), k -> false);
        check(b.size() == 5, "Blokade drugih prozora: " + b.size());
        check(b.get(0).key.equals("site:youtube.com") && b.get(0).kind == BlockKind.NIGHT, "Sajt noću u drugom prozoru");
        check(b.get(1).key.equals("app:org.browser") && b.get(1).kind == BlockKind.ADDRESS, "Nepotvrđena adresa u drugom prozoru");
        check(b.get(2).key.equals("app:org.browser") && b.get(2).kind == BlockKind.FOCUS, "Fokus u drugom prozoru");
        check(b.get(3).key.equals("app:com.insta") && b.get(3).kind == BlockKind.INAPP, "Pregledač u aplikaciji u drugom prozoru");
        check(b.get(4).key.equals("app:com.insta") && b.get(4).kind == BlockKind.FOCUS, "Fokus za aplikaciju u drugom prozoru");
        check(s.warnings.isEmpty(), "Drugi prozori ne upozoravaju");
    }

    /** Otključan pregledač ne otvara blokiran sajt; tvrda blokada ne zavisi od otključavanja. */
    private static void picking() {
        Fake f = new Fake();
        f.unknownBrowsers.add("org.unknown");
        f.codeRules.add(codeRule(null, "youtube.com"));
        List<Block> b = BlockRules.front(f, browser("org.unknown", "youtube.com"));
        Block shown = BlockKind.pick(b, k -> k.equals("app:org.unknown"));
        check(shown != null && shown.key.equals("site:youtube.com"), "Otključan pregledač, sajt i dalje traži šifru");
        check(BlockKind.pick(b, k -> true) == null, "Sve otključano");

        Fake t = new Fake();
        t.appLimits.put("com.game", 10);
        t.usedApps.put("com.game", 11 * 60000L);
        check(BlockKind.pick(BlockRules.front(t, app("com.game")), k -> true).kind == BlockKind.TIME, "Potrošen limit se ne otključava");
    }

    private static DailySchedule.Rule rule(String id, String pkg) {
        DailySchedule.Rule r = new DailySchedule.Rule(id, id, true, 0, 1439);
        if (pkg != null) r.apps.add(pkg);
        return r;
    }

    private static DailySchedule.Rule codeRule(String pkg, String site) {
        DailySchedule.Rule r = new DailySchedule.Rule("c", "c", true, 60, 120);
        r.code = true;
        if (pkg != null) r.apps.add(pkg);
        if (site != null) r.sites.add(site);
        return r;
    }

    private static List<Integer> kinds(List<Block> blocks) {
        List<Integer> out = new ArrayList<>();
        for (Block b : blocks) out.add(b.kind);
        return out;
    }

    private static boolean only(List<Block> b, int kind, String key, boolean code) {
        return b.size() == 1 && b.get(0).kind == kind && b.get(0).key.equals(key) && b.get(0).code == code;
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
    }
}
