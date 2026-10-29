package com.cuvar.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Srce aplikacije. Prati koja je aplikacija na ekranu i koji je sajt otvoren u pregledaču,
 * meri vreme i po potrebi prikazuje ekran za blokadu preko svega.
 */
public class GuardService extends AccessibilityService {

    static volatile boolean running;

    private static final long TICK_MS = 5000L;
    /** Povratak u istu aplikaciju za manje od ovoga ne računa se kao novo otvaranje. */
    private static final long OPEN_GRACE_MS = 15000L;

    static final int KIND_LOCK = 1;       // aplikacija zaključana PIN-om
    static final int KIND_TIME = 2;       // istekao dnevni limit aplikacije
    static final int KIND_SITE = 3;       // sajt uvek blokiran
    static final int KIND_SITE_TIME = 4;  // istekao dnevni limit sajta
    static final int KIND_SCHEDULE = 5;
    static final int KIND_CODE = 6;       // van perioda režima, otključava se dnevnom šifrom
    static final int KIND_NIGHT = 8;      // noćna blokada od 22:00, ništa se ne otključava do 06:00
    static final int KIND_DAY = 7;        // potrošen ukupni dnevni limit, ništa se ne otključava do ponoći
    static final int KIND_OPENS = 9;      // potrošena otvaranja aplikacije za danas
    static final int KIND_PAUSE = 11;     // kratka pauza pre otvaranja aplikacije sa pravilom, posle nje se nastavlja
    static final int PAUSE_SECONDS = 6;
    static final int KIND_BREAK = 12;     // obavezna pauza posle najdužeg korišćenja u komadu
    static final int KIND_CLONER = 13;    // aplikacija za kloniranje (Parallel Space i sl.), dok postoje pravila za aplikacije
    static final int KIND_INAPP = 14;     // pregledač unutar aplikacije (Instagram, Facebook) u kome se ne vidi adresa
    static final int KIND_ADDRESS = 15;   // podržan pregledač, ali adresa još nije potvrđena
    static final int KIND_FOCUS = 16;     // ručni režim: dozvoljene su samo izabrane aplikacije/sajtovi

    /** Aplikacije koje pokreću kopije drugih aplikacija pod svojim imenom, pa ih Čuvar ne bi prepoznao. */
    private static final String[] CLONERS = {"com.lbe.parallel", "com.parallel.space", "com.excelliance.multiaccount",
            "com.excelliance.dualaid", "com.ludashi.dualspace", "com.ludashi.superclone", "com.polestar.multiaccount",
            "com.polestar.domultiple", "com.jumobile.multiapp", "com.applisto.appcloner", "com.dualspace", "com.multiple.account",
            "com.trendmicro.dualapps", "do.multiple.cloner", "com.cloneapp", "com.waxmoon.ma.gp", "com.oasisfeng.island",
            "com.lody.virtual", "io.va.exposed", "com.vmos"};

    static final int KIND_BROWSER = 10;   // pregledač u kome Čuvar ne vidi adresu, dok postoje pravila za sajtove
    /** Sistemski prozori koji se pojave preko aplikacije, a ne znače da je korisnik izašao iz nje. */
    private static final Set<String> TRANSPARENT = new HashSet<>();
    /** Navigacija: Čuvar je nikad ne prati, ne čita i ne blokira, osim ako sama ima pravilo. */
    private static final Set<String> HANDS_OFF = new HashSet<>(java.util.Arrays.asList(
            "com.google.android.apps.maps", "com.google.android.apps.mapslite", "com.waze"));

    static {
        TRANSPARENT.add("com.android.systemui");
        TRANSPARENT.add("android");
    }

    final Handler h = new Handler(Looper.getMainLooper());
    Store store;
    private GuardOverlayWindow overlayWindow;
    private PowerManager power;
    private KeyguardManager keyguard;
    private boolean receiverOn;

    private String currentPkg;    // aplikacija koja je trenutno na ekranu
    private String currentHost;   // host trenutno otvoren u pregledaču (za vremenske režime)
    private DailySchedule.Rule overlayRule; // režim prikazan na ekranu za blokadu
    private final UsageTracker usageTracker = new UsageTracker(3 * TICK_MS);
    private final Set<String> visibleHosts = new HashSet<>();
    private final Set<String> secondaryContentPackages = new HashSet<>();
    private BrowserMonitor browserMonitor;
    PauseOverlayContent pauseOverlayContent;
    private BlockOverlayContent blockOverlayContent;
    private BrowserAddressState currentAddress;
    private boolean observationValid;
    private boolean checkPending;
    private boolean contentEvents = true; // da li stižu i događaji o promeni sadržaja (samo za pregledače)
    private long lastEventCheck;
    private Set<String> launcherApps;
    private long launcherAppsAt;
    private long ruledSeenAt;     // kad je poslednji put viđena aplikacija sa pravilom ili pregledač napred (System.currentTimeMillis)

    private View overlay;
    private String overlayKey;
    private int overlayKind;
    private boolean overlayCooling;   // prikazana je pauza posle otključavanja
    private boolean overlayCode;      // otključava se dnevnom šifrom umesto PIN-om
    TextView cooldownLabel;  // odbrojavanje do sledećeg mogućeg otključavanja
    private String leavingSiteKey;
    private String leavingSiteBrowser;
    private long leavingSiteUntil;
    private Set<String> exempt;      // aplikacije koje ne troše ukupni limit i ne blokiraju se zbog njega, ni uz pravilo
    private String pendingOpen;      // aplikacija upravo otvorena; broji se kad se zaista prikaže, bez blokade
    private String opensBlocked;     // aplikacija otvorena posle potrošenih otvaranja; blokirana dok se ne izađe
    private final Map<String, String> activityOf = new LinkedHashMap<String, String>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> entry) {
            return size() > 128;
        }
    }; // paket -> poslednji prikazan ekran (aktivnost)
    /** Do kada sme da se otvori ekran „Pristup korišćenju“ iako je zaštita uključena (korisnik je tapnuo dugme u Čuvaru). */
    static volatile long usageSetupUntil;
    /** Aplikacije koje otvaraju linkove u svom pregledaču. */
    private static final String[] IN_APP_APPS = {"com.instagram.android", "com.instagram.lite", "com.facebook.katana",
            "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.twitter.android", "com.snapchat.android", "com.linkedin.android", "com.reddit.frontpage",
            "com.google.android.googlequicksearchbox", "com.google.android.gm", "org.telegram.messenger", "com.pinterest"};
    /** Sistemski ekrani koje zaštita od isključivanja mora da vidi. */
    private static final String[] SETTINGS_APPS = {"com.android.settings", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.android.vending", "com.miui.securitycenter", "com.samsung.android.lool", "com.samsung.accessibility",
            "com.google.android.marvin.talkback", "com.huawei.systemmanager", "com.coloros.safecenter", "com.oplus.safecenter"};
    private ForegroundApp foreground;
    private AudioManager audio;
    private boolean currentCall;     // napred je ekran poziva aplikacije kojoj pozivi uvek prolaze
    private boolean usageOk;
    private long usageCheckedAt;
    private Set<String> watched;     // paketi od kojih Pristupačnost šalje događaje 
    private boolean currentQuiet;    // napred je aplikacija bez ikakvog pravila (mape, kalkulator...)
    private boolean currentInApp;    // napred je pregledač unutar aplikacije
    private String rawPkg;           // stvarni paket na ekranu (pre prepoznavanja kopije)
    private Map<String, String> clones;
    private long clonesAt;
    private String pausePkg;         // aplikacija upravo otvorena koja pre prikaza čeka pauzu
    private String lastLeftPkg;      // aplikacija iz koje se upravo izašlo
    private long lastLeftAt;
    private boolean leftBlocked, leftPending, leftPause;
    private long exemptAt;
    private long protectToastAt;
    private int overlayFailures;
    private long overlayRetryAt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateWatched();
            // Dok je navigacija napred bez „Pristupa korišćenju“, njen prozor se ne čita ni povremeno;
            // prelazak u drugu aplikaciju javlja ta aplikacija.
            if (usageOk || !(currentQuiet && overlay == null && handsOff(rawPkg))) safeCheck();
            try {
                store.guardBeat(false);
            } catch (Throwable error) {
                GuardDiagnostics.report("heartbeat", error);
            }
            h.postDelayed(this, TICK_MS);
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
            safeCheck();
        }
    };

    final Runnable recheckSoon = new Runnable() {
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
    private final Runnable finishSiteExit = new Runnable() {
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
                accountUntilNow(Collections.emptySet());
                hideOverlay();
                if (store != null) {
                    store.flush();
                    store.guardBeat(true);
                }
            } else {
                h.removeCallbacks(recheckSoon);
                h.postDelayed(recheckSoon, 300);
            }
        }
    };

    /** Da li je korisnik uključio Čuvara u Pristupačnosti. */
    static boolean isEnabled(Context c) {
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
        store = Store.get(this);
        browserMonitor = new BrowserMonitor(this);
        overlayWindow = new GuardOverlayWindow(this);
        pauseOverlayContent = new PauseOverlayContent(this, h, store, new PauseOverlayContent.Actions() {
            @Override public boolean isVisible(View view) { return overlay == view; }
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

        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
                contentEvents = false;
                info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                info.notificationTimeout = 250;
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

        running = true;
        try {
            store.guardStarted();
        } catch (Throwable error) {
            GuardDiagnostics.report("guardStarted", error);
        }
        foreground = new ForegroundApp(this);
        if (usageOk) foreground.refresh(() -> h.post(this::safeCheck));
        updateWatched();
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
        if (!usageOk || store.focusActive()) {
            // Bez zapisa o korišćenju (ili tokom fokusa) prate se sve aplikacije sa ikonom i sistemski ekrani, ali ne i navigacija.
            want.addAll(launcherApps());
            want.add("android");
            want.add("com.android.systemui");
            Collections.addAll(want, SETTINGS_APPS);
        }
        want.add(getPackageName());
        want.addAll(exempt()); // početni ekran (i nedavne aplikacije u njemu), telefon, poruke
        want.addAll(BrowserMonitor.BROWSER_PACKAGES);
        want.addAll(browserMonitor.webApps());
        want.addAll(store.guardedApps());
        if (store.hasSiteRules() || store.focusHasSites()) Collections.addAll(want, IN_APP_APPS);
        if (store.protectNow()) Collections.addAll(want, SETTINGS_APPS);
        for (String p : HANDS_OFF) if (handsOff(p)) want.remove(p);
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
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence p = event.getPackageName(), cls = event.getClassName();
            if (p != null && store != null && !quiet(p.toString())) ruledSeenAt = System.currentTimeMillis();
            if (p != null && cls != null) {
                String c = cls.toString();
                // Pamti se samo ekran aplikacije, ne sistemski dijalozi i delovi ekrana.
                if (c.contains(".") && !c.startsWith("android.") && !c.startsWith("androidx.")) activityOf.put(p.toString(), c);
            }
        }
        if ((type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
                && currentQuiet && overlay == null) {
            // Napred je aplikacija bez pravila. Mape i slične aplikacije šalju ovakve događaje u nizu (pomeranje mape,
            // donji paneli), pa se ništa ne čita dok događaj dolazi iz iste aplikacije; promena aplikacije se proveri odmah.
            CharSequence p = event.getPackageName();
            if (handsOff(rawPkg) && (p == null || p.toString().equals(rawPkg))) return; // navigacija napred: ništa se ne proverava
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
                checkPending = true;
                h.postDelayed(throttled, 700); // pri skrolovanju stranice događaji stižu stalno: adresa se čita najviše ~1,4 puta u sekundi
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (store != null) {
            store.guardStopped();
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
        accountUntilNow(Collections.emptySet());
        hideOverlay();
        if (receiverOn) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Throwable ignored) {
            }
            receiverOn = false;
        }
        if (store != null) {
            store.flush();
        }
    }

    void safeCheck() {
        // Settle the OLD app/site before check() changes the observed context or shows a block.
        accountUntilNow(null);
        try {
            check();
        } catch (Throwable error) {
            observationValid = false;
            GuardDiagnostics.report("check", error);
        } finally {
            try {
                accountUntilNow(usageKeys());
            } catch (Throwable error) {
                accountUntilNow(Collections.emptySet());
                GuardDiagnostics.report("usageContext", error);
            }
        }
    }

    private Set<String> usageKeys() {
        Set<String> keys = new HashSet<>();
        if (!observationValid || store == null || power == null || keyguard == null || !power.isInteractive()
                || keyguard.isKeyguardLocked() || overlay != null || currentPkg == null) return keys;
        if (currentCall) return keys; // poziv ne troši limit aplikacije ni ukupni dnevni limit
        if (tracked(this, currentPkg)) keys.add(currentPkg);
        for (String host : visibleHosts) {
            // Meri se samo vreme sajtova sa pravilom (za limit); istorija ostalih sajtova se ne čuva.
            for (String domain : store.matchingSitesNow(host)) keys.add("site:" + domain);
        }
        if (countsForDayLimit()) keys.add(Store.GUARDED_KEY);
        return keys;
    }

    /**
     * Ukupni dnevni limit troši samo ono što on i zaključa: aplikacija sa pravilom ili sajt sa pravilom.
     * Mape, pozivi i sve ostalo bez pravila ga ne troše.
     */
    private boolean countsForDayLimit() {
        if (exempt().contains(currentPkg)) return false;
        if (guarded(currentPkg)) return true;
        for (String host : visibleHosts) if (store.siteGuarded(host) != null) return true;
        return false;
    }

    private void accountUntilNow(Set<String> nextKeys) {
        if (store == null) return;
        try {
            long elapsed = SystemClock.elapsedRealtime();
            long epoch = store.now();
            UsageTracker.Interval interval = nextKeys == null ? usageTracker.checkpoint(elapsed, epoch)
                    : usageTracker.transition(elapsed, epoch, nextKeys);
            if (interval == null) return;
            long dt = interval.toMs - interval.fromMs;
            for (String key : interval.keys) {
                store.addUsageBetween(key, interval.fromMs, interval.toMs);
                if (key.startsWith("site:") || key.startsWith("web:") || key.equals(Store.GUARDED_KEY)) continue;
                long left = store.addSession(key, dt);
                if (left > 0 && left <= 60000L && left + dt > 60000L) {
                    Toast.makeText(this, "Čuvar: još minut u komadu, pa pauza od "
                            + Store.SESSION_BREAK_MS / 60000L + " min.", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("usage", error);
        }
    }

    /**
     * Da li je napred poziv u aplikaciji kojoj pozivi uvek prolaze: ekran poziva (dolazni, odlazni, u toku)
     * ili internet poziv koji upravo traje (Telegram poziv nema poseban ekran).
     */
    private boolean callPasses(String pkg, String raw) {
        if (!store.callsPass(pkg)) return false;
        String cls = activityOf.get(raw);
        if (cls != null && callScreen(cls)) return true;
        try {
            return audio != null && audio.getMode() == AudioManager.MODE_IN_COMMUNICATION;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Ekran poziva po imenu (npr. VoipActivityV2, PhoneFragmentActivity, RtcCallActivity), a ne istorija poziva. */
    static boolean callScreen(String cls) {
        String n = cls.substring(cls.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (n.contains("log") || n.contains("history") || n.contains("link") || n.contains("setting")
                || n.contains("info") || n.contains("contact") || n.contains("picker") || n.contains("permission")) {
            return false;
        }
        return n.contains("call") || n.contains("voip") || n.startsWith("phone");
    }

    /** Glavna odluka: šta je na ekranu i da li to treba blokirati. */
    private void check() {
        observationValid = false;
        if (store == null || power == null || keyguard == null) {
            return;
        }
        if (!power.isInteractive()) {
            hideOverlay();
            return;
        }
        if (keyguard.isKeyguardLocked()) {
            hideOverlay();
            return;
        }

        // Aplikacija bez pravila (Mape, kalkulator...) se uopšte ne dira: koja je napred zna se od Androida
        // („Pristup korišćenju“), pa se njen prozor ne čita. Prozor se čita samo kad je napred aplikacija sa
        // pravilom, pregledač, podešavanja pod zaštitom ili kad se ne zna šta je napred.
        AccessibilityNodeInfo root = null;
        String pkg = null;
        if (usageOk && foreground != null && overlay == null) {
            String fg = foreground.current();
            // Android ponekad kasni sa zapisom: ako je posle njega javila aplikacija sa pravilom, čita se prozor.
            if (fg != null && !TRANSPARENT.contains(fg) && quiet(fg)
                    && (handsOff(fg) || (foreground.since() > ruledSeenAt && appWindowCount() < 2))) {
                pkg = fg;
            }
        }
        if (pkg == null) {
            // Prvo se uzme samo vrh ekrana, bez ostatka sadržaja: za aplikacije bez pravila to je sve što treba.
            root = Build.VERSION.SDK_INT >= 33 ? getRootInActiveWindow(0) : getRootInActiveWindow();
            pkg = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
            if (pkg != null && !pkg.equals(getPackageName())) ruledSeenAt = quiet(pkg) ? 0L : System.currentTimeMillis();
        }
        if (pkg == null && usageOk && foreground != null && overlay == null) {
            // Prozor se ne može pročitati: koju je aplikaciju Android poslednju pokrenuo.
            String fg = foreground.current();
            if (fg != null && quiet(fg)) pkg = fg;
        }
        if (pkg == null) {
            return;
        }
        if (TRANSPARENT.contains(pkg)) {
            return;
        }
        boolean quiet = overlay == null && quiet(pkg) && (handsOff(pkg) || appWindowCount() < 2);
        if (!quiet && root == null) return;
        // Ceo vrh ekrana (sa delom sadržaja) treba samo za pregledač unutar aplikacije, gde se adresa traži po ekranu.
        // U pregledačima se adresa čita direktno po polju za adresu, bez čitanja stranice.
        if (!quiet && Build.VERSION.SDK_INT >= 33 && inAppBrowser(pkg)) {
            AccessibilityNodeInfo full = getRootInActiveWindow();
            if (full != null && full.getPackageName() != null && pkg.equals(full.getPackageName().toString())) root = full;
        }
        currentQuiet = quiet;
        observationValid = true;
        visibleHosts.clear();
        secondaryContentPackages.clear();
        String raw = pkg;
        if (overlay != null && pkg.equals(getPackageName())) {
            if (currentPkg == null) return;
            pkg = currentPkg; // ponovo proveri pravila i dok je naš ekran preko aplikacije
            raw = rawPkg != null ? rawPkg : currentPkg;
        } else {
            pkg = cloneOf(pkg); // kopija aplikacije (npr. Instagram 2) potpada pod pravila originala
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
                opensBlocked = max > 0 && store.opensToday("app:" + pkg) >= max ? pkg : null;
                pendingOpen = opensBlocked == null && max > 0 ? pkg : null;
                pausePkg = store.appGuarded(pkg) && !exempt().contains(pkg) ? pkg : null;
            }
        }

        currentCall = false;
        if (quiet) {
            // Aplikacija bez pravila: samo se beleži da je napred (za merenje), bez čitanja ekrana i drugih prozora.
            currentInApp = false;
            currentHost = null;
            currentAddress = null;
            setContentEvents(false);
            hideOverlay();
            return;
        }

        if (store.rulesPaused()) {
            // Pauza svih pravila do sutra u 06:00: ništa se ne blokira i ne zatvara, vreme se i dalje meri.
            currentInApp = false;
            currentHost = null;
            currentAddress = null;
            setContentEvents(false);
            hideOverlay();
            return;
        }

        if (store.protectNow() && guardsSelf(root, pkg)) {
            // Ekran na kome bi Čuvar mogao da se isključi, zaustavi ili obriše: zatvori ga.
            hideOverlay();
            performGlobalAction(GLOBAL_ACTION_BACK);
            performGlobalAction(GLOBAL_ACTION_HOME);
            long nowEl = SystemClock.elapsedRealtime();
            if (nowEl - protectToastAt > 5000L) {
                protectToastAt = nowEl;
                Toast.makeText(this, "Čuvar je zaštićen od isključivanja. Zaštita se gasi u Čuvaru i važi tek od sledećeg jutra.",
                        Toast.LENGTH_LONG).show();
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
            hideOverlay();
            return;
        }

        String urlBarId = BrowserMonitor.addressViewId(pkg);
        boolean inApp = urlBarId == null && inAppBrowser(raw);
        if (inApp != currentInApp) {
            currentInApp = inApp;
            currentHost = null;
            currentAddress = null;
            setContentEvents(inApp || wantsContent(pkg));
        }
        AccessibilityNodeInfo addressRoot = null;
        if (urlBarId != null && !root.getPackageName().toString().equals(getPackageName())) {
            // Oblačić, meni ili savet preko stranice je poseban prozor bez adresne trake: adresa se čita iz glavnog prozora.
            addressRoot = browserAddressRoot(root, raw, urlBarId);
            currentAddress = browserMonitor.readAddress(addressRoot, urlBarId, activityOf.get(raw));
            currentHost = currentAddress.host();
        } else if (inApp && !root.getPackageName().toString().equals(getPackageName())) {
            String host = browserMonitor.inAppHost(root);
            currentHost = host;
        }
        boolean browsing = urlBarId != null || inApp;
        if (browsing && currentHost != null) visibleHosts.add(currentHost);

        // Sva pravila koja sada važe, od najstrožeg: ukupni dnevni limit (zajedničko vreme pod pravilima),
        // noć, ručni fokus, vremenski režim, pa aplikacija i sajt.
        // Otključavanje jedne stavke ne otvara ostale (otključan pregledač ne otvara blokiran sajt).
        List<Block> blocks = new ArrayList<>();
        String appKey = "app:" + pkg;
        boolean web = browsing && currentHost != null;
        int dayLimit = store.dayLimit();
        if (dayLimit > 0) {
            long used = store.guardedToday();
            if (DayLimit.warn(dayLimit, used) && store.firstDayLimitWarning()) {
                long left = dayLimit * 60000L - used;
                Toast.makeText(this, "Čuvar: do dnevnog limita ostalo je " + Ui.fmt(left) + ".",
                        Toast.LENGTH_LONG).show();
            }
            if (DayLimit.reached(dayLimit, used)) store.markDayLimitHit();
            if (DayLimit.reached(dayLimit, used) && !exempt().contains(pkg)) {
                String site = web ? store.siteGuarded(currentHost) : null;
                if (guarded(pkg)) {
                    blocks.add(new Block(appKey, KIND_DAY, null, false));
                } else if (site != null) {
                    blocks.add(new Block("site:" + site, KIND_DAY, null, false));
                }
            }
        }
        if (store.nightActive() && !exempt().contains(pkg)) {
            String site = web ? store.siteGuarded(currentHost) : null;
            if (guarded(pkg)) {
                blocks.add(new Block(appKey, KIND_NIGHT, null, false));
            } else if (site != null) {
                blocks.add(new Block("site:" + site, KIND_NIGHT, null, false));
            }
        }
        if (store.focusActive() && !exempt().contains(pkg)
                && !store.focusAllowsApp(pkg)
                && !(web && store.focusAllowsSite(currentHost))) {
            blocks.add(new Block(appKey, KIND_FOCUS, null, false));
        }
        if (pkg.equals(opensBlocked)) {
            blocks.add(new Block(appKey, KIND_OPENS, null, false));
        }
        if (store.sessionBreakLeft(pkg) > 0) {
            blocks.add(new Block(appKey, KIND_BREAK, null, false));
        }
        DailySchedule.Rule rule = store.scheduleBlockingApp(pkg);
        if (rule != null) {
            blocks.add(new Block(appKey, KIND_SCHEDULE, rule, false));
        }
        DailySchedule.Rule siteRule = web ? store.scheduleBlockingSite(currentHost) : null;
        if (siteRule != null) {
            blocks.add(new Block("site:" + DailySchedule.matchDomain(currentHost, siteRule.sites),
                    KIND_SCHEDULE, siteRule, false));
        }
        if (rule == null) {
            boolean lock = store.appLockNow(pkg);
            int limit = store.appLimitNow(pkg);
            boolean timeUp = limit > 0 && store.usedShared(pkg) >= limit * 60000L;
            // Van perioda režim sa dnevnom šifrom drži svoje aplikacije zaključane, a umesto PIN-a traži šifru.
            DailySchedule.Rule codeRule = store.codeRuleForApp(pkg);
            if (lock || timeUp || codeRule != null) {
                int k = timeUp ? KIND_TIME : codeRule != null ? KIND_CODE : KIND_LOCK;
                blocks.add(new Block(appKey, k, null, true));
            }
        }
        if (unknownBrowser(pkg)) {
            blocks.add(new Block(appKey, KIND_BROWSER, null, true));
        }
        if (cloner(pkg)) {
            blocks.add(new Block(appKey, KIND_CLONER, null, true));
        }
        if (web && siteRule == null) {
            DailySchedule.Rule codeRule = store.codeRuleForSite(currentHost);
            if (codeRule != null) {
                blocks.add(new Block("site:" + DailySchedule.matchDomain(currentHost, codeRule.sites),
                        KIND_CODE, null, true));
            }
        }
        if (inApp && currentHost == null && store.hasSiteRules()) {
            // Adresa se ne vidi, a postoje pravila za sajtove: bez ovoga bi se blokiran sajt otvorio preko linka u aplikaciji.
            blocks.add(new Block(appKey, KIND_INAPP, null, true));
        }
        if (currentAddress != null && currentAddress.unresolved(SystemClock.elapsedRealtime()) && store.hasSiteRules()) {
            blocks.add(new Block(appKey, KIND_ADDRESS, null, true));
        }
        if (browsing) for (String domain : store.matchingSitesNow(currentHost)) {
            int siteLimit = store.siteLimitNow(domain);
            if (siteLimit == 0) {
                blocks.add(new Block("site:" + domain, KIND_SITE, null, true));
            } else if (siteLimit > 0 && store.usedShared("site:" + domain) >= siteLimit * 60000L) {
                blocks.add(new Block("site:" + domain, KIND_SITE_TIME, null, true));
            }
        }

        // Inspect every visible window even while another rule wins, so all visible sites are timed.
        List<Block> secondary = new ArrayList<>();
        for (AppWindow other : otherAppWindows()) {
            if (isSameBrowserWindow(other, raw, urlBarId, addressRoot)) continue;
            Block b = appOnlyBlock(other.pkg);
            if (b != null) secondary.add(b);
            String id = BrowserMonitor.addressViewId(other.pkg);
            boolean embedded = id == null && inAppBrowser(other.rawPkg);
            if (id != null || embedded) secondaryContentPackages.add(other.rawPkg);
            String host = null;
            if (id != null) {
                BrowserAddressState address = browserMonitor.readAddress(other.root, id, activityOf.get(other.rawPkg));
                host = address.host();
                if (address.unresolved(SystemClock.elapsedRealtime()) && store.hasSiteRules()) {
                    secondary.add(new Block("app:" + other.pkg, KIND_ADDRESS, null, true));
                }
            } else if (embedded) {
                host = browserMonitor.inAppHost(other.root);
                if (host == null && store.hasSiteRules()) {
                    secondary.add(new Block("app:" + other.pkg, KIND_INAPP, null, true));
                }
            }
            if (host != null) {
                visibleHosts.add(host);
                if (store.focusActive() && !exempt().contains(other.pkg)
                        && !store.focusAllowsApp(other.pkg) && !store.focusAllowsSite(host)) {
                    secondary.add(new Block("app:" + other.pkg, KIND_FOCUS, null, false));
                }
                addSecondarySiteBlocks(secondary, other.pkg, host);
            } else if (store.focusActive() && !exempt().contains(other.pkg)
                    && !store.focusAllowsApp(other.pkg)) {
                secondary.add(new Block("app:" + other.pkg, KIND_FOCUS, null, false));
            }
        }
        blocks.addAll(secondary);
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
                        || (b.kind == KIND_ADDRESS && ("app:" + exitingBrowser).equals(b.key)));
            }
        }

        // Otključavanje dnevnom šifrom važi 5 minuta, a zatim sat vremena nema otključavanja (vidi Store),
        // osim jednog hitnog otključavanja dnevno. Režim i potrošen limit se nikad ne otključavaju.
        Block show = null;
        for (Block b : blocks) {
            if (hard(b.kind) || (store.unlockLeft(b.key) <= 0 && store.emergencyLeft(b.key) <= 0)) {
                show = b;
                break;
            }
        }
        if (show == null && pkg.equals(pausePkg)) {
            if (store.unlockLeft(appKey) > 0 || store.emergencyLeft(appKey) > 0) {
                pausePkg = null; // upravo otključano šifrom: bez još jedne pauze
            } else {
                show = new Block(appKey, KIND_PAUSE, null, false);
            }
        }
        long coolLeft = show == null || hard(show.kind) || show.kind == KIND_PAUSE ? 0 : store.cooldownLeft(show.key);

        if (show != null && show.kind != KIND_PAUSE && (overlay == null || !show.key.equals(overlayKey) || overlayKind == KIND_PAUSE)) {
            store.countOpen("try:" + show.key); // novi pokušaj da se otvori nešto blokirano
        }
        if (show == null && pkg.equals(pendingOpen)) {
            store.countOpen("app:" + pkg); // otvaranje se broji tek kad se aplikacija zaista vidi
            pendingOpen = null;
        }

        if (show != null) {
            showOverlay(show.key, show.kind, show.rule, coolLeft > 0, show.code);
            if (cooldownLabel != null && coolLeft > 0) {
                cooldownLabel.setText(cooldownText(coolLeft));
            }
        } else {
            hideOverlay();
            overlayFailures = 0;
            overlayRetryAt = 0L;
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

    private static final class AppWindow {
        final String pkg;
        final String rawPkg;
        final AccessibilityNodeInfo root;

        AppWindow(String pkg, String rawPkg, AccessibilityNodeInfo root) {
            this.pkg = pkg;
            this.rawPkg = rawPkg;
            this.root = root;
        }
    }

    /**
     * Prozor pregledača u kome je adresna traka. Kad je napred oblačić, meni ili savet pregledača (poseban mali
     * prozor bez trake), traži se glavni prozor istog pregledača na ekranu. Ako ga nema, ostaje prednji prozor.
     */
    private AccessibilityNodeInfo browserAddressRoot(AccessibilityNodeInfo root, String raw, String urlBarId) {
        if (hasNode(root, urlBarId)) return root;
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                if (w.getId() == root.getWindowId()) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null || !raw.equals(r.getPackageName().toString())) continue;
                if (hasNode(r, urlBarId)) return r;
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("addressWindow", error);
        }
        return root;
    }

    private static boolean hasNode(AccessibilityNodeInfo root, String id) {
        if (root == null) return false;
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            return nodes != null && !nodes.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Drugi prozor istog pregledača koji je već proveren (glavni prozor pročitan umesto oblačića)
     * ili je samo njegov oblačić/meni bez adresne trake. Pravi drugi prozor pregledača (podeljen ekran) ima traku.
     */
    private static boolean isSameBrowserWindow(AppWindow other, String raw, String urlBarId,
                                               AccessibilityNodeInfo addressRoot) {
        if (urlBarId == null || !other.rawPkg.equals(raw)) return false;
        if (addressRoot != null && other.root.getWindowId() == addressRoot.getWindowId()) return true;
        return !hasNode(other.root, urlBarId);
    }

    /** Other visible application windows; retain their roots to enforce website rules too. */
    private List<AppWindow> otherAppWindows() {
        List<AppWindow> out = new ArrayList<>();
        try {
            List<AccessibilityWindowInfo> apps = new ArrayList<>();
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w != null && w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) apps.add(w);
            }
            // Samo jedna aplikacija na ekranu (uobičajeno): ne pita se aplikacija za sadržaj bez potrebe.
            if (apps.size() < 2) return out;
            for (AccessibilityWindowInfo w : apps) {
                if (w.isActive() || w.isFocused()) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null) continue;
                String p = r.getPackageName().toString();
                if (p.equals(getPackageName()) || TRANSPARENT.contains(p)) continue;
                out.add(new AppWindow(cloneOf(p), p, r));
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("otherWindows", error);
        }
        return out;
    }

    private void addSecondarySiteBlocks(List<Block> blocks, String pkg, String host) {
        String guardedSite = store.siteGuarded(host);
        if (guardedSite != null && !exempt().contains(pkg)) {
            if (DayLimit.reached(store.dayLimit(), store.guardedToday())) {
                blocks.add(new Block("site:" + guardedSite, KIND_DAY, null, false));
            }
            if (store.nightActive()) blocks.add(new Block("site:" + guardedSite, KIND_NIGHT, null, false));
        }
        DailySchedule.Rule rule = store.scheduleBlockingSite(host);
        if (rule != null) {
            blocks.add(new Block("site:" + DailySchedule.matchDomain(host, rule.sites), KIND_SCHEDULE, rule, false));
        } else {
            DailySchedule.Rule codeRule = store.codeRuleForSite(host);
            if (codeRule != null) {
                blocks.add(new Block("site:" + DailySchedule.matchDomain(host, codeRule.sites), KIND_CODE, null, true));
            }
        }
        for (String domain : store.matchingSitesNow(host)) {
            int limit = store.siteLimitNow(domain);
            if (limit == 0) blocks.add(new Block("site:" + domain, KIND_SITE, null, true));
            else if (limit > 0 && store.usedShared("site:" + domain) >= limit * 60000L) {
                blocks.add(new Block("site:" + domain, KIND_SITE_TIME, null, true));
            }
        }
    }

    /** Blokada aplikacije koja je na ekranu, ali nije u fokusu (bez sajtova i broja otvaranja), ili null. */
    private Block appOnlyBlock(String p) {
        if (exempt().contains(p)) return null;
        String k = "app:" + p;
        Block b = null;
        int dayLimit = store.dayLimit();
        if (guarded(p) && DayLimit.reached(dayLimit, store.guardedToday())) {
            b = new Block(k, KIND_DAY, null, false);
        } else if (guarded(p) && store.nightActive()) {
            b = new Block(k, KIND_NIGHT, null, false);
        } else if (store.sessionBreakLeft(p) > 0) {
            b = new Block(k, KIND_BREAK, null, false);
        } else {
            DailySchedule.Rule rule = store.scheduleBlockingApp(p);
            if (rule != null) {
                b = new Block(k, KIND_SCHEDULE, rule, false);
            } else {
                int limit = store.appLimitNow(p);
                boolean timeUp = limit > 0 && store.usedShared(p) >= limit * 60000L;
                DailySchedule.Rule codeRule = store.codeRuleForApp(p);
                if (store.appLockNow(p) || timeUp || codeRule != null) {
                    b = new Block(k, timeUp ? KIND_TIME : codeRule != null ? KIND_CODE : KIND_LOCK, null, true);
                } else if (unknownBrowser(p)) {
                    b = new Block(k, KIND_BROWSER, null, true);
                } else if (cloner(p)) {
                    b = new Block(k, KIND_CLONER, null, true);
                }
            }
        }
        if (b != null && !hard(b.kind) && (store.unlockLeft(k) > 0 || store.emergencyLeft(k) > 0)) return null;
        return b;
    }

    /** Blokade koje se ne otključavaju: vremenski režim, potrošen limit aplikacije, sajta ili ukupni. */
    static boolean hard(int kind) {
        return kind == KIND_SCHEDULE || kind == KIND_DAY || kind == KIND_NIGHT || kind == KIND_TIME || kind == KIND_SITE_TIME
                || kind == KIND_OPENS || kind == KIND_BREAK || kind == KIND_FOCUS;
    }

    /** Aplikacija sa pravilom, ili pregledač koji Čuvar ne prati dok postoje pravila za sajtove. */
    private boolean guarded(String p) {
        return store.appGuarded(p) || unknownBrowser(p) || cloner(p);
    }

    /** Aplikacija za kloniranje drugih aplikacija; zaključana dok postoje pravila za aplikacije. */
    private boolean cloner(String p) {
        boolean known = false;
        for (String c : CLONERS) known |= p.startsWith(c);
        return known && !store.guardedApps().isEmpty();
    }

    /**
     * Kopija aplikacije sa drugim imenom paketa (npr. com.instagram.android2 ili „Instagram 2“ iz App Cloner-a)
     * vodi se kao original, da se pravila ne zaobiđu kloniranjem. Vraća paket originala ili isti paket.
     */
    private String cloneOf(String p) {
        long now = SystemClock.elapsedRealtime();
        if (clones == null || now - clonesAt > 60000L) {
            clones = new HashMap<>();
            clonesAt = now;
        }
        String known = clones.get(p);
        if (known != null) return known;
        String out = p;
        Set<String> guarded = store.guardedApps();
        if (!guarded.contains(p) && !exempt().contains(p) && !BrowserMonitor.isBrowser(p)) {
            String label = appLabel(p).toLowerCase(Locale.ROOT).trim();
            for (String g : guarded) {
                if (p.startsWith(g) && p.length() > g.length()) {
                    out = g;
                    break;
                }
                String gl = appLabel(g).toLowerCase(Locale.ROOT).trim();
                if (gl.length() >= 4 && !gl.equals(g) && label.length() > gl.length() && label.startsWith(gl)
                        && label.substring(gl.length()).matches("[\\s\\d()+.-]+")) {
                    out = g;
                    break;
                }
            }
        }
        clones.put(p, out);
        return out;
    }

    /** Da li je napred pregledač unutar aplikacije (link otvoren iz Instagrama, Facebooka, Messengera...). */
    private boolean inAppBrowser(String p) {
        if (BrowserMonitor.isBrowser(p) || p.equals(getPackageName())) return false;
        String a = activityOf.get(p);
        return BrowserMonitor.isInAppBrowserActivity(a);
    }

    /**
     * Pregledač u kome Čuvar ne može da pročita adresu (npr. Opera, DuckDuckGo). Dok postoje pravila za sajtove,
     * on je zaključan kao aplikacija, inače bi se blokirani sajtovi samo otvorili u njemu.
     */
    private boolean unknownBrowser(String p) {
        return browserMonitor.isUnknownBrowser(p, store.hasSiteRules());
    }

    /** Promene sadržaja trebaju za adresu u pregledaču i, dok je zaštita uključena, za ekrane podešavanja. */
    /**
     * Aplikacija na koju se ne odnosi nijedno pravilo ni provera: nije pregledač, nema pravilo, nije kopija ni
     * aplikacija za kloniranje, nije ekran podešavanja pod zaštitom i ne traje fokus.
     */
    private boolean quiet(String raw) {
        if (handsOff(raw)) return true;
        if (raw.equals(getPackageName()) || BrowserMonitor.isBrowser(raw) || store.focusActive()) return false;
        if (settingsLike(raw) && store.protectNow()) return false;
        if (inAppBrowser(raw)) return false;
        String p = cloneOf(raw);
        return p.equals(raw) && !guarded(p);
    }

    private boolean handsOff(String p) {
        return p != null && HANDS_OFF.contains(p) && (store == null || !store.appGuarded(p));
    }

    /** Sve aplikacije sa ikonom, za praćenje bez „Pristupa korišćenju“; osvežava se na 5 minuta. */
    private Set<String> launcherApps() {
        long now = SystemClock.elapsedRealtime();
        if (launcherApps == null || now - launcherAppsAt > 5 * 60000L) {
            Set<String> out = new HashSet<>();
            try {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(i, PackageManager.MATCH_ALL)) {
                    if (r.activityInfo != null) out.add(r.activityInfo.packageName);
                }
            } catch (Throwable ignored) {
            }
            launcherApps = out;
            launcherAppsAt = now;
        }
        return launcherApps;
    }

    /** Broj prozora aplikacija na ekranu (podeljen ekran, plutajući prozor); ne čita sadržaj aplikacija. */
    private int appWindowCount() {
        int n = 0;
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w != null && w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) n++;
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    private boolean wantsContent(String p) {
        return (BrowserMonitor.isBrowser(p) && (store == null || store.hasSiteRules() || store.focusHasSites()))
                || (settingsLike(p) && store != null && store.protectNow());
    }

    /** Podešavanja telefona, instalacija i brisanje aplikacija, Play prodavnica i slični sistemski ekrani. */
    private static boolean settingsLike(String p) {
        return p.contains("settings") || p.contains("packageinstaller") || p.contains("permissioncontroller")
                || p.contains("securitycenter") || p.contains("safecenter") || p.contains("accessibility")
                || p.equals("com.android.vending") || p.equals("com.samsung.android.lool");
    }

    /** Reči sa ekrana za brisanje aplikacije (u instalaciji i Play prodavnici se Čuvar sme samo ažurirati). */
    private static final String[] UNINSTALL = {"uninstall", "deinstal", "деинстал", "ukloni", "уклони", "obriši", "обриши",
            "izbriši", "избриши", "delete", "remove"};

    /** Da li sistemski ekran prikazuje Čuvara (podaci o aplikaciji, Pristupačnost, brisanje). */
    private boolean guardsSelf(AccessibilityNodeInfo root, String p) {
        if (p.equals(getPackageName()) || exempt().contains(p)) return false;
        if (!settingsLike(p)) return false;
        // Korisnik je iz Čuvara otvorio „Pristup korišćenju“ da ga uključi: taj ekran se ne zatvara dva minuta.
        if (SystemClock.elapsedRealtime() < usageSetupUntil && !usageOk) return false;
        boolean installer = p.contains("packageinstaller") || p.equals("com.android.vending");
        try {
            String label = getString(R.string.app_name);
            if (!mentions(root, label) && !mentions(root, "Чувар")) return false;
            if (!installer) return true;
            for (String w : UNINSTALL) {
                List<AccessibilityNodeInfo> n = root.findAccessibilityNodeInfosByText(w);
                if (n != null && !n.isEmpty()) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean hasText(AccessibilityNodeInfo root, String t) {
        try {
            List<AccessibilityNodeInfo> n = root.findAccessibilityNodeInfosByText(t);
            return n != null && !n.isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Da li se na ekranu pominje naziv (ali ne „čuvar ekrana“, koji je podešavanje ekrana). */
    private static boolean mentions(AccessibilityNodeInfo root, String name) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(name);
        if (nodes == null) return false;
        for (AccessibilityNodeInfo n : nodes) {
            CharSequence t = n == null ? null : n.getText();
            if (t == null) t = n == null ? null : n.getContentDescription();
            if (t == null) continue;
            String s = t.toString().toLowerCase(Locale.ROOT)
                    .replaceAll("(čuvar|чувар)[a-zа-я]* (ekrana|екрана)", "");
            if (s.contains("čuvar") || s.contains("чувар")) return true;
        }
        return false;
    }

    private static final Map<String, Boolean> TRACKED = new HashMap<>();

    /**
     * Meri se samo vreme aplikacija koje imaju ikonicu u meniju. Sistemski prozori bez ikonice
     * (biometrija, dozvole, instalacija) ne ulaze u statistiku ni u limite.
     */
    static synchronized boolean tracked(Context c, String pkg) {
        Boolean t = TRACKED.get(pkg);
        if (t == null) {
            try {
                t = c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
            } catch (Throwable e) {
                t = true;
            }
            TRACKED.put(pkg, t);
        }
        return t;
    }

    /** Početni ekran, Čuvar, pozivi i poruke: ne troše ukupni limit i nikad se zbog njega ne blokiraju, ni uz pravilo. */
    private Set<String> exempt() {
        long now = SystemClock.elapsedRealtime();
        if (exempt == null || now - exemptAt > 60000L) {
            exempt = exemptApps(this);
            exemptAt = now;
        }
        return exempt;
    }

    static Set<String> exemptApps(Context c) {
        Set<String> out = new HashSet<>();
        out.add(c.getPackageName());
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo r = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r != null && r.activityInfo != null) out.add(r.activityInfo.packageName);
        } catch (Throwable ignored) {
        }
        try {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null && tm.getDefaultDialerPackage() != null) out.add(tm.getDefaultDialerPackage());
        } catch (Throwable ignored) {
        }
        try {
            String sms = Telephony.Sms.getDefaultSmsPackage(c);
            if (sms != null) out.add(sms);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Jedno pravilo koje sada blokira aplikaciju ili sajt. */
    private static final class Block {
        final String key;
        final int kind;
        final DailySchedule.Rule rule;
        final boolean code;

        Block(String key, int kind, DailySchedule.Rule rule, boolean code) {
            this.key = key;
            this.kind = kind;
            this.rule = rule;
            this.code = code;
        }
    }

    static String cooldownText(long left) {
        long min = (left + 59999L) / 60000L;
        return "Otključavanje je iskorišćeno. Sledeće je moguće za " + min + " min.";
    }

    String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    // ---------- Ekran za blokadu ----------

    /** Blokada se odnosi na stranicu u pregledaču koji je na ekranu (sajt ili nepotvrđena adresa). */
    private boolean inBrowserPage(String key, int kind) {
        return (key.startsWith("site:") || kind == KIND_ADDRESS) && browserForCurrentBlock() != null;
    }

    String browserForCurrentBlock() {
        if (currentPkg != null && BrowserMonitor.isBrowser(currentPkg)) return currentPkg;
        return rawPkg != null && BrowserMonitor.isBrowser(rawPkg) ? rawPkg : null;
    }

    /**
     * Izlaz sa ekrana za blokadu. Blokiran sajt u pregledaču: ostani u pregledaču, samo skloni karticu
     * sa te stranice (otvori početnu Google stranu). Sve ostalo: početni ekran telefona.
     */
    void leaveBlock(String key, int kind) {
        boolean sitePage = inBrowserPage(key, kind);
        String browser = sitePage ? browserForCurrentBlock() : null;
        if (sitePage) {
            leavingSiteKey = key;
            leavingSiteBrowser = browser;
            leavingSiteUntil = SystemClock.elapsedRealtime() + 2500L;
            h.removeCallbacks(finishSiteExit);
            h.postDelayed(finishSiteExit, 2500L);
        }
        // Remove the accessibility window before starting browser navigation so Back feels immediate and
        // the browser can receive focus while we set its neutral URL.
        hideOverlay();
        if (sitePage) {
            openStartPage(browser);
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        h.removeCallbacks(recheckSoon);
        h.postDelayed(recheckSoon, 600);
    }

    private static final String START_PAGE = "https://www.google.com";

    /** Otvara neutralnu stranicu direktnim browser intent-om; ne menja fokus dok je accessibility overlay aktivan. */
    private void openStartPage(String browser) {
        startPageIntent(browser);
        h.removeCallbacks(recheckSoon);
        h.postDelayed(recheckSoon, 600);
    }

    private void startPageIntent(String browser) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(START_PAGE))
                    .setPackage(browser)
                    .putExtra("com.android.browser.application_id", getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    /** Omotač koji sistemsko „nazad“ (taster ili pokret) pretvara u izlaz na početnu stranu pregledača. */
    private View backCatcher(View inner, final String key, final int kind) {
        FrameLayout wrap = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) leaveBlock(key, kind);
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        wrap.setFocusable(true);
        wrap.setFocusableInTouchMode(true);
        wrap.addView(inner, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 16 šalje „nazad“ kao povratni poziv, ne kao taster.
            wrap.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                private android.window.OnBackInvokedDispatcher dispatcher;
                private android.window.OnBackInvokedCallback callback;

                @Override
                public void onViewAttachedToWindow(View v) {
                    v.requestFocus();
                    dispatcher = v.findOnBackInvokedDispatcher();
                    if (dispatcher != null) {
                        callback = () -> leaveBlock(key, kind);
                        dispatcher.registerOnBackInvokedCallback(
                                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (dispatcher != null && callback != null) {
                        dispatcher.unregisterOnBackInvokedCallback(callback);
                        dispatcher = null;
                        callback = null;
                    }
                }
            });
        }
        return wrap;
    }

    private void showOverlay(String key, int kind, DailySchedule.Rule rule, boolean cooling, boolean code) {
        if (overlay != null && key.equals(overlayKey) && kind == overlayKind && sameRule(rule, overlayRule)
                && cooling == overlayCooling && code == overlayCode) {
            return;
        }
        if (overlay == null && overlayRetryAt > SystemClock.elapsedRealtime()) return;
        hideOverlay();
        try {
            View v = blockOverlayContent.build(key, kind, rule, cooling, code);
            // Kod sajta u pregledaču naš ekran hvata i sistemsko „nazad“: inače bi ga dobio pregledač,
            // zatvorio karticu bez istorije i izašao iz aplikacije umesto da te vrati na početnu stranu.
            boolean catchBack = inBrowserPage(key, kind);
            if (catchBack) v = backCatcher(v, key, kind);
            overlayWindow.show(v, catchBack);
            overlay = v;
            overlayKey = key;
            overlayKind = kind;
            overlayRule = rule;
            overlayCooling = cooling;
            overlayCode = code;
            overlayFailures = 0;
            overlayRetryAt = 0L;
        } catch (Throwable t) {
            GuardDiagnostics.report("showOverlay", t);
            overlay = null;
            overlayKey = null;
            cooldownLabel = null;
            overlayFailures = Math.min(5, overlayFailures + 1);
            long delay = Math.min(30000L, 1000L << Math.min(overlayFailures - 1, 4));
            overlayRetryAt = SystemClock.elapsedRealtime() + delay;
            h.removeCallbacks(recheckSoon);
            h.postDelayed(recheckSoon, delay);
        }
    }

    private void hideOverlay() {
        if (overlay != null) {
            overlayWindow.hide(overlay);
            overlay = null;
            overlayKey = null;
            overlayKind = 0;
            overlayRule = null;
            overlayCooling = false;
            overlayCode = false;
        }
        cooldownLabel = null;
    }

    /** Isti režim sa istim nazivom i periodom; inače ekran za blokadu treba osvežiti. */
    private static boolean sameRule(DailySchedule.Rule a, DailySchedule.Rule b) {
        if (a == null || b == null) return a == b;
        return a.id.equals(b.id) && a.name.equals(b.name) && a.start == b.start && a.end == b.end
                && a.days == b.days && a.code == b.code;
    }

    void overlayGlobalBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }


}
