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
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
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

    private static final int KIND_LOCK = 1;       // aplikacija zaključana PIN-om
    private static final int KIND_TIME = 2;       // istekao dnevni limit aplikacije
    private static final int KIND_SITE = 3;       // sajt uvek blokiran
    private static final int KIND_SITE_TIME = 4;  // istekao dnevni limit sajta
    private static final int KIND_SCHEDULE = 5;
    private static final int KIND_CODE = 6;       // van perioda režima, otključava se dnevnom šifrom
    private static final int KIND_NIGHT = 8;      // noćna blokada od 22:00, ništa se ne otključava do 06:00
    private static final int KIND_DAY = 7;        // potrošen ukupni dnevni limit, ništa se ne otključava do ponoći
    private static final int KIND_OPENS = 9;      // potrošena otvaranja aplikacije za danas
    private static final int KIND_PAUSE = 11;     // kratka pauza pre otvaranja aplikacije sa pravilom, posle nje se nastavlja
    static final int PAUSE_SECONDS = 6;
    private static final int KIND_BREAK = 12;     // obavezna pauza posle najdužeg korišćenja u komadu
    private static final int KIND_CLONER = 13;    // aplikacija za kloniranje (Parallel Space i sl.), dok postoje pravila za aplikacije
    private static final int KIND_INAPP = 14;     // pregledač unutar aplikacije (Instagram, Facebook) u kome se ne vidi adresa
    private static final int KIND_ADDRESS = 15;   // podržan pregledač, ali adresa još nije potvrđena
    private static final int KIND_FOCUS = 16;     // ručni režim: dozvoljene su samo izabrane aplikacije/sajtovi

    /** Aplikacije koje pokreću kopije drugih aplikacija pod svojim imenom, pa ih Čuvar ne bi prepoznao. */
    private static final String[] CLONERS = {"com.lbe.parallel", "com.parallel.space", "com.excelliance.multiaccount",
            "com.excelliance.dualaid", "com.ludashi.dualspace", "com.ludashi.superclone", "com.polestar.multiaccount",
            "com.polestar.domultiple", "com.jumobile.multiapp", "com.applisto.appcloner", "com.dualspace", "com.multiple.account",
            "com.trendmicro.dualapps", "do.multiple.cloner", "com.cloneapp", "com.waxmoon.ma.gp", "com.oasisfeng.island",
            "com.lody.virtual", "io.va.exposed", "com.vmos"};

    /** Ekran pregledača unutar aplikacije, po imenu ekrana (npr. BrowserLiteActivity kod Instagrama i Facebooka). */
    private static final java.util.regex.Pattern IN_APP_BROWSER = java.util.regex.Pattern.compile(
            "(?i).*(browserlite|inappbrowser|browseractivity|webviewactivity|webbrowser|customtab).*");
    private static final java.util.regex.Pattern DOMAIN = java.util.regex.Pattern.compile(
            "(?i)^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(:[0-9]+)?([/?#]\\S*)?$");
    private static final int KIND_BROWSER = 10;   // pregledač u kome Čuvar ne vidi adresu, dok postoje pravila za sajtove

    /** Pregledači i ID polja sa adresom u svakom od njih. */
    private static final Map<String, String> BROWSERS = new HashMap<>();
    /** Sistemski prozori koji se pojave preko aplikacije, a ne znače da je korisnik izašao iz nje. */
    private static final Set<String> TRANSPARENT = new HashSet<>();

    static {
        BROWSERS.put("com.android.chrome", "com.android.chrome:id/url_bar");
        BROWSERS.put("com.chrome.beta", "com.chrome.beta:id/url_bar");
        BROWSERS.put("com.brave.browser", "com.brave.browser:id/url_bar");
        BROWSERS.put("com.microsoft.emmx", "com.microsoft.emmx:id/url_bar");
        BROWSERS.put("com.vivaldi.browser", "com.vivaldi.browser:id/url_bar");
        BROWSERS.put("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text");
        BROWSERS.put("org.mozilla.firefox", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view");
        BROWSERS.put("com.chrome.dev", "com.chrome.dev:id/url_bar");
        BROWSERS.put("com.chrome.canary", "com.chrome.canary:id/url_bar");
        BROWSERS.put("com.kiwibrowser.browser", "com.kiwibrowser.browser:id/url_bar");
        TRANSPARENT.add("com.android.systemui");
        TRANSPARENT.add("android");
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private Store store;
    private WindowManager wm;
    private PowerManager power;
    private KeyguardManager keyguard;
    private AudioManager audio;
    private AudioFocusRequest silence; // drži zvuk utišanim dok je ekran za blokadu prikazan
    private boolean receiverOn;

    private String currentPkg;    // aplikacija koja je trenutno na ekranu
    private String currentHost;   // host trenutno otvoren u pregledaču (za vremenske režime)
    private DailySchedule.Rule overlayRule; // režim prikazan na ekranu za blokadu
    private final UsageTracker usageTracker = new UsageTracker(3 * TICK_MS);
    private final Set<String> visibleHosts = new HashSet<>();
    private final Set<String> secondaryContentPackages = new HashSet<>();
    private final Map<String, BrowserAddressState> browserAddresses = new LinkedHashMap<String, BrowserAddressState>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BrowserAddressState> entry) {
            return size() > 32;
        }
    };
    private BrowserAddressState currentAddress;
    private boolean observationValid;
    private boolean checkPending;
    private boolean contentEvents = true; // da li stižu i događaji o promeni sadržaja (samo za pregledače)
    private long lastEventCheck;

    private View overlay;
    private String overlayKey;
    private int overlayKind;
    private boolean overlayCooling;   // prikazana je pauza posle otključavanja
    private boolean overlayCode;      // otključava se dnevnom šifrom umesto PIN-om
    private TextView cooldownLabel;  // odbrojavanje do sledećeg mogućeg otključavanja
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
    private boolean usageOk;
    private long usageCheckedAt;
    private Set<String> watched;     // paketi od kojih Pristupačnost šalje događaje (null = sve)
    private boolean currentQuiet;    // napred je aplikacija bez ikakvog pravila (mape, kalkulator...)
    private boolean currentInApp;    // napred je pregledač unutar aplikacije
    private String rawPkg;           // stvarni paket na ekranu (pre prepoznavanja kopije)
    private Map<String, String> clones;
    private long clonesAt;
    private String pausePkg;         // aplikacija upravo otvorena koja pre prikaza čeka pauzu
    private long pauseShownAt;
    private String lastLeftPkg;      // aplikacija iz koje se upravo izašlo
    private long lastLeftAt;
    private boolean leftBlocked, leftPending, leftPause;
    private long exemptAt;
    private Set<String> webApps;     // sve aplikacije koje otvaraju veb adrese (pregledači)
    private long webAppsAt;
    private long protectToastAt;
    private int overlayFailures;
    private long overlayRetryAt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateWatched();
            safeCheck();
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
                    String fg = foreground.current();
                    if (fg != null && !fg.equals(rawPkg) && !TRANSPARENT.contains(fg)) safeCheck();
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

    private final Runnable recheckSoon = new Runnable() {
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
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
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
                info.notificationTimeout = 100;
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
        Set<String> want = null;
        if (usageOk && !store.focusActive()) {
            want = new HashSet<>();
            want.add(getPackageName());
            want.addAll(exempt()); // početni ekran (i nedavne aplikacije u njemu), telefon, poruke
            want.addAll(BROWSERS.keySet());
            want.addAll(webApps());
            want.addAll(store.guardedApps());
            if (store.hasSiteRules() || store.focusHasSites()) Collections.addAll(want, IN_APP_APPS);
            if (store.protectNow()) Collections.addAll(want, SETTINGS_APPS);
        }
        if (want == null ? watched == null : want.equals(watched)) return;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) return;
            info.packageNames = want == null ? null : want.toArray(new String[0]);
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
                h.postDelayed(throttled, 200);
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

    private void safeCheck() {
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
        if (tracked(this, currentPkg)) keys.add(currentPkg);
        for (String host : visibleHosts) {
            for (String domain : store.matchingSitesNow(host)) keys.add("site:" + domain);
            String web = Store.mainDomain(host);
            if (web != null) keys.add("web:" + web);
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

        // Prvo se uzme samo vrh ekrana, bez ostatka sadržaja: za aplikacije bez pravila to je sve što treba.
        AccessibilityNodeInfo root = Build.VERSION.SDK_INT >= 33 ? getRootInActiveWindow(0) : getRootInActiveWindow();
        String pkg = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
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
        boolean quiet = overlay == null && quiet(pkg) && appWindowCount() < 2;
        if (!quiet && root == null) return;
        if (!quiet && Build.VERSION.SDK_INT >= 33) {
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

        if (quiet) {
            // Aplikacija bez pravila: samo se beleži da je napred (za merenje), bez čitanja ekrana i drugih prozora.
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

        String urlBarId = BROWSERS.get(pkg);
        boolean inApp = urlBarId == null && inAppBrowser(raw);
        if (inApp != currentInApp) {
            currentInApp = inApp;
            currentHost = null;
            currentAddress = null;
            setContentEvents(inApp || wantsContent(pkg));
        }
        if (urlBarId != null && !root.getPackageName().toString().equals(getPackageName())) {
            currentAddress = readAddress(root, urlBarId);
            currentHost = currentAddress.host();
        } else if (inApp && !root.getPackageName().toString().equals(getPackageName())) {
            String host = inAppHost(root);
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
            Block b = appOnlyBlock(other.pkg);
            if (b != null) secondary.add(b);
            String id = BROWSERS.get(other.pkg);
            boolean embedded = id == null && inAppBrowser(other.rawPkg);
            if (id != null || embedded) secondaryContentPackages.add(other.rawPkg);
            String host = null;
            if (id != null) {
                BrowserAddressState address = readAddress(other.root, id);
                host = address.host();
                if (address.unresolved(SystemClock.elapsedRealtime()) && store.hasSiteRules()) {
                    secondary.add(new Block("app:" + other.pkg, KIND_ADDRESS, null, true));
                }
            } else if (embedded) {
                host = inAppHost(other.root);
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
    private static boolean hard(int kind) {
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
        if (!guarded.contains(p) && !exempt().contains(p) && !BROWSERS.containsKey(p)) {
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
        if (BROWSERS.containsKey(p) || p.equals(getPackageName())) return false;
        String a = activityOf.get(p);
        return a != null && IN_APP_BROWSER.matcher(a).matches();
    }

    /**
     * Adresa u gornjem delu ekrana pregledača unutar aplikacije (ispod naslova strane piše domen).
     * Sadržaj same strane se ne čita, da se ne opterećuje telefon.
     */
    private String inAppHost(AccessibilityNodeInfo root) {
        android.graphics.Rect r = new android.graphics.Rect();
        root.getBoundsInScreen(r);
        int limitY = r.top + Math.max(r.height() / 4, Ui.dp(this, 160));
        java.util.ArrayDeque<AccessibilityNodeInfo> q = new java.util.ArrayDeque<>();
        q.add(root);
        int seen = 0;
        try {
            while (!q.isEmpty() && seen++ < 300) {
                AccessibilityNodeInfo n = q.poll();
                if (n == null) continue;
                n.getBoundsInScreen(r);
                if (r.top > limitY) continue;
                CharSequence cls = n.getClassName();
                if (cls != null && cls.toString().contains("WebView")) continue;
                for (CharSequence t : new CharSequence[]{n.getText(), n.getContentDescription()}) {
                    if (t == null || t.length() > 200) continue;
                    String s = t.toString().trim();
                    if (DOMAIN.matcher(s).matches()) {
                        String host = Store.hostOf(s);
                        if (host != null) return host;
                    }
                }
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo child = n.getChild(i);
                    if (child != null) q.add(child);
                }
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("inAppAddress", error);
        }
        return null;
    }

    /**
     * Pregledač u kome Čuvar ne može da pročita adresu (npr. Opera, DuckDuckGo). Dok postoje pravila za sajtove,
     * on je zaključan kao aplikacija, inače bi se blokirani sajtovi samo otvorili u njemu.
     */
    private boolean unknownBrowser(String p) {
        if (BROWSERS.containsKey(p) || p.equals(getPackageName()) || !webApps().contains(p)) return false;
        return store.hasSiteRules();
    }

    /** Aplikacije koje mogu da otvore bilo koju veb adresu; osvežava se na svakih 5 minuta. */
    private Set<String> webApps() {
        long now = SystemClock.elapsedRealtime();
        if (webApps == null || now - webAppsAt > 5 * 60000L) {
            Set<String> out = new HashSet<>();
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://example.com/"));
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(i, PackageManager.MATCH_ALL)) {
                    if (r.activityInfo != null) out.add(r.activityInfo.packageName);
                }
            } catch (Throwable ignored) {
            }
            out.remove("android"); // izbor aplikacije, nije pregledač
            webApps = out;
            webAppsAt = now;
        }
        return webApps;
    }

    /** Promene sadržaja trebaju za adresu u pregledaču i, dok je zaštita uključena, za ekrane podešavanja. */
    /**
     * Aplikacija na koju se ne odnosi nijedno pravilo ni provera: nije pregledač, nema pravilo, nije kopija ni
     * aplikacija za kloniranje, nije ekran podešavanja pod zaštitom i ne traje fokus.
     */
    private boolean quiet(String raw) {
        if (raw.equals(getPackageName()) || BROWSERS.containsKey(raw) || store.focusActive()) return false;
        if (settingsLike(raw) && store.protectNow()) return false;
        if (inAppBrowser(raw)) return false;
        String p = cloneOf(raw);
        return p.equals(raw) && !guarded(p);
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
        return (BROWSERS.containsKey(p) && (store == null || store.hasSiteRules() || store.focusHasSites()))
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
        if (p.equals(getPackageName()) || !settingsLike(p) || exempt().contains(p)) return false;
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

    private static String cooldownText(long left) {
        long min = (left + 59999L) / 60000L;
        return "Otključavanje je iskorišćeno. Sledeće je moguće za " + min + " min.";
    }

    /** Never infer an address from page text. Preserve a verified host only within the same window. */
    private BrowserAddressState readAddress(AccessibilityNodeInfo root, String id) {
        String key = root.getPackageName() + ":" + root.getWindowId();
        BrowserAddressState state = browserAddresses.get(key);
        if (state == null) {
            state = new BrowserAddressState();
            browserAddresses.put(key, state);
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            state.missing(SystemClock.elapsedRealtime());
            return state;
        }
        AccessibilityNodeInfo n = nodes.get(0);
        if (n == null) {
            state.missing(SystemClock.elapsedRealtime());
        } else if (n.isFocused()) {
            state.editing();
        } else {
            CharSequence text = n.getText();
            state.readable(Store.hostOf(text == null ? "" : text.toString()));
        }
        return state;
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    // ---------- Ekran za blokadu ----------

    private void showOverlay(String key, int kind, DailySchedule.Rule rule, boolean cooling, boolean code) {
        if (overlay != null && key.equals(overlayKey) && kind == overlayKind && sameRule(rule, overlayRule)
                && cooling == overlayCooling && code == overlayCode) {
            return;
        }
        if (overlay == null && overlayRetryAt > SystemClock.elapsedRealtime()) return;
        hideOverlay();
        try {
            View v = buildOverlay(key, kind, rule, cooling, code);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(v, lp);
            silenceMedia();
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

    /**
     * Blokirana aplikacija ostaje ispod ekrana za blokadu, pa bi video ili muzika nastavili da idu.
     * Zato se pošalje „pauza“ i uzme zvuk za sebe dok je ekran prikazan (YouTube, Spotify i slični tada stanu).
     */
    private void silenceMedia() {
        if (audio == null) return;
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
        } catch (Throwable error) {
            GuardDiagnostics.report("mediaPause", error);
        }
        try {
            if (silence == null) {
                silence = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(change -> { })
                        .build();
                audio.requestAudioFocus(silence);
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("audioFocus", error);
            silence = null;
        }
    }

    private void releaseMedia() {
        if (audio == null || silence == null) return;
        try {
            audio.abandonAudioFocusRequest(silence);
        } catch (Throwable error) {
            GuardDiagnostics.report("releaseAudioFocus", error);
        }
        silence = null;
    }

    private void hideOverlay() {
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Throwable error) {
                GuardDiagnostics.report("hideOverlay", error);
            }
            overlay = null;
            overlayKey = null;
            overlayKind = 0;
            releaseMedia();
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

    /**
     * Pauza od nekoliko sekundi pre otvaranja aplikacije sa pravilom. Istraživanja pokazuju da ovakav kratak zastoj
     * sa lakim izlazom smanjuje otvaranja više od samih zabrana. Otvaranje se broji tek kad se pređe pauza.
     */
    private View buildPause(final String key) {
        final Context c = this;
        String pkg = key.substring(4);
        String name = appLabel(pkg);
        pauseShownAt = SystemClock.elapsedRealtime();

        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Ui.NIGHT);
        scroll.setFillViewport(true);
        scroll.setClickable(true);
        LinearLayout box = Ui.column(c);
        box.setGravity(Gravity.CENTER);
        int p = Ui.dp(c, 24);
        box.setPadding(p, p, p, p);

        TextView eyebrow = Ui.text(c, "ČUVAR", 12, Ui.NIGHT_ACCENT, true);
        eyebrow.setLetterSpacing(0.25f);
        eyebrow.setGravity(Gravity.CENTER);
        box.addView(eyebrow, Ui.fill(c, 0));

        TextView t = Ui.text(c, "Zastani na trenutak", 25, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, Ui.fill(c, 10));

        StringBuilder sb = new StringBuilder("Otvaraš ").append(name).append(". Danas si ga koristio ")
                .append(Ui.fmt(store.usedShared(pkg)));
        int limit = store.appLimitNow(pkg);
        if (limit > 0) sb.append(" od ").append(limit).append(" min");
        int opens = store.appOpensNow(pkg);
        if (opens > 0) {
            sb.append(", a otvorio ").append(Ui.count(store.opensToday(key), "put", "puta", "puta"))
                    .append(" od ").append(opens);
        }
        sb.append(". Da li ti zaista treba sada?");
        TextView s = Ui.text(c, sb.toString(), 15, Ui.NIGHT_MUTED, false);
        s.setGravity(Gravity.CENTER);
        box.addView(s, Ui.fill(c, 8));

        TextView close = overlayButton(c, "Zatvori");
        close.setTextSize(17);
        close.setBackground(Ui.pressable(Ui.ACCENT, Ui.ACCENT_DOWN, Ui.dp(c, 14)));
        close.setOnClickListener(v -> {
            pausePkg = null;
            performGlobalAction(GLOBAL_ACTION_HOME);
            h.removeCallbacks(recheckSoon);
            h.postDelayed(recheckSoon, 600);
        });
        box.addView(close, Ui.fill(c, 24));

        final TextView go = overlayButton(c, "");
        go.setTextColor(Ui.NIGHT_MUTED);
        go.setBackground(null);
        go.setEnabled(false);
        box.addView(go, Ui.fill(c, 16));
        final View self = scroll;
        Runnable count = new Runnable() {
            @Override
            public void run() {
                if (overlay != self) return;
                long left = PAUSE_SECONDS * 1000L - (SystemClock.elapsedRealtime() - pauseShownAt);
                if (left > 0) {
                    go.setText("Nastavi za " + ((left + 999L) / 1000L) + " s");
                    h.postDelayed(this, 250L);
                } else {
                    go.setText("Nastavi u " + name);
                    go.setEnabled(true);
                }
            }
        };
        go.setText("Nastavi za " + PAUSE_SECONDS + " s");
        h.postDelayed(count, 250L);
        go.setOnClickListener(v -> {
            if (SystemClock.elapsedRealtime() - pauseShownAt < PAUSE_SECONDS * 1000L) return;
            pausePkg = null;
            safeCheck();
        });

        scroll.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View buildOverlay(final String key, int kind, DailySchedule.Rule rule, boolean cooling, final boolean code) {
        if (kind == KIND_PAUSE) return buildPause(key);
        final Context c = this;
        final boolean isSite = key.startsWith("site:");
        String name = isSite ? key.substring(5) : appLabel(key.substring(4));

        String title;
        String sub;
        String joke;
        if (kind == KIND_NIGHT) {
            title = "Noćna blokada";
            sub = "Od " + DailyCode.LOCK_HOUR + ":00 do 0" + DailyCode.NIGHT_END_HOUR + ":00 sve iz tvojih pravila je zaključano. "
                    + name + " se ne otvara do jutra, ni šifrom.";
            joke = Jokes.pick(Jokes.SCHEDULE);
        } else if (kind == KIND_DAY) {
            title = "Dnevni limit je potrošen";
            sub = "Danas si u aplikacijama i na sajtovima iz svojih pravila proveo "
                    + Ui.fmt(store.guardedToday()) + ", a limit je " + DayLimit.label(store.dayLimit()) + ". " + name
                    + " je zaključan do ponoći i ne može da se otključa, ni šifrom.";
            joke = Jokes.pick(Jokes.TIME_UP);
        } else if (kind == KIND_OPENS) {
            String pkg = key.substring(4);
            title = "Otvaranja za danas su potrošena";
            sub = name + " si danas otvorio " + Ui.count(store.opensToday(key), "put", "puta", "puta") + ", a dozvoljeno je "
                    + store.appOpensNow(pkg) + ". Do ponoći se ne otvara, ni šifrom.";
            joke = Jokes.pick(Jokes.TIME_UP);
        } else if (kind == KIND_BREAK) {
            String pkg = key.substring(4);
            long left = store.sessionBreakLeft(pkg);
            title = "Vreme je za pauzu";
            sub = "Bio si u " + name + " " + store.appSessionNow(pkg) + " min u komadu. "
                    + name + " se ponovo otvara posle pauze, za " + Ui.fmt(left) + ", ni šifrom ranije.";
            joke = Jokes.pick(Jokes.TIME_UP);
        } else if (kind == KIND_FOCUS) {
            title = "Fokus režim je aktivan";
            long left = store.focusLeft();
            sub = "Dozvoljene su samo aplikacije i sajtovi koje si izabrao. Fokus traje još "
                    + Ui.fmt(left) + " i ne može se zaobići dnevnom šifrom.";
            joke = Jokes.pick(Jokes.SCHEDULE);
        } else if (kind == KIND_SCHEDULE && rule != null) {
            title = "Režim „" + rule.name + "“ je aktivan";
            sub = name + " je blokiran " + rule.daysLabel() + " od " + DailySchedule.label(rule.start)
                    + " do " + DailySchedule.label(rule.end) + "."
                    + (rule.code ? " Posle toga se otključava dnevnom šifrom." : "");
            joke = Jokes.pick(Jokes.SCHEDULE);
        } else if (kind == KIND_CODE) {
            title = name + " je zaključan";
            sub = "Unesi dnevnu šifru. Važi od " + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00 i vidi se u Čuvaru.";
            joke = Jokes.pick(Jokes.LOCK);
        } else if (kind == KIND_BROWSER) {
            title = name + " je zaključan";
            sub = "U ovom pregledaču Čuvar ne vidi koji je sajt otvoren, pa je zaključan dok imaš pravila za sajtove. "
                    + "Koristi Chrome, Samsung Internet, Firefox, Edge, Brave ili Vivaldi. Otvara se dnevnom šifrom, od "
                    + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00.";
            joke = Jokes.pick(Jokes.LOCK);
        } else if (kind == KIND_CLONER) {
            title = name + " je zaključan";
            sub = "U aplikaciji za kloniranje bi se zaključane aplikacije otvarale pod drugim imenom. "
                    + "Zato je zaključana dok imaš pravila za aplikacije. Otvara se dnevnom šifrom, od "
                    + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00.";
            joke = Jokes.pick(Jokes.LOCK);
        } else if (kind == KIND_INAPP) {
            title = "Link je zaključan";
            sub = "U pregledaču unutar aplikacije " + name + " Čuvar ne vidi koji je sajt otvoren, a imaš pravila za sajtove. "
                    + "Vrati se nazad ili otvori link u Chrome-u (meni sa tri tačke, „Otvori u pregledaču“). "
                    + "Otvara se i dnevnom šifrom, od " + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00.";
            joke = Jokes.pick(Jokes.SITE);
        } else if (kind == KIND_ADDRESS) {
            title = "Adresa sajta nije dostupna";
            sub = "Čuvar još nije mogao da pročita adresu u " + name + ", a imaš pravila za sajtove. "
                    + "Vrati se nazad i prikaži adresnu traku ili otvori sajt u drugom podržanom pregledaču. "
                    + "Otključavanje dnevnom šifrom važi kao i za pregledač bez dostupne adrese.";
            joke = Jokes.pick(Jokes.SITE);
        } else if (kind == KIND_LOCK) {
            title = name + " je zaključan";
            sub = "Otvara se dnevnom šifrom, od " + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00. Šifra se vidi u Čuvaru.";
            joke = Jokes.pick(Jokes.LOCK);
        } else if (kind == KIND_SITE) {
            title = "Sajt je blokiran";
            sub = name + " je na tvojoj listi blokiranih sajtova. Otvara se dnevnom šifrom, od "
                    + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00.";
            joke = Jokes.pick(Jokes.SITE);
        } else {
            title = "Vreme je isteklo";
            long used = store.usedShared(isSite ? key : key.substring(4));
            sub = "Danas si na " + name + " proveo " + Ui.fmt(used) + ". Dnevni limit je potrošen i "
                    + name + " je zaključan do ponoći, ni šifrom se ne otvara.";
            joke = Jokes.pick(Jokes.TIME_UP);
        }

        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Ui.NIGHT);
        scroll.setFillViewport(true);
        scroll.setClickable(true);

        LinearLayout box = Ui.column(c);
        box.setGravity(Gravity.CENTER);
        int p = Ui.dp(c, 24);
        box.setPadding(p, p, p, p);

        TextView eyebrow = Ui.text(c, "ČUVAR", 12, Ui.NIGHT_ACCENT, true);
        eyebrow.setLetterSpacing(0.25f);
        eyebrow.setGravity(Gravity.CENTER);
        box.addView(eyebrow, Ui.fill(c, 0));

        TextView t = Ui.text(c, title, 25, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, Ui.fill(c, 10));

        TextView s = Ui.text(c, sub, 15, Ui.NIGHT_MUTED, false);
        s.setGravity(Gravity.CENTER);
        box.addView(s, Ui.fill(c, 8));

        int tries = store.opensToday("try:" + key);
        if (tries > 1) {
            TextView tr = Ui.text(c, "Ovo ti je danas " + tries + ". pokušaj.", 15, 0xFFFFFFFF, true);
            tr.setGravity(Gravity.CENTER);
            box.addView(tr, Ui.fill(c, 10));
        }

        TextView j = Ui.text(c, joke, 16, Ui.NIGHT_ACCENT, false);
        j.setGravity(Gravity.CENTER);
        j.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        box.addView(j, Ui.fill(c, 16));

        // Najveće dugme na ekranu je izlaz: najlakši izbor je da odustaneš.
        TextView close = overlayButton(c, "Zatvori");
        close.setTextSize(17);
        close.setBackground(Ui.pressable(Ui.ACCENT, Ui.ACCENT_DOWN, Ui.dp(c, 14)));
        close.setOnClickListener(v -> {
            performGlobalAction(GLOBAL_ACTION_HOME);
            h.removeCallbacks(recheckSoon);
            h.postDelayed(recheckSoon, 600);
        });
        box.addView(close, Ui.fill(c, 20));

        if (cooling) {
            // Pauza posle otključavanja: nema dugmeta ni PIN-a, samo odbrojavanje.
            cooldownLabel = Ui.text(c, cooldownText(store.cooldownLeft(key)), 15, 0xFFFFFFFF, true);
            cooldownLabel.setGravity(Gravity.CENTER);
            box.addView(cooldownLabel, Ui.fill(c, 22));
            if (store.emergencyAvailable()) {
                final LinearLayout urgent = Ui.column(c);
                urgent.setGravity(Gravity.CENTER_HORIZONTAL);
                box.addView(urgent, Ui.fill(c, 16));
                TextView ask = overlayButton(c, "Hitno otključavanje (" + Store.EMERGENCY_MS / 60000L + " min)");
                ask.setTextColor(Ui.NIGHT_MUTED);
                ask.setBackground(null);
                ask.setOnClickListener(v -> showQuiz(urgent, () -> showEmergencyPad(urgent, key, code), null));
                urgent.addView(ask);
            }
        } else if (code && !hard(kind)) {
            final LinearLayout unlock = Ui.column(c);
            unlock.setGravity(Gravity.CENTER_HORIZONTAL);
            box.addView(unlock, Ui.fill(c, 22));
            // Zaključana aplikacija odmah traži odgovor pa PIN; kod isteklog vremena i blokiranog sajta prvo pitamo.
            if (kind == KIND_LOCK || kind == KIND_CODE || kind == KIND_BROWSER || kind == KIND_CLONER) {
                showQuiz(unlock, () -> showPinPad(unlock, key, code), null);
            } else {
                TextView ask = overlayButton(c, "Ipak želim da otključam");
                ask.setTextColor(Ui.NIGHT_MUTED);
                ask.setBackground(null);
                ask.setOnClickListener(v -> showAreYouSure(unlock, key, code));
                unlock.addView(ask);
            }
        }

        LinearLayout actions = Ui.row(c);
        actions.setGravity(Gravity.CENTER);
        if (isSite || kind == KIND_INAPP || kind == KIND_ADDRESS) {
            TextView back = overlayButton(c, "Nazad");
            back.setOnClickListener(v -> {
                performGlobalAction(GLOBAL_ACTION_BACK);
                h.removeCallbacks(recheckSoon);
                h.postDelayed(recheckSoon, 600);
            });
            actions.addView(back, actionParams(c));
        }
        if (actions.getChildCount() > 0) box.addView(actions, Ui.fill(c, 12));

        scroll.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** Šaljivo „Jesi li siguran?“ pre unosa PIN-a. */
    private void showAreYouSure(final LinearLayout area, final String key, final boolean code) {
        final Context c = this;
        area.removeAllViews();
        TextView q = Ui.text(c, "Jesi li siguran?", 22, 0xFFFFFFFF, true);
        q.setGravity(Gravity.CENTER);
        area.addView(q, Ui.fill(c, 0));
        TextView why = Ui.text(c, Jokes.pick(Jokes.ARE_YOU_SURE), 15, Ui.NIGHT_MUTED, false);
        why.setGravity(Gravity.CENTER);
        area.addView(why, Ui.fill(c, 6));
        area.addView(rulesNote(c), Ui.fill(c, 8));

        TextView no = overlayButton(c, Jokes.pick(Jokes.NO));
        no.setBackground(Ui.pressable(Ui.ACCENT, Ui.ACCENT_DOWN, Ui.dp(c, 14)));
        no.setOnClickListener(v -> {
            performGlobalAction(key.startsWith("site:") ? GLOBAL_ACTION_BACK : GLOBAL_ACTION_HOME);
            h.removeCallbacks(recheckSoon);
            h.postDelayed(recheckSoon, 600);
        });
        area.addView(no, Ui.fill(c, 16));

        TextView yes = overlayButton(c, Jokes.pick(Jokes.YES));
        yes.setOnClickListener(v -> showQuiz(area, () -> showPinPad(area, key, code), null));
        area.addView(yes, Ui.fill(c, 10));
    }

    /** Pitanje pre PIN-a. Tačan odgovor vodi dalje, a pogrešan donosi novo pitanje. */
    private void showQuiz(final LinearLayout area, final Runnable onPass, String notice) {
        final Context c = this;
        area.removeAllViews();
        final Quiz.Question q = Quiz.next();

        TextView head = Ui.text(c, "Prvo odgovori na pitanje", 20, 0xFFFFFFFF, true);
        head.setGravity(Gravity.CENTER);
        area.addView(head, Ui.fill(c, 0));
        if (notice != null) {
            TextView n = Ui.text(c, notice, 14, Ui.NIGHT_ACCENT, false);
            n.setGravity(Gravity.CENTER);
            area.addView(n, Ui.fill(c, 8));
        }
        TextView cat = Ui.text(c, q.category.toUpperCase(Locale.ROOT), 11, Ui.NIGHT_MUTED, true);
        cat.setLetterSpacing(0.15f);
        cat.setGravity(Gravity.CENTER);
        area.addView(cat, Ui.fill(c, 16));
        TextView text = Ui.text(c, q.text, 18, 0xFFFFFFFF, false);
        text.setGravity(Gravity.CENTER);
        area.addView(text, Ui.fill(c, 6));

        if (q.typed()) {
            final PinPad pad = new PinPad(c, true);
            pad.showDigits();
            pad.setListener(given -> {
                if (q.isCorrect(given)) {
                    onPass.run();
                } else {
                    showQuiz(area, onPass, "Netačno. Evo novog pitanja.");
                }
            });
            area.addView(pad, Ui.fill(c, 12));
        } else {
            for (final String choice : q.choices) {
                TextView b = overlayButton(c, choice);
                b.setOnClickListener(v -> {
                    if (q.isCorrect(choice)) {
                        onPass.run();
                    } else {
                        showQuiz(area, onPass, "Netačno. Evo novog pitanja.");
                    }
                });
                area.addView(b, Ui.fill(c, 10));
            }
        }
    }

    /** Tastatura za PIN, ili za dnevnu šifru kod režima sa šifrom. */
    private void showPinPad(final LinearLayout area, final String key, final boolean code) {
        area.removeAllViews();
        if (code) area.addView(codeNote(this), Ui.fill(this, 0));
        area.addView(rulesNote(this), Ui.fill(this, code ? 6 : 0));
        final PinPad pad = new PinPad(this, true);
        pad.setListener(pin -> {
            if (store.cooldownLeft(key) > 0) {
                safeCheck(); // pauza je počela dok je tastatura bila otvorena
                return;
            }
            String err = store.tryCode(pin);
            if (err == null) {
                store.startUnlock(key);
                safeCheck(); // another matching domain or rule may still require a block
            } else {
                pad.clear();
                pad.setMessage(err);
            }
        });
        area.addView(pad, Ui.fill(this, 12));
    }

    /** Hitno otključavanje mimo pauze: posle pitanja upozorenje pa PIN. */
    private void showEmergencyPad(final LinearLayout area, final String key, final boolean code) {
        final Context c = this;
        area.removeAllViews();
        TextView t = Ui.text(c, "Hitno otključavanje", 20, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        area.addView(t, Ui.fill(c, 0));
        String times = Store.EMERGENCY_PER_DAY == 1 ? "samo jednom dnevno"
                : Store.EMERGENCY_PER_DAY + " puta dnevno";
        TextView note = Ui.text(c, "Otključava na " + Store.EMERGENCY_MS / 60000L
                + " min i može se iskoristiti " + times + ". Ako ga sada potrošiš, do ponoći ga više nemaš.",
                13, Ui.NIGHT_MUTED, false);
        note.setGravity(Gravity.CENTER);
        area.addView(note, Ui.fill(c, 6));
        if (code) area.addView(codeNote(c), Ui.fill(c, 8));
        final PinPad pad = new PinPad(c, true);
        pad.setListener(pin -> {
            if (!store.emergencyAvailable()) {
                pad.clear();
                pad.setMessage("Hitno otključavanje je danas već iskorišćeno");
                return;
            }
            String err = store.tryCode(pin);
            if (err == null) {
                store.startEmergency(key);
                safeCheck();
            } else {
                pad.clear();
                pad.setMessage(err);
            }
        });
        area.addView(pad, Ui.fill(c, 12));
    }

    private static TextView codeNote(Context c) {
        TextView t = Ui.text(c, "Unesi dnevnu šifru (6 cifara) iz Čuvara.", 15, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** Upozorenje pre otključavanja: koliko traje i šta sledi posle. */
    private static TextView rulesNote(Context c) {
        TextView t = Ui.text(c, "Otključano je " + Store.UNLOCK_USE_MS / 60000L
                + " min, a posle toga " + Store.UNLOCK_COOLDOWN_MS / 60000L
                + " min nema otključavanja ničega, ni šifrom.", 13, Ui.NIGHT_MUTED, false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static TextView overlayButton(Context c, String label) {
        TextView b = Ui.text(c, label, 15, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.pressable(Ui.NIGHT_KEY, Ui.NIGHT_KEY_DOWN, Ui.dp(c, 14)));
        int ph = Ui.dp(c, 22);
        int pv = Ui.dp(c, 13);
        b.setPadding(ph, pv, ph, pv);
        return b;
    }

    private static LinearLayout.LayoutParams actionParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = Ui.dp(c, 6);
        lp.setMargins(m, 0, m, 0);
        return lp;
    }
}
