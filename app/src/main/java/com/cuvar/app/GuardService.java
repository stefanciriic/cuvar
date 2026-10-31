package com.cuvar.app;

import com.cuvar.app.rules.ItemKey;
import com.cuvar.app.rules.BlockRules;
import com.cuvar.app.guard.GuardFacts;
import com.cuvar.app.data.AppLabels;
import com.cuvar.app.data.Store;
import com.cuvar.app.web.BrowserWindows;
import com.cuvar.app.guard.AppKinds;
import com.cuvar.app.guard.UsageAccounting;
import com.cuvar.app.guard.BlockScreen;

import com.cuvar.app.guard.BlockOverlayContent;
import com.cuvar.app.guard.ForegroundApp;
import com.cuvar.app.guard.GuardDiagnostics;
import com.cuvar.app.guard.GuardOverlayWindow;
import com.cuvar.app.guard.LoadMeter;
import com.cuvar.app.guard.PauseOverlayContent;
import com.cuvar.app.guard.SystemScreens;
import com.cuvar.app.rules.BlockKind;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.rules.DayLimit;
import com.cuvar.app.rules.UsageTracker;
import com.cuvar.app.ui.Safe;
import com.cuvar.app.ui.Ui;
import com.cuvar.app.web.BrowserAddressState;
import com.cuvar.app.web.BrowserMonitor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.cuvar.app.rules.BlockKind.Block;

/**
 * Srce aplikacije. Prati koja je aplikacija na ekranu i koji je sajt otvoren u pregledaču,
 * meri vreme i po potrebi prikazuje ekran za blokadu preko svega.
 */
public class GuardService extends AccessibilityService {

    public static volatile boolean running;
    private static final long TICK_MS = 5000L;
    /** Povratak u istu aplikaciju za manje od ovoga ne računa se kao novo otvaranje. */
    private static final long OPEN_GRACE_MS = 15000L;

    // Vrste blokade: opis je u BlockKind.
    public static final int KIND_LOCK = BlockKind.LOCK;
    public static final int KIND_TIME = BlockKind.TIME;
    public static final int KIND_SITE = BlockKind.SITE;
    public static final int KIND_SITE_TIME = BlockKind.SITE_TIME;
    public static final int KIND_SCHEDULE = BlockKind.SCHEDULE;
    public static final int KIND_CODE = BlockKind.CODE;
    public static final int KIND_NIGHT = BlockKind.NIGHT;
    public static final int KIND_DAY = BlockKind.DAY;
    public static final int KIND_OPENS = BlockKind.OPENS;
    public static final int KIND_PAUSE = BlockKind.PAUSE;
    public static final int PAUSE_SECONDS = 6;
    public static final int KIND_BREAK = BlockKind.BREAK;
    public static final int KIND_CLONER = BlockKind.CLONER;
    public static final int KIND_INAPP = BlockKind.INAPP;
    public static final int KIND_ADDRESS = BlockKind.ADDRESS;
    public static final int KIND_FOCUS = BlockKind.FOCUS;
    public static final int KIND_BROWSER = BlockKind.BROWSER;
    public static final int KIND_UNSEEN_SITE = BlockKind.UNSEEN_SITE;
    /** Sistemski prozori koji se pojave preko aplikacije, a ne znače da je korisnik izašao iz nje. */
    public static final Set<String> TRANSPARENT = new HashSet<>();

    static {
        TRANSPARENT.add("com.android.systemui");
        TRANSPARENT.add("android");
    }

    public final BrowserWindows windows = new BrowserWindows(this);
    public final AppKinds kinds = new AppKinds(this);
    public final UsageAccounting accounting = new UsageAccounting(this);
    private final GuardFacts facts = new GuardFacts(this);
    public final BlockScreen screen = new BlockScreen(this);
    public final Handler h = new Handler(Looper.getMainLooper());
    public Store store;
    public GuardOverlayWindow overlayWindow;
    public PowerManager power;
    public KeyguardManager keyguard;
    private boolean receiverOn;
    public String currentPkg;    // aplikacija koja je trenutno na ekranu
    private String currentHost;   // host trenutno otvoren u pregledaču (za vremenske režime)
    public DailySchedule.Rule overlayRule; // režim prikazan na ekranu za blokadu
    public final UsageTracker usageTracker = new UsageTracker(3 * TICK_MS);
    public final Set<String> visibleHosts = new HashSet<>();
    private final Set<String> secondaryContentPackages = new HashSet<>();
    public BrowserMonitor browserMonitor;
    public PauseOverlayContent pauseOverlayContent;
    public BlockOverlayContent blockOverlayContent;
    private BrowserAddressState currentAddress;
    public boolean observationValid;
    private boolean checkPending;
    private boolean contentEvents = true; // da li stižu i događaji o promeni sadržaja (samo za pregledače)
    private long lastEventCheck;
    private long lastContentCheck; // poslednja provera posle promene sadržaja (SystemClock.elapsedRealtime)
    // Najkraći razmak provera dok se sadržaj stalno menja (skrolovanje): adresa se čita najviše ~2,5 puta u sekundi.
    private static final long CONTENT_CHECK_MS = 400L;
    private long ruledSeenAt;     // kad je poslednji put viđena aplikacija sa pravilom ili pregledač napred (System.currentTimeMillis)
    public String leavingSiteKey;
    public String leavingSiteBrowser;
    public long leavingSiteUntil;
    private String pendingOpen;      // aplikacija upravo otvorena; broji se kad se zaista prikaže, bez blokade
    private String opensBlocked;     // aplikacija otvorena posle potrošenih otvaranja; blokirana dok se ne izađe
    public final Map<String, String> activityOf = new LinkedHashMap<String, String>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> entry) {
            return size() > 128;
        }
    }; // paket -> poslednji prikazan ekran (aktivnost)
    /** Do kada sme da se otvori ekran „Pristup korišćenju“ iako je zaštita uključena (korisnik je tapnuo dugme u Čuvaru). */
    public static volatile long usageSetupUntil;
    /** Korisnik je iz Statistike otvorio „Pristup obaveštenjima“: taj ekran se ne zatvara dva minuta. */
    private ForegroundApp foreground;
    private AudioManager audio;
    public boolean currentCall;     // napred je ekran poziva aplikacije kojoj pozivi uvek prolaze
    public boolean usageOk;
    private long usageCheckedAt;
    private Set<String> watched;     // paketi od kojih Pristupačnost šalje događaje 
    private boolean currentQuiet;    // napred je aplikacija bez ikakvog pravila (mape, kalkulator...)
    private boolean currentInApp;    // napred je pregledač unutar aplikacije
    public String rawPkg;           // stvarni paket na ekranu (pre prepoznavanja kopije)
    private String pausePkg;         // aplikacija upravo otvorena koja pre prikaza čeka pauzu
    private String lastLeftPkg;      // aplikacija iz koje se upravo izašlo
    private long lastLeftAt;
    private boolean leftBlocked, leftPending, leftPause;
    private long protectToastAt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            // Otkucaj se zakazuje u finally: jedna greška ne sme zauvek da zaustavi merenje i limite.
            try {
                guarded("watched", GuardService.this::updateWatched);
                guarded("music", () -> {
                    if (screen.overlay == null && overlayWindow != null) overlayWindow.noteMusic(rawPkg);
                });
                // Dok je navigacija napred bez „Pristupa korišćenju“, njen prozor se ne čita ni povremeno;
                // prelazak u drugu aplikaciju javlja ta aplikacija.
                guarded("tick", () -> {
                    if (usageOk || !(currentQuiet && screen.overlay == null && kinds.handsOff(rawPkg))) safeCheck();
                });
                guarded("heartbeat", () -> store.guardLog.guardBeat(false));
            } finally {
                h.postDelayed(this, TICK_MS);
            }
        }
    };

    /** Dok je ekran upaljen, na 1,5 s se od Androida pita koja je aplikacija napred (aplikacije bez pravila ne šalju događaje). */
    private final Runnable foregroundPoll = new Runnable() {
        @Override
        public void run() {
            try {
                if (usageOk && foreground != null && power != null && power.isInteractive()) {
                    foreground.refresh(() -> h.post(() -> {
                        String fg = foreground == null ? null : foreground.current();
                        if (fg != null && !fg.equals(rawPkg) && !TRANSPARENT.contains(fg)) safeCheck();
                    }));
                }
            } catch (Throwable error) {
                GuardDiagnostics.report("foregroundPoll", error);
            }
            h.postDelayed(this, 1500L);
        }
    };

    private final Runnable throttled = new Runnable() {
        @Override
        public void run() {
            checkPending = false;
            lastContentCheck = SystemClock.elapsedRealtime();
            safeCheck();
        }
    };

    public final Runnable recheckSoon = new Runnable() {
        @Override
        public void run() {
            safeCheck();
        }
    };

    private final Runnable recheckLater = new Runnable() {
        @Override
        public void run() {
            safeCheck();
        }
    };

    /** Keep the just-dismissed site block from flashing back while the browser navigates to the neutral page. */
    public final Runnable finishSiteExit = new Runnable() {
        @Override
        public void run() {
            leavingSiteKey = null;
            leavingSiteBrowser = null;
            leavingSiteUntil = 0L;
            safeCheck();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent == null ? null : intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                // Svaki korak posebno: i kad merenje pukne, izmereno vreme se sačuva.
                guarded("screenOffAccount", () -> accounting.accountUntilNow(Collections.emptySet()));
                guarded("screenOffOverlay", screen::hideOverlay);
                if (store != null) {
                    guarded("screenOffFlush", store::flush);
                    guarded("screenOffBeat", () -> store.guardLog.guardBeat(true));
                }
                GuardDiagnostics.flush();
            } else {
                h.removeCallbacks(recheckSoon);
                h.postDelayed(recheckSoon, 300);
            }
        }
    };

    /**
     * Instalirana ili ažurirana aplikacija: spiskovi pregledača, aplikacija sa ikonom i kopija se odmah osvežavaju.
     * Inače bi novi pregledač do 5 minuta radio kao obična aplikacija, bez pravila za sajtove.
     */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (browserMonitor != null) browserMonitor.resetApps();
                kinds.launcherApps = null;
                kinds.webApkHosts.clear();
                kinds.clones = null;
                android.net.Uri data = intent == null ? null : intent.getData();
                if (data != null) accounting.forgetTracked(data.getSchemeSpecificPart());
                if (store != null) updateWatched();
                h.removeCallbacks(recheckSoon);
                h.postDelayed(recheckSoon, 300);
            } catch (Throwable error) {
                GuardDiagnostics.report("packageAdded", error);
            }
        }
    };
    private boolean packageReceiverOn;

    /** Da li je korisnik uključio Čuvara u Pristupačnosti. */
    public static boolean isEnabled(Context c) {
        if (running) {
            return true;
        }
        try {
            String s = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) {
                return false;
            }
            ComponentName me = new ComponentName(c, GuardService.class);
            String all = s.toLowerCase(Locale.ROOT);
            return all.contains(me.flattenToString().toLowerCase(Locale.ROOT))
                    || all.contains(me.flattenToShortString().toLowerCase(Locale.ROOT));
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Pokretanje ne sme da padne: pad ovde se ponavlja pri svakom pokušaju i Android gasi Pristupačnost.
        // Bez Store-a servis ostaje živ i ništa ne blokira, a greška se vidi u dijagnostici.
        guarded("script", () -> Ui.loadScript(this));
        try {
            store = Store.get(this);
            Store s = store;
            GuardDiagnostics.useClock(s::now);
        } catch (Throwable error) {
            GuardDiagnostics.report("store", error);
        }
        guarded("unlockSince", () -> store.unlock.unlockSince()); // otključavanja se broje od prvog pokretanja ove verzije
        try {
            connectParts();
        } catch (Throwable error) {
            GuardDiagnostics.report("connect", error);
        }
        connectSystem();
    }

    /** Delovi servisa koji crtaju i čitaju ekran. */
    private void connectParts() {
        browserMonitor = new BrowserMonitor(this);
        overlayWindow = new GuardOverlayWindow(this);
        pauseOverlayContent = new PauseOverlayContent(this, h, store, new PauseOverlayContent.Actions() {
            @Override public boolean isVisible(View view) { return screen.overlay == view; }
            @Override public String appLabel(String packageName) { return GuardService.this.appLabel(packageName); }
            @Override public void close() {
                pausePkg = null;
                performGlobalAction(GLOBAL_ACTION_HOME);
                h.removeCallbacks(recheckSoon);
                h.postDelayed(recheckSoon, 600L);
            }
            @Override public void continueToApp() {
                pausePkg = null;
                safeCheck();
            }
        });
        blockOverlayContent = new BlockOverlayContent(this);
        power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    /** Događaji, prijemnici i otkucaj; otkucaj se zakazuje i kad neki korak pre njega pukne. */
    private void connectSystem() {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
                contentEvents = false;
                info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                info.notificationTimeout = 100; // manje čekanja na događaj: blokada stiže brže
                setServiceInfo(info);
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("serviceInfo", error);
        }

        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(screenReceiver, f);
            }
            receiverOn = true;
        } catch (Throwable error) {
            GuardDiagnostics.report("screenReceiver", error);
        }
        try {
            IntentFilter pf = new IntentFilter();
            pf.addAction(Intent.ACTION_PACKAGE_ADDED);
            pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
            pf.addDataScheme("package");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(packageReceiver, pf, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(packageReceiver, pf);
            }
            packageReceiverOn = true;
        } catch (Throwable error) {
            GuardDiagnostics.report("packageReceiver", error);
        }

        running = true;
        LoadMeter.GUARD.start(SystemClock.elapsedRealtime());
        guarded("guardStarted", () -> store.guardLog.guardStarted());
        guarded("foreground", () -> {
            if (foreground != null) foreground.close();
            foreground = new ForegroundApp(this);
            if (usageOk) foreground.refresh(() -> h.post(this::safeCheck));
        });
        guarded("watched", this::updateWatched);
        h.removeCallbacks(tick);
        h.postDelayed(tick, TICK_MS);
        h.removeCallbacks(foregroundPoll);
        h.postDelayed(foregroundPoll, 1500L);
        safeCheck();
    }

    /**
     * Sa dozvolom „Pristup korišćenju“ Pristupačnost šalje događaje samo iz aplikacija sa pravilom, pregledača,
     * početnog ekrana i (dok je zaštita uključena) podešavanja. Mape, pozivi i sve ostalo tada Čuvaru ništa ne šalju,
     * a da su napred, Čuvar saznaje od Androida. Bez dozvole se prate sve aplikacije, kao ranije.
     */
    private void updateWatched() {
        long now = SystemClock.elapsedRealtime();
        if (now - usageCheckedAt > 30000L || usageCheckedAt == 0) {
            usageOk = ForegroundApp.granted(this);
            usageCheckedAt = now;
        }
        Set<String> want = new HashSet<>();
        if (!usageOk || store.focus.focusActive()) {
            // Bez zapisa o korišćenju (ili tokom fokusa) prate se sve aplikacije sa ikonom i sistemski ekrani, ali ne i navigacija.
            want.addAll(kinds.launcherApps());
            want.add("android");
            want.add("com.android.systemui");
            Collections.addAll(want, AppKinds.SETTINGS_APPS);
        }
        want.add(getPackageName());
        want.addAll(kinds.exempt()); // početni ekran (i nedavne aplikacije u njemu), telefon, poruke
        want.addAll(BrowserMonitor.BROWSER_PACKAGES);
        want.addAll(browserMonitor.webApps());
        want.addAll(store.guardedApps());
        if (store.hasSiteRules() || store.focus.focusHasSites()) Collections.addAll(want, AppKinds.IN_APP_APPS);
        if (store.protectNow()) Collections.addAll(want, AppKinds.SETTINGS_APPS);
        for (String p : AppKinds.HANDS_OFF) if (kinds.handsOff(p)) want.remove(p);
        if (want.equals(watched)) return;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) return;
            info.packageNames = want.toArray(new String[0]);
            setServiceInfo(info);
            watched = want;
        } catch (Throwable error) {
            GuardDiagnostics.report("watched", error);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        try {
            onEvent(event);
        } catch (Throwable error) {
            GuardDiagnostics.report("event", error);
        }
    }

    private void onEvent(AccessibilityEvent event) {
        LoadMeter.GUARD.event();
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence p = event.getPackageName(), cls = event.getClassName();
            if (p != null && store != null && !kinds.quiet(p.toString())) ruledSeenAt = System.currentTimeMillis();
            if (p != null && cls != null) {
                String c = cls.toString();
                // Pamti se samo ekran aplikacije, ne sistemski dijalozi i delovi ekrana.
                if (c.contains(".") && !c.startsWith("android.") && !c.startsWith("androidx.")) activityOf.put(p.toString(), c);
            }
        }
        if ((type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
                && currentQuiet && screen.overlay == null) {
            // Napred je aplikacija bez pravila. Mape i slične aplikacije šalju ovakve događaje u nizu (pomeranje mape,
            // donji paneli), pa se ništa ne čita dok događaj dolazi iz iste aplikacije; promena aplikacije se proveri odmah.
            CharSequence p = event.getPackageName();
            if (kinds.handsOff(rawPkg) && (p == null || p.toString().equals(rawPkg))) return; // navigacija napred: ništa se ne proverava
            if (p == null || p.toString().equals(rawPkg)) {
                h.removeCallbacks(recheckLater);
                h.postDelayed(recheckLater, 1200);
                return;
            }
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Otvoren je novi prozor: proveri odmah, pa još dva puta jer sistem ponekad kasni.
            // Kad događaji stižu u nizu, odmah se proverava najviše jednom u 300 ms, da se ne opterećuje telefon.
            long nowEl = SystemClock.elapsedRealtime();
            if (nowEl - lastEventCheck >= 300) {
                lastEventCheck = nowEl;
                safeCheck();
            }
            h.removeCallbacks(recheckSoon);
            h.postDelayed(recheckSoon, 350);
            h.removeCallbacks(recheckLater);
            h.postDelayed(recheckLater, 1200);
        } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Sadržaj se menja vrlo često; zanima nas samo u pregledaču (promena adrese).
            CharSequence p = event.getPackageName();
            if (p != null && (wantsContent(p.toString()) || secondaryContentPackages.contains(p.toString())
                    || (currentInApp && p.toString().equals(currentPkg))) && !checkPending) {
                // Prva promena posle mirovanja (nova adresa) se proverava odmah, da se blokiran sajt ne vidi;
                // promene koje stižu u nizu čekaju jednu zajedničku proveru.
                long nowEl = SystemClock.elapsedRealtime();
                long wait = CONTENT_CHECK_MS - (nowEl - lastContentCheck);
                if (wait <= 0) {
                    lastContentCheck = nowEl;
                    safeCheck();
                } else {
                    checkPending = true;
                    h.postDelayed(throttled, wait);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (store != null) {
            store.guardLog.guardStopped();
        }
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        running = false;
        h.removeCallbacksAndMessages(null);
        if (foreground != null) {
            guarded("foregroundClose", foreground::close);
            foreground = null;
        }
        // I kad merenje ili skidanje prozora pukne, prijemnici se odjave i podaci se sačuvaju.
        guarded("stopAccount", () -> accounting.accountUntilNow(Collections.emptySet()));
        guarded("stopOverlay", screen::hideOverlay);
        if (receiverOn) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Throwable ignored) {
                // prijemnik je već odjavljen
            }
            receiverOn = false;
        }
        if (packageReceiverOn) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Throwable ignored) {
                // prijemnik je već odjavljen
            }
            packageReceiverOn = false;
        }
        if (store != null) {
            guarded("stopFlush", store::flush);
        }
        GuardDiagnostics.flush();
    }

    /**
     * Ulaz iz Androida (događaj, prijemnik, zakazan posao): greška se prijavi, a servis radi dalje.
     * Neuhvaćena greška ruši ceo proces, a posle par padova Android gasi Pristupačnost.
     */
    private static void guarded(String where, Runnable work) {
        Safe.run(where, work);
    }

    public void safeCheck() {
        // Settle the OLD app/site before check() changes the observed context or shows a block.
        guarded("accountBefore", () -> accounting.accountUntilNow(null));
        // Muzika se beleži pre nego što se promeni aplikacija napred, da se zna iz koje je krenula.
        guarded("musicBefore", () -> {
            if (screen.overlay == null && overlayWindow != null) overlayWindow.noteMusic(rawPkg);
        });
        long started = System.nanoTime();
        try {
            check();
        } catch (Throwable error) {
            observationValid = false;
            GuardDiagnostics.report("check", error);
        } finally {
            LoadMeter.GUARD.check(System.nanoTime() - started);
            try {
                accounting.accountUntilNow(accounting.usageKeys());
            } catch (Throwable error) {
                GuardDiagnostics.report("usageContext", error);
                guarded("usageReset", () -> accounting.accountUntilNow(Collections.emptySet()));
            }
        }
    }

    /**
     * Da li je napred poziv u aplikaciji kojoj pozivi uvek prolaze: ekran poziva (dolazni, odlazni, u toku)
     * ili internet poziv koji upravo traje (Telegram poziv nema poseban ekran).
     */
    private boolean callPasses(String pkg, String raw) {
        if (!store.callsPass(pkg)) return false;
        String cls = activityOf.get(raw);
        if (cls != null && SystemScreens.callScreen(cls)) return true;
        try {
            return audio != null && audio.getMode() == AudioManager.MODE_IN_COMMUNICATION;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Glavna odluka: šta je na ekranu i da li to treba blokirati. */
    private void check() {
        observationValid = false;
        if (store == null || power == null || keyguard == null) {
            return;
        }
        if (!power.isInteractive()) {
            screen.hideOverlay();
            return;
        }
        if (keyguard.isKeyguardLocked()) {
            screen.hideOverlay();
            return;
        }

        // Aplikacija bez pravila (Mape, kalkulator...) se uopšte ne dira: koja je napred zna se od Androida
        // („Pristup korišćenju“), pa se njen prozor ne čita. Prozor se čita samo kad je napred aplikacija sa
        // pravilom, pregledač, podešavanja pod zaštitom ili kad se ne zna šta je napred.
        AccessibilityNodeInfo root = null;
        String pkg = null;
        if (usageOk && foreground != null && screen.overlay == null) {
            String fg = foreground.current();
            // Android ponekad kasni sa zapisom: ako je posle njega javila aplikacija sa pravilom, čita se prozor.
            if (fg != null && !TRANSPARENT.contains(fg) && kinds.quiet(fg)
                    && (kinds.handsOff(fg) || (foreground.since() > ruledSeenAt && windows.appWindowCount() < 2))) {
                pkg = fg;
            }
        }
        if (pkg == null) {
            // Prvo se uzme samo vrh ekrana, bez ostatka sadržaja: za aplikacije bez pravila to je sve što treba.
            root = Build.VERSION.SDK_INT >= 33 ? getRootInActiveWindow(0) : getRootInActiveWindow();
            pkg = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
            if (pkg != null && !pkg.equals(getPackageName())) ruledSeenAt = kinds.quiet(pkg) ? 0L : System.currentTimeMillis();
        }
        if (pkg == null && usageOk && foreground != null && screen.overlay == null) {
            // Prozor se ne može pročitati: koju je aplikaciju Android poslednju pokrenuo.
            String fg = foreground.current();
            if (fg != null && kinds.quiet(fg)) pkg = fg;
        }
        if (pkg == null) {
            return;
        }
        if (TRANSPARENT.contains(pkg)) {
            return;
        }
        boolean quiet = screen.overlay == null && kinds.quiet(pkg) && (kinds.handsOff(pkg) || windows.appWindowCount() < 2);
        if (!quiet && root == null) return;
        // Ceo vrh ekrana (sa delom sadržaja) treba samo za pregledač unutar aplikacije, gde se adresa traži po ekranu.
        // U pregledačima se adresa čita direktno po polju za adresu, bez čitanja stranice.
        if (!quiet && Build.VERSION.SDK_INT >= 33 && kinds.inAppBrowser(pkg)) {
            AccessibilityNodeInfo full = getRootInActiveWindow();
            if (full != null && full.getPackageName() != null && pkg.equals(full.getPackageName().toString())) root = full;
        }
        currentQuiet = quiet;
        observationValid = true;
        visibleHosts.clear();
        secondaryContentPackages.clear();
        String raw = pkg;
        if (screen.overlay != null && pkg.equals(getPackageName())) {
            if (currentPkg == null) return;
            pkg = currentPkg; // ponovo proveri pravila i dok je naš ekran preko aplikacije
            raw = rawPkg != null ? rawPkg : currentPkg;
        } else {
            pkg = kinds.cloneOf(pkg); // kopija aplikacije (npr. Instagram 2) potpada pod pravila originala
        }
        rawPkg = raw;

        if (!pkg.equals(currentPkg)) {
            long nowEl = SystemClock.elapsedRealtime();
            // Kratak izlazak (deljenje, izbor fajla, dozvola) i povratak nije novo otvaranje.
            boolean back = pkg.equals(lastLeftPkg) && nowEl - lastLeftAt < OPEN_GRACE_MS;
            // Sačuvano stanje pripada aplikaciji kojoj se vraćamo. Pre nego što
            // zabeležimo stanje aplikacije iz koje izlazimo, uzmi njegov snimak;
            // u suprotnom bi A -> B -> A obnovilo stanje aplikacije B.
            boolean returnBlocked = leftBlocked;
            boolean returnPending = leftPending;
            boolean returnPause = leftPause;
            if (currentPkg != null) {
                lastLeftPkg = currentPkg;
                lastLeftAt = nowEl;
                // Stanje aplikacije iz koje se izlazi: povratak u nju nastavlja tačno odatle (blokada, pauza, nebrojano otvaranje).
                leftBlocked = currentPkg.equals(opensBlocked);
                leftPending = currentPkg.equals(pendingOpen);
                leftPause = currentPkg.equals(pausePkg);
            }
            currentPkg = pkg;
            setContentEvents(wantsContent(pkg));
            currentHost = null;
            currentAddress = null;
            if (back) {
                opensBlocked = returnBlocked ? pkg : null;
                pendingOpen = returnPending ? pkg : null;
                pausePkg = returnPause ? pkg : null;
            } else {
                int max = store.appOpensNow(pkg);
                opensBlocked = max > 0 && store.usageLog.opensToday(ItemKey.app(pkg)) >= max ? pkg : null;
                pendingOpen = opensBlocked == null && max > 0 ? pkg : null;
                pausePkg = store.appGuarded(pkg) && !kinds.exempt().contains(pkg) ? pkg : null;
            }
        }

        currentCall = false;
        if (quiet) {
            // Aplikacija bez pravila: samo se beleži da je napred (za merenje), bez čitanja ekrana i drugih prozora.
            currentInApp = false;
            currentHost = null;
            currentAddress = null;
            setContentEvents(false);
            screen.hideOverlay();
            return;
        }

        if (store.protectNow() && kinds.guardsSelf(root, pkg)) {
            // Ekran na kome bi Čuvar mogao da se isključi, zaustavi ili obriše: zatvori ga.
            screen.hideOverlay();
            performGlobalAction(GLOBAL_ACTION_BACK);
            performGlobalAction(GLOBAL_ACTION_HOME);
            long nowEl = SystemClock.elapsedRealtime();
            if (nowEl - protectToastAt > 5000L) {
                protectToastAt = nowEl;
                Ui.toast(this, "Čuvar je zaštićen od isključivanja. Zaštita se gasi u Čuvaru i važi tek od sledećeg jutra.");
            }
            return;
        }

        if (callPasses(pkg, raw)) {
            // Glasovni ili video poziv (WhatsApp, Viber...): prolazi kroz noć, limite, režime i pauzu,
            // ne broji se kao otvaranje i ne troši limit. Prozor sa porukama ostaje pod pravilima.
            currentCall = true;
            currentInApp = false;
            currentHost = null;
            currentAddress = null;
            pendingOpen = null;
            setContentEvents(false);
            screen.hideOverlay();
            return;
        }

        String urlBarId = BrowserMonitor.addressViewId(pkg);
        boolean inApp = urlBarId == null && kinds.inAppBrowser(raw);
        String apkHost = urlBarId == null && !inApp ? kinds.webApkHost(raw) : null;
        if (inApp != currentInApp) {
            currentInApp = inApp;
            currentHost = null;
            currentAddress = null;
            setContentEvents(inApp || wantsContent(pkg));
        }
        AccessibilityNodeInfo addressRoot = null;
        if (urlBarId != null && !root.getPackageName().toString().equals(getPackageName())) {
            // Oblačić, meni ili savet preko stranice je poseban prozor bez adresne trake: adresa se čita iz glavnog prozora.
            addressRoot = windows.browserAddressRoot(root, raw, urlBarId);
            currentAddress = browserMonitor.readAddress(addressRoot, urlBarId, activityOf.get(raw));
            currentHost = currentAddress.host();
        } else if (inApp && !root.getPackageName().toString().equals(getPackageName())) {
            String host = browserMonitor.inAppHost(root);
            currentHost = host;
        } else if (apkHost != null) {
            currentHost = apkHost.isEmpty() ? null : apkHost;
        }
        boolean browsing = urlBarId != null || inApp || apkHost != null;
        if (browsing && currentHost != null) visibleHosts.add(currentHost);

        // Sva pravila koja sada važe, od najstrožeg (vidi BlockRules).
        // Otključavanje jedne stavke ne otvara ostale (otključan pregledač ne otvara blokiran sajt).
        String appKey = ItemKey.app(pkg);
        int dayLimit = store.dayLimits.dayLimit();
        if (dayLimit > 0) {
            long used = store.dayLimits.guardedToday();
            if (DayLimit.warn(dayLimit, used) && store.dayLimits.firstDayLimitWarning()) {
                long left = dayLimit * 60000L - used;
                Ui.toast(this, "Čuvar: do dnevnog limita ostalo je " + Ui.fmt(left) + ".");
            }
            if (DayLimit.reached(dayLimit, used)) store.dayLimits.markDayLimitHit();
        }
        boolean unresolved = currentAddress != null && currentAddress.unresolved(SystemClock.elapsedRealtime());
        List<Block> blocks = BlockRules.front(facts, new BlockRules.Front(pkg, currentHost, browsing,
                inApp || apkHost != null, pkg.equals(opensBlocked), unresolved));

        // Svaki prozor na ekranu se proverava i kad druga blokada pobeđuje, da se izmeri vreme svih vidljivih sajtova.
        List<BlockRules.Window> others = new ArrayList<>();
        for (BrowserWindows.AppWindow other : windows.otherAppWindows()) {
            if (BrowserWindows.isSameBrowserWindow(other, raw, urlBarId, addressRoot)) continue;
            String id = BrowserMonitor.addressViewId(other.pkg);
            boolean embedded = id == null && kinds.inAppBrowser(other.rawPkg);
            if (id != null || embedded) secondaryContentPackages.add(other.rawPkg);
            String host = null;
            boolean addressUnknown = false;
            if (id != null) {
                BrowserAddressState address = browserMonitor.readAddress(other.root, id, activityOf.get(other.rawPkg));
                host = address.host();
                addressUnknown = address.unresolved(SystemClock.elapsedRealtime());
            } else if (embedded) {
                host = browserMonitor.inAppHost(other.root);
            }
            if (host != null) visibleHosts.add(host);
            others.add(new BlockRules.Window(other.pkg, host, addressUnknown, embedded && host == null));
        }
        blocks.addAll(BlockRules.windows(facts, others, this::unlockedNow));
        setContentEvents(wantsContent(pkg) || inApp || !secondaryContentPackages.isEmpty());

        // Back dismisses the current blocked URL while the browser opens a neutral page. Ignore that site
        // and its temporary unknown-address state briefly; app rules still take effect immediately.
        if (leavingSiteKey != null) {
            boolean sameBrowser = pkg.equals(leavingSiteBrowser)
                    || (rawPkg != null && rawPkg.equals(leavingSiteBrowser));
            if (SystemClock.elapsedRealtime() >= leavingSiteUntil || !sameBrowser) {
                h.removeCallbacks(finishSiteExit);
                leavingSiteKey = null;
                leavingSiteBrowser = null;
                leavingSiteUntil = 0L;
            } else {
                String exitingKey = leavingSiteKey;
                String exitingBrowser = leavingSiteBrowser;
                blocks.removeIf(b -> exitingKey.equals(b.key)
                        // U adresnoj traci se kratko pojavi i „nepoznata adresa”; ne prikazuj drugi overlay
                        // dok isti browser otvara neutralnu stranicu.
                        || ((b.kind == KIND_ADDRESS || b.kind == KIND_UNSEEN_SITE) && (ItemKey.app(exitingBrowser)).equals(b.key)));
            }
        }

        // Otključavanje dnevnom šifrom važi 5 minuta, a zatim sat vremena nema otključavanja (vidi Store),
        // osim jednog hitnog otključavanja dnevno. Režim i potrošen limit se nikad ne otključavaju.
        Block show = BlockKind.pick(blocks, this::unlockedNow);
        if (show == null && pkg.equals(pausePkg)) {
            if (unlockedNow(appKey)) {
                pausePkg = null; // upravo otključano šifrom: bez još jedne pauze
            } else {
                show = new Block(appKey, KIND_PAUSE, null, false);
            }
        }
        long coolLeft = show == null || hard(show.kind) || show.kind == KIND_PAUSE ? 0 : store.unlock.cooldownLeft(show.key);

        if (show != null && show.kind != KIND_PAUSE && (screen.overlay == null || !show.key.equals(screen.overlayKey) || screen.overlayKind == KIND_PAUSE)) {
            store.usageLog.countOpen("try:" + show.key); // novi pokušaj da se otvori nešto blokirano
        }
        if (show == null && pkg.equals(pendingOpen)) {
            store.usageLog.countOpen(ItemKey.app(pkg)); // otvaranje se broji tek kad se aplikacija zaista vidi
            pendingOpen = null;
        }

        if (show != null) {
            screen.showOverlay(show.key, show.kind, show.rule, coolLeft > 0, show.code);
            if (screen.cooldownLabel != null && coolLeft > 0) {
                screen.cooldownLabel.setText(BlockScreen.cooldownText(coolLeft));
            }
        } else {
            screen.hideOverlay();
            screen.overlayFailures = 0;
            screen.overlayRetryAt = 0L;
        }
    }

    /**
     * Promene sadržaja ekrana trebaju samo za adresu u pregledaču. Dok je napred druga aplikacija,
     * Čuvar ih ne prima, pa ni aplikacije (npr. mape koje se stalno iscrtavaju) ne moraju da ih šalju.
     */
    private void setContentEvents(boolean on) {
        if (on == contentEvents) return;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) return;
            if (on) {
                info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            } else {
                info.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            }
            setServiceInfo(info);
            contentEvents = on;
        } catch (Throwable error) {
            GuardDiagnostics.report("contentEvents", error);
        }
    }

    public static boolean hard(int kind) {
        return BlockKind.hard(kind);
    }

    private boolean wantsContent(String p) {
        return (BrowserMonitor.isBrowser(p) && (store == null || store.hasSiteRules() || store.focus.focusHasSites()))
                || (AppKinds.settingsLike(p) && store != null && store.protectNow());
    }

    /** Stavka je upravo otključana šifrom ili hitnim otključavanjem. */
    private boolean unlockedNow(String key) {
        return store.unlock.unlockLeft(key) > 0 || store.unlock.emergencyLeft(key) > 0;
    }

    public String appLabel(String pkg) {
        return AppLabels.of(getPackageManager(), pkg);
    }

    // ---------- Ekran za blokadu ----------

}
