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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
    private static final long GRACE_MS = 15000L;

    private static final int KIND_LOCK = 1;       // aplikacija zaključana PIN-om
    private static final int KIND_TIME = 2;       // istekao dnevni limit aplikacije
    private static final int KIND_SITE = 3;       // sajt uvek blokiran
    private static final int KIND_SITE_TIME = 4;  // istekao dnevni limit sajta
    private static final int KIND_SCHEDULE = 5;

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
    private boolean receiverOn;

    private String currentPkg;    // aplikacija koja je trenutno na ekranu
    private String currentSite;   // domen sa liste koji je trenutno otvoren u pregledaču
    private String currentHost;   // host trenutno otvoren u pregledaču (za vremenske režime)
    private DailySchedule.Rule overlayRule; // režim prikazan na ekranu za blokadu
    private String unlockedKey;   // šta je trenutno otključano PIN-om ("app:paket" ili "site:domen")
    private long unlockedLeftAt;
    private long lastTick;
    private boolean checkPending;

    private View overlay;
    private String overlayKey;
    private int overlayKind;

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
                    if (currentSite != null && BROWSERS.containsKey(currentPkg)) {
                        store.addUsage("site:" + currentSite, dt);
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
                unlockedKey = null;
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
            unlockedKey = null;
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
            currentPkg = pkg;
            currentSite = null;
            currentHost = null;
        }

        String urlBarId = BROWSERS.get(pkg);
        if (urlBarId != null) {
            String url = readUrl(root, urlBarId);
            if (url != null) {
                String host = Store.hostOf(url);
                currentSite = host == null ? null : store.matchSite(host);
                currentHost = host;
            }
        }

        String blockKey = null;
        int kind = 0;

        boolean lock = store.appLock(pkg);
        int limit = store.appLimit(pkg);
        boolean timeUp = limit > 0 && store.usedToday(pkg) >= limit * 60000L;
        DailySchedule.Rule rule = store.scheduleBlockingApp(pkg);
        if (rule != null || lock || timeUp) {
            blockKey = "app:" + pkg;
            kind = rule != null ? KIND_SCHEDULE : timeUp ? KIND_TIME : KIND_LOCK;
        }

        if (rule == null && urlBarId != null && currentHost != null) {
            rule = store.scheduleBlockingSite(currentHost);
            if (rule != null) {
                blockKey = "site:" + DailySchedule.matchDomain(currentHost, rule.sites);
                kind = KIND_SCHEDULE;
            }
        }

        if (blockKey == null && urlBarId != null && currentSite != null) {
            int siteLimit = store.siteLimit(currentSite);
            if (siteLimit == 0) {
                blockKey = "site:" + currentSite;
                kind = KIND_SITE;
            } else if (siteLimit > 0 && store.usedToday("site:" + currentSite) >= siteLimit * 60000L) {
                blockKey = "site:" + currentSite;
                kind = KIND_SITE_TIME;
            }
        }

        // Otključavanje PIN-om važi dok si u toj aplikaciji / na tom sajtu, i još kratko posle izlaska.
        long now = SystemClock.elapsedRealtime();
        if (unlockedKey != null) {
            boolean inside = unlockedKey.equals("app:" + pkg)
                    || (currentSite != null && unlockedKey.equals("site:" + currentSite));
            if (unlockedLeftAt != 0 && now - unlockedLeftAt > GRACE_MS) {
                unlockedKey = null;
                unlockedLeftAt = 0;
            } else if (inside) {
                unlockedLeftAt = 0;
            } else if (unlockedLeftAt == 0) {
                unlockedLeftAt = now;
            }
        }

        if (blockKey != null && (kind == KIND_SCHEDULE || !blockKey.equals(unlockedKey))) {
            if (kind == KIND_SCHEDULE) {
                unlockedKey = null;
                unlockedLeftAt = 0;
            }
            showOverlay(blockKey, kind, rule);
        } else {
            hideOverlay();
        }
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

    private void showOverlay(String key, int kind, DailySchedule.Rule rule) {
        if (overlay != null && key.equals(overlayKey) && kind == overlayKind && sameRule(rule, overlayRule)) {
            return;
        }
        hideOverlay();
        try {
            View v = buildOverlay(key, kind, rule);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(v, lp);
            overlay = v;
            overlayKey = key;
            overlayKind = kind;
            overlayRule = rule;
        } catch (Throwable t) {
            overlay = null;
            overlayKey = null;
        }
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
            overlayRule = null;
        }
    }

    /** Isti režim sa istim nazivom i periodom; inače ekran za blokadu treba osvežiti. */
    private static boolean sameRule(DailySchedule.Rule a, DailySchedule.Rule b) {
        if (a == null || b == null) return a == b;
        return a.id.equals(b.id) && a.name.equals(b.name) && a.start == b.start && a.end == b.end;
    }

    private View buildOverlay(final String key, int kind, DailySchedule.Rule rule) {
        final Context c = this;
        final boolean isSite = key.startsWith("site:");
        String name = isSite ? key.substring(5) : appLabel(key.substring(4));

        String title;
        String sub;
        if (kind == KIND_SCHEDULE && rule != null) {
            title = "Režim „" + rule.name + "“ je aktivan";
            sub = name + " je blokiran svakog dana od " + DailySchedule.label(rule.start)
                    + " do " + DailySchedule.label(rule.end) + ".";
        } else if (kind == KIND_LOCK) {
            title = name + " je zaključan";
            sub = "Unesi PIN da otvoriš aplikaciju.";
        } else if (kind == KIND_TIME) {
            title = "Vreme je isteklo";
            sub = "Dnevni limit za " + name + " je potrošen. Sutra kreće ispočetka.";
        } else if (kind == KIND_SITE) {
            title = "Sajt je blokiran";
            sub = name + " je na tvojoj listi blokiranih sajtova.";
        } else {
            title = "Vreme je isteklo";
            sub = "Dnevni limit za " + name + " je potrošen. Sutra kreće ispočetka.";
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

        if (store.hasPin() && kind != KIND_SCHEDULE) {
            final PinPad pad = new PinPad(c, true);
            pad.setListener(pin -> {
                String err = store.tryPin(pin);
                if (err == null) {
                    unlockedKey = key;
                    unlockedLeftAt = 0;
                    hideOverlay();
                } else {
                    pad.clear();
                    pad.setMessage(err);
                }
            });
            box.addView(pad, Ui.fill(c, 22));
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
        TextView home = overlayButton(c, isSite ? "Početni ekran" : "Izađi");
        home.setOnClickListener(v -> {
            performGlobalAction(GLOBAL_ACTION_HOME);
            h.removeCallbacks(recheckSoon);
            h.postDelayed(recheckSoon, 600);
        });
        actions.addView(home, actionParams(c));
        box.addView(actions, Ui.fill(c, 18));

        scroll.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return scroll;
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
