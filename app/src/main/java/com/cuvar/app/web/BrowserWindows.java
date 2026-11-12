package com.cuvar.app.web;

import com.cuvar.app.GuardService;
import com.cuvar.app.guard.GuardDiagnostics;

import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/** Prozori na ekranu: glavni prozor pregledača sa adresom i drugi prozori aplikacija (podeljen ekran). */
public final class BrowserWindows {
    private final GuardService svc;

    public BrowserWindows(GuardService svc) {
        this.svc = svc;
    }

    public static final class AppWindow {
        public final String pkg;
        public final String rawPkg;
        public final AccessibilityNodeInfo root;

        public AppWindow(String pkg, String rawPkg, AccessibilityNodeInfo root) {
            this.pkg = pkg;
            this.rawPkg = rawPkg;
            this.root = root;
        }
    }

    /**
     * Prozor pregledača u kome je adresna traka. Kad je napred oblačić, meni ili savet pregledača (poseban mali
     * prozor bez trake), traži se glavni prozor istog pregledača na ekranu. Ako ga nema, ostaje prednji prozor.
     */
    public AccessibilityNodeInfo browserAddressRoot(AccessibilityNodeInfo root, String raw, String urlBarId) {
        if (hasNode(root, urlBarId)) return root;
        try {
            for (AccessibilityWindowInfo w : svc.getWindows()) {
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
    public static boolean isSameBrowserWindow(AppWindow other, String raw, String urlBarId,
                                               AccessibilityNodeInfo addressRoot) {
        if (urlBarId == null || !other.rawPkg.equals(raw)) return false;
        if (addressRoot != null && other.root.getWindowId() == addressRoot.getWindowId()) return true;
        return !hasNode(other.root, urlBarId);
    }

    /** Kad je napred naš ekran blokade: preskače se aktivan ili fokusiran prozor (aplikacija ispod blokade). */
    public static final int FOCUSED = -2;

    /**
     * Ostali vidljivi prozori aplikacija; root se zadržava da bi se proverila i pravila sajtova.
     * Preskače se samo prozor koji je check() već pročitao (frontWindowId); -1: nijedan.
     * Na MIUI aktivan može biti početni ekran dok je fokusirana aplikacija sa pravilom, pa se fokus ne gleda.
     */
    public List<AppWindow> otherAppWindows(int frontWindowId) {
        List<AppWindow> out = new ArrayList<>();
        try {
            List<AccessibilityWindowInfo> apps = new ArrayList<>();
            for (AccessibilityWindowInfo w : svc.getWindows()) {
                if (w != null && w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) apps.add(w);
            }
            // Samo jedna aplikacija na ekranu (uobičajeno): ne pita se aplikacija za sadržaj bez potrebe.
            if (apps.size() < 2) return out;
            for (AccessibilityWindowInfo w : apps) {
                if (frontWindowId == FOCUSED ? (w.isActive() || w.isFocused()) : w.getId() == frontWindowId) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null) continue;
                String p = r.getPackageName().toString();
                if (p.equals(svc.getPackageName()) || GuardService.TRANSPARENT.contains(p)) continue;
                out.add(new AppWindow(svc.kinds.cloneOf(p), p, r));
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("otherWindows", error);
        }
        return out;
    }

    /**
     * Granice prozora aplikacije čiji paket odgovara, kad je na ekranu više aplikacija (podeljen ekran,
     * plutajući prozor); inače null, pa blokada pokriva ceo ekran. Kad se prozor ne nađe, takođe null.
     */
    public android.graphics.Rect boundsOf(java.util.function.Predicate<String> matches) {
        try {
            List<AccessibilityWindowInfo> apps = new ArrayList<>();
            for (AccessibilityWindowInfo w : svc.getWindows()) {
                if (w != null && w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) apps.add(w);
            }
            if (apps.size() < 2) return null;
            for (AccessibilityWindowInfo w : apps) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null) continue;
                if (!matches.test(r.getPackageName().toString())) continue;
                android.graphics.Rect b = new android.graphics.Rect();
                w.getBoundsInScreen(b);
                return b.isEmpty() ? null : b;
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("windowBounds", error);
        }
        return null;
    }

    /** Broj prozora aplikacija na ekranu (podeljen ekran, plutajući prozor); ne čita sadržaj aplikacija. */
    public int appWindowCount() {
        int n = 0;
        try {
            for (AccessibilityWindowInfo w : svc.getWindows()) {
                if (w != null && w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) n++;
            }
        } catch (Throwable ignored) {
            // bez spiska prozora vraća 0, isto kao jedna aplikacija na ekranu (uobičajen slučaj)
        }
        return n;
    }
}
