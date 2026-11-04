package com.cuvar.app.guard;

import com.cuvar.app.rules.ItemKey;
import com.cuvar.app.rules.UnlockCost;
import com.cuvar.app.GuardService;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.web.BrowserMonitor;

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
    // Blokiran sajt: pregledač je odmah vraćen nazad, a ekran blokade samo javlja šta se desilo (samo „Zatvori“).
    private String noticeKey;       // sajt za koji je prikazana takva poruka
    private String noticeBrowser;   // pregledač u kome je vraćeno nazad
    private String backedKey;       // sajt za koji je poslednji put pritisnuto nazad
    private long backedAt;
    // Izlaz posle „Zatvori“: nazad u pregledaču, ponovo ako se isti sajt i dalje vidi (vidi exitBlocked).
    private String exitKey;
    private String exitBrowser;
    private int exitBacks;
    private long exitAt;
    private static final long EXIT_MS = 3000L;
    private static final int EXIT_MAX_BACKS = 3;
    private static final long BACK_RETRY_MS = 10000L;
    // Kad nazad izađe iz pregledača (sajt otvoren iz druge aplikacije), poruka ipak ostaje bar ovoliko.
    private static final long NOTICE_MIN_MS = 3000L;
    private android.graphics.Rect overlayBounds; // prozor koji blokada pokriva u podeljenom ekranu, ili null
    /** Blokada pokriva jednu polovinu podeljenog ekrana: dugme vodi na početni ekran (izlaz iz podeljenog ekrana). */
    public boolean splitExit;
    // Dok blokada pokriva jedan prozor, prati njegove granice (pomerena granica ekrana, tastatura gura prozor gore).
    private static final long FOLLOW_MS = 400L;
    private final Runnable followBounds = new Runnable() {
        @Override
        public void run() {
            if (overlay == null || overlayBounds == null || overlayKey == null) return;
            com.cuvar.app.ui.Safe.run("followBounds", () -> {
                // Posle vraćanja nazad pregledač više ne prikazuje blokiran sajt, pa se prozor traži po pregledaču.
                String browser = noticeKey != null && noticeKey.equals(overlayKey) ? noticeBrowser : null;
                android.graphics.Rect b = browser != null ? svc.windows.boundsOf(browser::equals) : svc.blockBounds(overlayKey);
                if (b != null && !b.equals(overlayBounds)) {
                    svc.overlayWindow.move(overlay, b);
                    overlayBounds = b;
                }
            });
            svc.h.postDelayed(this, FOLLOW_MS);
        }
    };

    public static String cooldownText(long left) {
        long min = (left + 59999L) / 60000L;
        return "Otključavanje je iskorišćeno. Sledeće je moguće za " + min + " min.";
    }

    /** Blokada se odnosi na stranicu u pregledaču koji je na ekranu (sajt ili nepotvrđena adresa). */
    private boolean inBrowserPage(String key, int kind) {
        return (ItemKey.isSite(key) || kind == GuardService.KIND_ADDRESS || kind == GuardService.KIND_UNSEEN_SITE) && browserForCurrentBlock() != null;
    }

    /** Blokiran sajt u pravom pregledaču (ne nepotvrđena adresa ni pregledač u aplikaciji): vraća se nazad. */
    public boolean sitePage(String key, int kind) {
        return ItemKey.isSite(key) && inBrowserPage(key, kind);
    }

    /**
     * Da li ekran sa porukom o vraćenom sajtu treba da ostane: dok se ne pritisne „Zatvori“ ili ne izađe iz
     * pregledača, a prvih NOTICE_MIN_MS i van njega (nazad je možda zatvorilo karticu i vratilo u drugu aplikaciju).
     */
    public boolean holdsNotice(String front) {
        if (overlay == null || noticeKey == null || !noticeKey.equals(overlayKey)) return false;
        return (front != null && front.equals(noticeBrowser)) || SystemClock.elapsedRealtime() - backedAt < NOTICE_MIN_MS;
    }

    public String browserForCurrentBlock() {
        if (svc.currentPkg != null && BrowserMonitor.isBrowser(svc.currentPkg)) return svc.currentPkg;
        return svc.rawPkg != null && BrowserMonitor.isBrowser(svc.rawPkg) ? svc.rawPkg : null;
    }

    /**
     * Izlaz sa ekrana za blokadu. Blokiran sajt u pregledaču: ostani u pregledaču, samo se vrati nazad
     * u istoj kartici. Sve ostalo: početni ekran telefona.
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
        if (key.equals(noticeKey)) {
            // Pregledač je već vraćen na prethodnu stranicu: samo se skloni ekran. Ako se sajt ipak i dalje vidi
            // (nazad nije imalo kuda), sledeća provera ponovo vraća nazad (exitBlocked).
            String browser = noticeBrowser;
            hideOverlay();
            startExit(key, browser, 1);
            svc.h.removeCallbacks(svc.recheckSoon);
            svc.h.postDelayed(svc.recheckSoon, 300);
            return;
        }
        if (inBrowserPage(key, kind)) {
            // Stranica u pregledaču: nazad u istoj kartici, bez nove kartice.
            String browser = browserForCurrentBlock();
            hideOverlay();
            startExit(key, browser, 1);
            // Kratko čekanje da pregledač ponovo dobije fokus kad naš prozor nestane.
            svc.h.postDelayed(this::pressBack, 150L);
            return;
        }
        hideOverlay();
        svc.performGlobalAction(GuardService.GLOBAL_ACTION_HOME);
        svc.h.removeCallbacks(svc.recheckSoon);
        svc.h.postDelayed(svc.recheckSoon, 600);
    }

    private void startExit(String key, String browser, int backs) {
        exitKey = key;
        exitBrowser = browser;
        exitBacks = backs;
        exitAt = SystemClock.elapsedRealtime();
    }

    private void pressBack() {
        com.cuvar.app.ui.Safe.run("exitBack", () -> {
            try {
                svc.performGlobalAction(GuardService.GLOBAL_ACTION_BACK);
            } finally {
                svc.h.removeCallbacks(svc.recheckSoon);
                svc.h.postDelayed(svc.recheckSoon, 700);
            }
        });
    }

    /**
     * Posle „Zatvori“ na stranici u pregledaču: ako se isti sajt i dalje vidi (preusmeravanje, nazad nije imalo
     * kuda), vraća se nazad još jednom ili dva puta, pa na početni ekran. Dok pregledač menja stranicu, kratko
     * „nepoznata adresa“ ne pravi novi ekran. Vraća true kada je blokada tako obrađena (bez ekrana i pokušaja).
     */
    public boolean exitBlocked(String key, int kind) {
        if (exitKey == null) return false;
        if (SystemClock.elapsedRealtime() - exitAt >= EXIT_MS) {
            exitKey = null;
            return false;
        }
        String front = browserForCurrentBlock();
        if (front == null || !front.equals(exitBrowser)) return false;
        if (key.equals(exitKey) && inBrowserPage(key, kind)) {
            hideOverlay();
            if (exitBacks < EXIT_MAX_BACKS) {
                exitBacks++;
                exitAt = SystemClock.elapsedRealtime();
                pressBack();
            } else {
                exitKey = null;
                svc.performGlobalAction(GuardService.GLOBAL_ACTION_HOME);
            }
            return true;
        }
        return (kind == GuardService.KIND_ADDRESS || kind == GuardService.KIND_UNSEEN_SITE)
                && key.equals(ItemKey.app(exitBrowser));
    }

    /** Omotač koji sistemsko „nazad“ (taster ili pokret) pretvara u izlaz sa stranice (kao „Zatvori“). */
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

    /**
     * bounds: prozor blokirane aplikacije kad je ekran podeljen (blokada pokriva samo njega, druga polovina radi),
     * ili null za ceo ekran.
     */
    public void showOverlay(String key, int kind, DailySchedule.Rule rule, boolean cooling, boolean code,
                            android.graphics.Rect bounds) {
        if (overlay != null && key.equals(overlayKey) && kind == overlayKind && sameRule(rule, svc.overlayRule)
                && cooling == overlayCooling && code == overlayCode && (bounds == null) == (overlayBounds == null)) {
            if (bounds != null && !bounds.equals(overlayBounds)) {
                // Granica podeljenog ekrana je pomerena: blokada prati prozor.
                svc.overlayWindow.move(overlay, bounds);
                overlayBounds = bounds;
            }
            return;
        }
        if (overlay == null && overlayRetryAt > SystemClock.elapsedRealtime()) return;
        hideOverlay();
        try {
            boolean notice = false;
            String browser = browserForCurrentBlock();
            long now = SystemClock.elapsedRealtime();
            // Kad sajt sada može da se otključa (igrica/pitanje pa šifra), ostaje ispod ekrana; „Zatvori“ ga sklanja.
            // Režim, potrošen limit i noć: pregledač se odmah vraća nazad.
            if (sitePage(key, kind) && !canUnlock(kind, cooling, code)
                    && !(key.equals(backedKey) && now - backedAt < BACK_RETRY_MS)) {
                // Pregledač se odmah vraća na prethodnu stranicu u istoj kartici, bez nove kartice.
                svc.performGlobalAction(GuardService.GLOBAL_ACTION_BACK);
                backedKey = key;
                backedAt = now;
                notice = true;
                // Posle NOTICE_MIN_MS nova provera skida poruku ako pregledač više nije napred.
                svc.h.postDelayed(svc::safeCheck, NOTICE_MIN_MS + 100L);
            }
            // U podeljenom ekranu „Zatvori“ vodi na početni ekran, pa se tako i zove.
            splitExit = bounds != null && !notice;
            View v = svc.blockOverlayContent.build(key, kind, rule, cooling, code);
            // Kod sajta u pregledaču naš ekran hvata i sistemsko „nazad“: inače bi ga dobio pregledač,
            // zatvorio karticu bez istorije i izašao iz aplikacije umesto da te vrati na početnu stranu.
            // Poruka posle vraćanja nazad i blokada jedne polovine ekrana ne uzimaju fokus.
            boolean catchBack = inBrowserPage(key, kind) && !notice && bounds == null;
            if (catchBack) v = backCatcher(v, key, kind);
            svc.overlayWindow.show(v, catchBack, svc.rawPkg, bounds);
            overlay = v;
            overlayBounds = bounds;
            svc.h.removeCallbacks(followBounds);
            if (bounds != null) svc.h.postDelayed(followBounds, FOLLOW_MS);
            overlayKey = key;
            overlayKind = kind;
            svc.overlayRule = rule;
            overlayCooling = cooling;
            overlayCode = code;
            noticeKey = notice ? key : null;
            noticeBrowser = notice ? browser : null;
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

    /** Ekran za blokadu nudi otključavanje (isti uslovi kao u BlockOverlayContent). */
    private boolean canUnlock(int kind, boolean cooling, boolean code) {
        return code && !cooling && !GuardService.hard(kind) && svc.store.unlock.dailyCodeVisible()
                && UnlockCost.next(svc.store.unlock.unlocksToday()) != null;
    }

    /** Skida ekran za blokadu ako je prikazan baš za ovu stavku (npr. upravo otključanu). */
    public void hideIfShowing(String key) {
        if (overlay != null && key.equals(overlayKey)) hideOverlay();
    }

    public void hideOverlay() {
        if (overlay != null) {
            svc.overlayWindow.hide(overlay);
            overlay = null;
            noticeKey = null;
            overlayBounds = null;
            noticeBrowser = null;
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
