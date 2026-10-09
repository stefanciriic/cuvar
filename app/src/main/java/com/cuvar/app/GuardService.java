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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private String currentSite;   // domen sa liste koji je trenutno otvoren u pregledaču
    private String currentHost;   // host trenutno otvoren u pregledaču (za vremenske režime)
    private DailySchedule.Rule overlayRule; // režim prikazan na ekranu za blokadu
    private long lastTick;
    private boolean checkPending;

    private View overlay;
    private String overlayKey;
    private int overlayKind;
    private boolean overlayCooling;   // prikazana je pauza posle otključavanja
    private boolean overlayCode;      // otključava se dnevnom šifrom umesto PIN-om
    private TextView cooldownLabel;  // odbrojavanje do sledećeg mogućeg otključavanja
    private Set<String> exempt;      // aplikacije koje se ne računaju u ukupni limit i ne blokiraju se zbog njega
    private String pendingOpen;      // aplikacija upravo otvorena; broji se kad se zaista prikaže, bez blokade
    private String opensBlocked;     // aplikacija otvorena posle potrošenih otvaranja; blokirana dok se ne izađe
    private String lastLeftPkg;      // aplikacija iz koje se upravo izašlo
    private long lastLeftAt;
    private long exemptAt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            long dt = now - lastTick;
            lastTick = now;
            try {
                boolean active = power.isInteractive() && !keyguard.isKeyguardLocked();
                if (active && overlay == null && currentPkg != null && dt > 0 && dt <= 3 * TICK_MS) {
                    store.addUsage(currentPkg, dt);
                    if (BROWSERS.containsKey(currentPkg)) {
                        if (currentSite != null) {
                            store.addUsage("site:" + currentSite, dt); // za limite sa liste sajtova
                        }
                        String web = Store.mainDomain(currentHost);
                        if (web != null) {
                            store.addUsage("web:" + web, dt); // za statistiku svih posećenih sajtova
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            safeCheck();
            h.postDelayed(this, TICK_MS);
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
                hideOverlay();
                if (store != null) {
                    store.flush();
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
                        | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
                info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                info.notificationTimeout = 100;
                setServiceInfo(info);
            }
        } catch (Throwable ignored) {
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
        } catch (Throwable ignored) {
        }

        running = true;
        lastTick = SystemClock.elapsedRealtime();
        h.removeCallbacks(tick);
        h.postDelayed(tick, TICK_MS);
        safeCheck();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Otvoren je novi prozor: proveri odmah, pa još dva puta jer sistem ponekad kasni.
            safeCheck();
            h.removeCallbacks(recheckSoon);
            h.postDelayed(recheckSoon, 350);
            h.removeCallbacks(recheckLater);
            h.postDelayed(recheckLater, 1200);
        } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Sadržaj se menja vrlo često; zanima nas samo u pregledaču (promena adrese).
            CharSequence p = event.getPackageName();
            if (p != null && BROWSERS.containsKey(p.toString()) && !checkPending) {
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
        try {
            check();
        } catch (Throwable ignored) {
        }
    }

    /** Glavna odluka: šta je na ekranu i da li to treba blokirati. */
    private void check() {
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

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) {
            return;
        }
        String pkg = root.getPackageName().toString();
        if (TRANSPARENT.contains(pkg)) {
            return;
        }
        if (overlay != null && pkg.equals(getPackageName())) {
            if (currentPkg == null) return;
            pkg = currentPkg; // ponovo proveri pravila i dok je naš ekran preko aplikacije
        }

        if (!pkg.equals(currentPkg)) {
            long nowEl = SystemClock.elapsedRealtime();
            // Kratak izlazak (deljenje, izbor fajla, dozvola) i povratak nije novo otvaranje.
            boolean back = pkg.equals(lastLeftPkg) && nowEl - lastLeftAt < OPEN_GRACE_MS;
            if (currentPkg != null) {
                lastLeftPkg = currentPkg;
                lastLeftAt = nowEl;
            }
            currentPkg = pkg;
            currentSite = null;
            currentHost = null;
            if (!back) {
                int max = store.appOpensNow(pkg);
                opensBlocked = max > 0 && store.opensToday("app:" + pkg) >= max ? pkg : null;
                pendingOpen = opensBlocked == null && max > 0 ? pkg : null;
            }
        }

        String urlBarId = BROWSERS.get(pkg);
        if (urlBarId != null) {
            String url = readUrl(root, urlBarId);
            if (url != null) {
                String host = Store.hostOf(url);
                currentSite = host == null ? null : store.matchSiteNow(host);
                currentHost = host;
            }
        }

        // Sva pravila koja sada važe, od najstrožeg: ukupni dnevni limit, vremenski režim, pa aplikacija, pa sajt.
        // Otključavanje jedne stavke ne otvara ostale (otključan pregledač ne otvara blokiran sajt).
        List<Block> blocks = new ArrayList<>();
        String appKey = "app:" + pkg;
        boolean web = urlBarId != null && currentHost != null;
        int dayLimit = store.dayLimit();
        if (dayLimit > 0) {
            Set<String> skip = exempt();
            long phone = store.phoneToday(skip);
            if (DayLimit.warn(dayLimit, phone) && store.firstDayLimitWarning()) {
                long left = dayLimit * 60000L - phone;
                Toast.makeText(this, "Čuvar: do dnevnog limita ostalo je " + Ui.fmt(left) + ".",
                        Toast.LENGTH_LONG).show();
            }
            if (DayLimit.reached(dayLimit, phone) && !skip.contains(pkg)) {
                String site = web ? store.siteGuarded(currentHost) : null;
                if (store.appGuarded(pkg)) {
                    blocks.add(new Block(appKey, KIND_DAY, null, false));
                } else if (site != null) {
                    blocks.add(new Block("site:" + site, KIND_DAY, null, false));
                }
            }
        }
        if (store.nightActive() && !exempt().contains(pkg)) {
            String site = web ? store.siteGuarded(currentHost) : null;
            if (store.appGuarded(pkg)) {
                blocks.add(new Block(appKey, KIND_NIGHT, null, false));
            } else if (site != null) {
                blocks.add(new Block("site:" + site, KIND_NIGHT, null, false));
            }
        }
        if (pkg.equals(opensBlocked)) {
            blocks.add(new Block(appKey, KIND_OPENS, null, false));
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
            boolean timeUp = limit > 0 && store.usedToday(pkg) >= limit * 60000L;
            // Van perioda režim sa dnevnom šifrom drži svoje aplikacije zaključane, a umesto PIN-a traži šifru.
            DailySchedule.Rule codeRule = store.codeRuleForApp(pkg);
            if (lock || timeUp || codeRule != null) {
                int k = timeUp ? KIND_TIME : codeRule != null ? KIND_CODE : KIND_LOCK;
                blocks.add(new Block(appKey, k, null, true));
            }
        }
        if (web && siteRule == null) {
            DailySchedule.Rule codeRule = store.codeRuleForSite(currentHost);
            if (codeRule != null) {
                blocks.add(new Block("site:" + DailySchedule.matchDomain(currentHost, codeRule.sites),
                        KIND_CODE, null, true));
            }
        }
        if (urlBarId != null && currentSite != null) {
            int siteLimit = store.siteLimitNow(currentSite);
            if (siteLimit == 0) {
                blocks.add(new Block("site:" + currentSite, KIND_SITE, null, true));
            } else if (siteLimit > 0 && store.usedToday("site:" + currentSite) >= siteLimit * 60000L) {
                blocks.add(new Block("site:" + currentSite, KIND_SITE_TIME, null, true));
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
        long coolLeft = show == null || hard(show.kind) ? 0 : store.cooldownLeft(show.key);

        if (show != null && (overlay == null || !show.key.equals(overlayKey))) {
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
        }
    }

    /** Blokade koje se ne otključavaju: vremenski režim, potrošen limit aplikacije, sajta ili ukupni. */
    private static boolean hard(int kind) {
        return kind == KIND_SCHEDULE || kind == KIND_DAY || kind == KIND_NIGHT || kind == KIND_TIME || kind == KIND_SITE_TIME
                || kind == KIND_OPENS;
    }

    /** Početni ekran, Čuvar, pozivi i poruke: ne računaju se u ukupni limit i nikad se zbog njega ne blokiraju. */
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

    /**
     * Čita adresu iz pregledača. Vraća null ako se ne može pročitati (tada zadržavamo staro stanje),
     * a prazan tekst ako je polje prazno.
     */
    private String readUrl(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        AccessibilityNodeInfo n = nodes.get(0);
        if (n == null || n.isFocused()) {
            return null; // korisnik upravo kuca adresu
        }
        CharSequence t = n.getText();
        return t == null ? "" : t.toString();
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
        } catch (Throwable t) {
            overlay = null;
            overlayKey = null;
            cooldownLabel = null;
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
        } catch (Throwable ignored) {
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
        } catch (Throwable ignored) {
            silence = null;
        }
    }

    private void releaseMedia() {
        if (audio == null || silence == null) return;
        try {
            audio.abandonAudioFocusRequest(silence);
        } catch (Throwable ignored) {
        }
        silence = null;
    }

    private void hideOverlay() {
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Throwable ignored) {
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

    private View buildOverlay(final String key, int kind, DailySchedule.Rule rule, boolean cooling, final boolean code) {
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
            sub = "Danas si na telefonu proveo " + Ui.fmt(store.phoneToday(exempt())) + ", a limit je "
                    + DayLimit.label(store.dayLimit()) + ". " + name
                    + " je zaključan do ponoći i ne može da se otključa, ni šifrom.";
            joke = Jokes.pick(Jokes.TIME_UP);
        } else if (kind == KIND_OPENS) {
            String pkg = key.substring(4);
            title = "Otvaranja za danas su potrošena";
            sub = name + " si danas otvorio " + Ui.count(store.opensToday(key), "put", "puta", "puta") + ", a dozvoljeno je "
                    + store.appOpensNow(pkg) + ". Do ponoći se ne otvara, ni šifrom.";
            joke = Jokes.pick(Jokes.TIME_UP);
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
            long used = store.usedToday(isSite ? key : key.substring(4));
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
            if (kind == KIND_LOCK || kind == KIND_CODE) {
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
        if (isSite) {
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
                    showQuiz(area, onPass, "Netačno, tačan odgovor je " + q.answer + ". Evo novog pitanja.");
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
                        showQuiz(area, onPass, "Netačno, tačan odgovor je „" + q.answer + "“. Evo novog pitanja.");
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
                hideOverlay();
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
                hideOverlay();
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
