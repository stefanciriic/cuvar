package com.cuvar.app.rules;

import com.cuvar.app.rules.BlockKind.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Koje blokade sada važe za aplikaciju napred i za ostale prozore na ekranu.
 * Redosled u listi je bitan: BlockKind.pick prikazuje prvu blokadu koja važi.
 */
public final class BlockRules {
    private BlockRules() {
    }

    /** Stanje pravila koje se čita iz Store-a i vrste aplikacije; u testu se podmeće. */
    public interface Facts {
        /** Telefon, SMS, početni ekran: nikad se ne blokiraju. */
        boolean exempt(String pkg);
        /** Aplikacija pod pravilima (za dnevni limit i noć). */
        boolean guarded(String pkg);
        boolean dayLimitReached();
        /** Aplikacija van ukupnog dnevnog limita (sajt sa pravilom u njoj i dalje ga poštuje). */
        boolean dayFree(String pkg);
        boolean nightActive();
        boolean nightBlocksApp(String pkg);
        /** Domen sa pravilom koji pokriva host, ili null. */
        String siteGuarded(String host);
        boolean focusActive();
        boolean focusAllowsApp(String pkg);
        boolean focusAllowsSite(String host);
        boolean onBreak(String pkg);
        DailySchedule.Rule scheduleApp(String pkg);
        DailySchedule.Rule scheduleSite(String host);
        DailySchedule.Rule codeApp(String pkg);
        DailySchedule.Rule codeSite(String host);
        boolean appLock(String pkg);
        int appLimit(String pkg);
        long usedApp(String pkg);
        List<String> matchingSites(String host);
        int siteLimit(String domain);
        long usedSite(String domain);
        boolean unknownBrowser(String pkg);
        boolean cloner(String pkg);
        boolean hasSiteRules();
        /** Neki sajt je sada blokiran bez otključavanja (vidi BlockKind.unseenSite). */
        boolean hardSiteBlock();
        /** Upozorenje pred istek limita (može da prikaže poruku). */
        void warnApp(String pkg, int limit, long used);
        void warnSite(String domain, int limit, long used);
    }

    /** Aplikacija napred i šta se zna o sajtu u njoj. */
    public static final class Front {
        public final String pkg;
        public final String host;          // sajt koji se vidi, ili null
        public final boolean browsing;     // pregledač, pregledač u aplikaciji ili WebAPK
        public final boolean embedded;     // pregledač u aplikaciji ili WebAPK (adresa se ne čita iz trake)
        public final boolean opensBlocked; // potrošena otvaranja za danas
        public final boolean addressUnresolved;

        public Front(String pkg, String host, boolean browsing, boolean embedded, boolean opensBlocked, boolean addressUnresolved) {
            this.pkg = pkg;
            this.host = host;
            this.browsing = browsing;
            this.embedded = embedded;
            this.opensBlocked = opensBlocked;
            this.addressUnresolved = addressUnresolved;
        }
    }

    /** Drugi prozor na ekranu (podeljen ekran, plutajući prozor). */
    public static final class Window {
        public final String pkg;
        public final String host;            // pročitan sajt, ili null
        public final boolean addressUnknown; // pregledač čija adresa još nije potvrđena
        public final boolean embeddedUnknown; // pregledač u aplikaciji bez vidljive adrese

        public Window(String pkg, String host, boolean addressUnknown, boolean embeddedUnknown) {
            this.pkg = pkg;
            this.host = host;
            this.addressUnknown = addressUnknown;
            this.embeddedUnknown = embeddedUnknown;
        }
    }

    /**
     * Sva pravila za aplikaciju napred, od najstrožeg: ukupni dnevni limit, noć, fokus, otvaranja, pauza,
     * vremenski režim, pa aplikacija i sajt.
     */
    public static List<Block> front(Facts f, Front s) {
        List<Block> out = new ArrayList<>();
        String pkg = s.pkg;
        String appKey = ItemKey.app(pkg);
        boolean web = s.browsing && s.host != null;
        boolean exempt = f.exempt(pkg);
        if (f.dayLimitReached() && !exempt) {
            String site = web ? f.siteGuarded(s.host) : null;
            if (f.guarded(pkg) && !f.dayFree(pkg)) {
                out.add(new Block(appKey, BlockKind.DAY, null, false));
            } else if (site != null) {
                out.add(new Block(ItemKey.site(site), BlockKind.DAY, null, false));
            }
        }
        if (f.nightActive() && !exempt) {
            String site = web ? f.siteGuarded(s.host) : null;
            // Pregledač bez noćne blokade ne oslobađa sajtove iz pravila koji se u njemu otvaraju.
            if (f.guarded(pkg) && f.nightBlocksApp(pkg)) {
                out.add(new Block(appKey, BlockKind.NIGHT, null, false));
            } else if (site != null) {
                out.add(new Block(ItemKey.site(site), BlockKind.NIGHT, null, false));
            }
        }
        Block focus = focus(f, pkg, web ? s.host : null);
        if (focus != null) out.add(focus);
        if (s.opensBlocked) out.add(new Block(appKey, BlockKind.OPENS, null, false));
        if (f.onBreak(pkg)) out.add(new Block(appKey, BlockKind.BREAK, null, false));
        DailySchedule.Rule rule = f.scheduleApp(pkg);
        if (rule != null) out.add(new Block(appKey, BlockKind.SCHEDULE, rule, false));
        DailySchedule.Rule siteRule = web ? f.scheduleSite(s.host) : null;
        if (siteRule != null) {
            out.add(new Block(ItemKey.site(DailySchedule.matchDomain(s.host, siteRule.sites)), BlockKind.SCHEDULE, siteRule, false));
        }
        if (rule == null) {
            int limit = f.appLimit(pkg);
            boolean timeUp = limit > 0 && f.usedApp(pkg) >= limit * 60000L;
            if (limit > 0 && !timeUp) f.warnApp(pkg, limit, f.usedApp(pkg));
            // Van perioda režim sa dnevnom šifrom drži svoje aplikacije zaključane, a umesto PIN-a traži šifru.
            DailySchedule.Rule codeRule = f.codeApp(pkg);
            if (f.appLock(pkg) || timeUp || codeRule != null) {
                out.add(new Block(appKey, timeUp ? BlockKind.TIME : codeRule != null ? BlockKind.CODE : BlockKind.LOCK, null, true));
            }
        }
        if (f.unknownBrowser(pkg)) out.add(new Block(appKey, unseen(f, BlockKind.BROWSER), null, true));
        if (f.cloner(pkg)) out.add(new Block(appKey, BlockKind.CLONER, null, true));
        if (web && siteRule == null) {
            DailySchedule.Rule codeRule = f.codeSite(s.host);
            if (codeRule != null) {
                out.add(new Block(ItemKey.site(DailySchedule.matchDomain(s.host, codeRule.sites)), BlockKind.CODE, null, true));
            }
        }
        if (s.embedded && s.host == null && f.hasSiteRules()) {
            // Adresa se ne vidi, a postoje pravila za sajtove: bez ovoga bi se blokiran sajt otvorio preko linka u aplikaciji.
            out.add(new Block(appKey, unseen(f, BlockKind.INAPP), null, true));
        }
        if (s.addressUnresolved && f.hasSiteRules()) out.add(new Block(appKey, unseen(f, BlockKind.ADDRESS), null, true));
        if (s.browsing) siteLimits(f, out, s.host, true);
        return out;
    }

    /** Blokade ostalih prozora na ekranu; svaki se proverava i kad druga blokada već pobeđuje. */
    public static List<Block> windows(Facts f, List<Window> windows, Predicate<String> unlocked) {
        List<Block> out = new ArrayList<>();
        for (Window w : windows) {
            Block b = appOnly(f, w.pkg, unlocked);
            if (b != null) out.add(b);
            String appKey = ItemKey.app(w.pkg);
            if (w.addressUnknown && f.hasSiteRules()) out.add(new Block(appKey, unseen(f, BlockKind.ADDRESS), null, true));
            if (w.embeddedUnknown && f.hasSiteRules()) out.add(new Block(appKey, unseen(f, BlockKind.INAPP), null, true));
            Block focus = focus(f, w.pkg, w.host);
            if (focus != null) out.add(focus);
            if (w.host != null) windowSite(f, out, w.pkg, w.host);
        }
        return out;
    }

    /**
     * Blokada aplikacije koja je na ekranu, ali nije u fokusu (bez sajtova i broja otvaranja), ili null.
     * Prva po redu; otključana stavka ne blokira, osim tvrdom blokadom.
     */
    public static Block appOnly(Facts f, String p, Predicate<String> unlocked) {
        if (f.exempt(p)) return null;
        String k = ItemKey.app(p);
        Block b = null;
        if (f.guarded(p) && f.dayLimitReached() && !f.dayFree(p)) {
            b = new Block(k, BlockKind.DAY, null, false);
        } else if (f.guarded(p) && f.nightBlocksApp(p)) {
            b = new Block(k, BlockKind.NIGHT, null, false);
        } else if (f.onBreak(p)) {
            b = new Block(k, BlockKind.BREAK, null, false);
        } else {
            DailySchedule.Rule rule = f.scheduleApp(p);
            if (rule != null) {
                b = new Block(k, BlockKind.SCHEDULE, rule, false);
            } else {
                int limit = f.appLimit(p);
                boolean timeUp = limit > 0 && f.usedApp(p) >= limit * 60000L;
                DailySchedule.Rule codeRule = f.codeApp(p);
                if (f.appLock(p) || timeUp || codeRule != null) {
                    b = new Block(k, timeUp ? BlockKind.TIME : codeRule != null ? BlockKind.CODE : BlockKind.LOCK, null, true);
                } else if (f.unknownBrowser(p)) {
                    b = new Block(k, unseen(f, BlockKind.BROWSER), null, true);
                } else if (f.cloner(p)) {
                    b = new Block(k, BlockKind.CLONER, null, true);
                }
            }
        }
        if (b != null && !BlockKind.hard(b.kind) && unlocked.test(k)) return null;
        return b;
    }

    /** Fokus blokira sve osim izabranih aplikacija i sajtova; host je null kad se sajt ne vidi. */
    static Block focus(Facts f, String pkg, String host) {
        if (!f.focusActive() || f.exempt(pkg) || f.focusAllowsApp(pkg)) return null;
        if (host != null && f.focusAllowsSite(host)) return null;
        return new Block(ItemKey.app(pkg), BlockKind.FOCUS, null, false);
    }

    /** Sajt u drugom prozoru: dnevni limit, noć, režim ili šifra, pa limit sajta. */
    static void windowSite(Facts f, List<Block> out, String pkg, String host) {
        String guardedSite = f.siteGuarded(host);
        if (guardedSite != null && !f.exempt(pkg)) {
            if (f.dayLimitReached()) out.add(new Block(ItemKey.site(guardedSite), BlockKind.DAY, null, false));
            if (f.nightActive()) out.add(new Block(ItemKey.site(guardedSite), BlockKind.NIGHT, null, false));
        }
        DailySchedule.Rule rule = f.scheduleSite(host);
        if (rule != null) {
            out.add(new Block(ItemKey.site(DailySchedule.matchDomain(host, rule.sites)), BlockKind.SCHEDULE, rule, false));
        } else {
            DailySchedule.Rule codeRule = f.codeSite(host);
            if (codeRule != null) {
                out.add(new Block(ItemKey.site(DailySchedule.matchDomain(host, codeRule.sites)), BlockKind.CODE, null, true));
            }
        }
        siteLimits(f, out, host, false);
    }

    /** Sajtovi sa liste koji pokrivaju host: uvek blokiran ili potrošen limit; inače upozorenje pred istek. */
    static void siteLimits(Facts f, List<Block> out, String host, boolean warn) {
        for (String domain : f.matchingSites(host)) {
            int limit = f.siteLimit(domain);
            String key = ItemKey.site(domain);
            if (limit == 0) {
                out.add(new Block(key, BlockKind.SITE, null, true));
            } else if (limit > 0 && f.usedSite(domain) >= limit * 60000L) {
                out.add(new Block(key, BlockKind.SITE_TIME, null, true));
            } else if (limit > 0 && warn) {
                f.warnSite(domain, limit, f.usedSite(domain));
            }
        }
    }

    private static int unseen(Facts f, int softKind) {
        return BlockKind.unseenSite(softKind, f.hardSiteBlock());
    }
}
