package com.cuvar.app.guard;

import com.cuvar.app.GuardService;
import com.cuvar.app.rules.BlockRules;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.rules.DayLimit;
import com.cuvar.app.rules.ItemKey;

import java.util.List;

/** Pravila za BlockRules iz Store-a i vrste aplikacije, kako ih servis sada vidi. */
public final class GuardFacts implements BlockRules.Facts {
    private final GuardService svc;

    public GuardFacts(GuardService svc) {
        this.svc = svc;
    }

    @Override public boolean exempt(String pkg) { return svc.kinds.exempt().contains(pkg); }
    @Override public boolean guarded(String pkg) { return svc.kinds.guarded(pkg); }
    @Override public boolean dayLimitReached() {
        return DayLimit.reached(svc.store.dayLimits.dayLimit(), svc.store.dayLimits.guardedToday());
    }
    @Override public boolean dayGuarded(String pkg) { return svc.kinds.dayGuarded(pkg); }
    @Override public String daySite(String host) { return svc.store.dayLimitSite(host); }
    @Override public boolean nightActive() { return svc.store.nightActive(); }
    @Override public boolean nightBlocksApp(String pkg) { return svc.store.nightBlocksApp(pkg); }
    @Override public String siteGuarded(String host) { return svc.store.siteGuarded(host); }
    @Override public boolean focusActive() { return svc.store.focus.focusActive(); }
    @Override public boolean focusAllowsApp(String pkg) { return svc.store.focus.focusAllowsApp(pkg); }
    @Override public boolean focusAllowsSite(String host) { return svc.store.focus.focusAllowsSite(host); }
    @Override public boolean onBreak(String pkg) { return svc.store.sessions.sessionBreakLeft(pkg) > 0; }
    @Override public DailySchedule.Rule scheduleApp(String pkg) { return svc.store.modes.scheduleBlockingApp(pkg); }
    @Override public DailySchedule.Rule scheduleSite(String host) { return svc.store.modes.scheduleBlockingSite(host); }
    @Override public DailySchedule.Rule codeApp(String pkg) { return svc.store.modes.codeRuleForApp(pkg); }
    @Override public DailySchedule.Rule codeSite(String host) { return svc.store.modes.codeRuleForSite(host); }
    @Override public boolean appLock(String pkg) { return svc.store.appLockNow(pkg); }
    @Override public int appLimit(String pkg) { return svc.store.appLimitNow(pkg); }
    @Override public long usedApp(String pkg) { return svc.store.usedShared(pkg); }
    @Override public List<String> matchingSites(String host) { return svc.store.matchingSitesNow(host); }
    @Override public int siteLimit(String domain) { return svc.store.siteLimitNow(domain); }
    @Override public long usedSite(String domain) { return svc.store.usedShared(ItemKey.site(domain)); }
    @Override public boolean unknownBrowser(String pkg) { return svc.kinds.unknownBrowser(pkg); }
    @Override public boolean cloner(String pkg) { return svc.kinds.cloner(pkg); }
    @Override public boolean hasSiteRules() { return svc.store.hasSiteRules(); }
    @Override public boolean hardSiteBlock() { return svc.store.modes.hardSiteBlockNow(); }
    @Override public void warnApp(String pkg, int limit, long used) {
        svc.accounting.warnLimit(ItemKey.app(pkg), svc.appLabel(pkg), limit, used);
    }
    @Override public void warnSite(String domain, int limit, long used) {
        svc.accounting.warnLimit(ItemKey.site(domain), domain, limit, used);
    }
}
