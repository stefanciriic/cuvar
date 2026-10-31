package com.cuvar.app.guard;

import com.cuvar.app.rules.ItemKey;
import com.cuvar.app.GuardService;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.web.BrowserMonitor;

import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Ekran blokade preko svega: prikaz, sakrivanje, „nazad“ i izlaz na početnu stranu pregledača. */
public final class BlockScreen {
    private final GuardService svc;

    public BlockScreen(GuardService svc) {
        this.svc = svc;
    }

    public View overlay;
    public String overlayKey;
    public int overlayKind;
    private boolean overlayCooling;   // prikazana je pauza posle otključavanja
    private boolean overlayCode;      // otključava se dnevnom šifrom umesto PIN-om
    public TextView cooldownLabel;  // odbrojavanje do sledećeg mogućeg otključavanja
    public int overlayFailures;
    public long overlayRetryAt;

    public static String cooldownText(long left) {
        long min = (left + 59999L) / 60000L;
        return "Otključavanje je iskorišćeno. Sledeće je moguće za " + min + " min.";
    }

    /** Blokada se odnosi na stranicu u pregledaču koji je na ekranu (sajt ili nepotvrđena adresa). */
    private boolean inBrowserPage(String key, int kind) {
        return (ItemKey.isSite(key) || kind == GuardService.KIND_ADDRESS || kind == GuardService.KIND_UNSEEN_SITE) && browserForCurrentBlock() != null;
    }

    public String browserForCurrentBlock() {
        if (svc.currentPkg != null && BrowserMonitor.isBrowser(svc.currentPkg)) return svc.currentPkg;
        return svc.rawPkg != null && BrowserMonitor.isBrowser(svc.rawPkg) ? svc.rawPkg : null;
    }

    /**
     * Izlaz sa ekrana za blokadu. Blokiran sajt u pregledaču: ostani u pregledaču, samo skloni karticu
     * sa te stranice (otvori početnu Google stranu). Sve ostalo: početni ekran telefona.
     */
    public void leaveBlock(String key, int kind) {
        try {
            leave(key, kind);
        } catch (Throwable error) {
            GuardDiagnostics.report("leaveBlock", error);
            // Izlaz mora uvek da radi: početni ekran, pa nova provera (blokada se vraća ako i dalje važi).
            try {
                svc.performGlobalAction(GuardService.GLOBAL_ACTION_HOME);
            } catch (Throwable ignored) {
                // nema drugog izlaza; prozor se ipak skida ispod
            }
            try {
                hideOverlay();
            } catch (Throwable e2) {
                GuardDiagnostics.report("leaveBlockHide", e2);
            }
            svc.h.removeCallbacks(svc.recheckSoon);
            svc.h.postDelayed(svc.recheckSoon, 600);
        }
    }

    private void leave(String key, int kind) {
        boolean sitePage = inBrowserPage(key, kind);
        String browser = sitePage ? browserForCurrentBlock() : null;
        if (sitePage) {
            svc.leavingSiteKey = key;
            svc.leavingSiteBrowser = browser;
            svc.leavingSiteUntil = SystemClock.elapsedRealtime() + 2500L;
            svc.h.removeCallbacks(svc.finishSiteExit);
            svc.h.postDelayed(svc.finishSiteExit, 2500L);
        }
        // Remove the accessibility window before starting browser navigation so Back feels immediate and
        // the browser can receive focus while we set its neutral URL.
        hideOverlay();
        if (sitePage) {
            openStartPage(browser);
        } else {
            svc.performGlobalAction(GuardService.GLOBAL_ACTION_HOME);
        }
        svc.h.removeCallbacks(svc.recheckSoon);
        svc.h.postDelayed(svc.recheckSoon, 600);
    }

    private static final String START_PAGE = "https://www.google.com";

    /** Otvara neutralnu stranicu direktnim browser intent-om; ne menja fokus dok je accessibility overlay aktivan. */
    private void openStartPage(String browser) {
        startPageIntent(browser);
        svc.h.removeCallbacks(svc.recheckSoon);
        svc.h.postDelayed(svc.recheckSoon, 600);
    }

    private void startPageIntent(String browser) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(START_PAGE))
                    .setPackage(browser)
                    .putExtra("com.android.browser.application_id", svc.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            svc.startActivity(i);
        } catch (Throwable t) {
            svc.performGlobalAction(GuardService.GLOBAL_ACTION_HOME);
        }
    }

    /** Omotač koji sistemsko „nazad“ (taster ili pokret) pretvara u izlaz na početnu stranu pregledača. */
    private View backCatcher(View inner, final String key, final int kind) {
        FrameLayout wrap = new FrameLayout(svc) {
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

    public void showOverlay(String key, int kind, DailySchedule.Rule rule, boolean cooling, boolean code) {
        if (overlay != null && key.equals(overlayKey) && kind == overlayKind && sameRule(rule, svc.overlayRule)
                && cooling == overlayCooling && code == overlayCode) {
            return;
        }
        if (overlay == null && overlayRetryAt > SystemClock.elapsedRealtime()) return;
        hideOverlay();
        try {
            View v = svc.blockOverlayContent.build(key, kind, rule, cooling, code);
            // Kod sajta u pregledaču naš ekran hvata i sistemsko „nazad“: inače bi ga dobio pregledač,
            // zatvorio karticu bez istorije i izašao iz aplikacije umesto da te vrati na početnu stranu.
            boolean catchBack = inBrowserPage(key, kind);
            if (catchBack) v = backCatcher(v, key, kind);
            svc.overlayWindow.show(v, catchBack, svc.rawPkg);
            overlay = v;
            overlayKey = key;
            overlayKind = kind;
            svc.overlayRule = rule;
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
            svc.h.removeCallbacks(svc.recheckSoon);
            svc.h.postDelayed(svc.recheckSoon, delay);
        }
    }

    public void hideOverlay() {
        if (overlay != null) {
            svc.overlayWindow.hide(overlay);
            overlay = null;
            overlayKey = null;
            overlayKind = 0;
            svc.overlayRule = null;
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

    public void overlayGlobalBack() {
        svc.performGlobalAction(GuardService.GLOBAL_ACTION_BACK);
    }
}
