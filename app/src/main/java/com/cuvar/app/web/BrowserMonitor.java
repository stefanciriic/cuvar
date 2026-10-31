package com.cuvar.app.web;

import com.cuvar.app.guard.GuardDiagnostics;
import com.cuvar.app.ui.Ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Browser package recognition and low-cost address observation for GuardService. */
public final class BrowserMonitor {
    // Spisak pregledača i prepoznavanje adrese su u Browsers (čisto, sa testom).
    public static final Map<String, String> BROWSERS = Browsers.BROWSERS;
    public static final Set<String> BROWSER_PACKAGES = Browsers.BROWSER_PACKAGES;

    private final Context context;
    private final Map<String, BrowserAddressState> addresses = new LinkedHashMap<String, BrowserAddressState>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BrowserAddressState> entry) {
            return size() > 32;
        }
    };
    private Set<String> webApps;
    private long webAppsAt;

    public BrowserMonitor(Context context) {
        this.context = context.getApplicationContext();
    }

    public static boolean isBrowser(String pkg) {
        return Browsers.isBrowser(pkg);
    }

    public static String addressViewId(String pkg) {
        return Browsers.addressViewId(pkg);
    }

    public static boolean isInAppBrowserActivity(String activity) {
        return Browsers.isInAppBrowserActivity(activity);
    }

    public static boolean looksLikeDomain(String text) {
        return Browsers.looksLikeDomain(text);
    }

    public Set<String> webApps() {
        long now = SystemClock.elapsedRealtime();
        if (webApps == null || now - webAppsAt > 5 * 60000L) {
            Set<String> out = new LinkedHashSet<>();
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://example.com/"));
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                for (android.content.pm.ResolveInfo resolve : context.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                    if (resolve.activityInfo != null) out.add(resolve.activityInfo.packageName);
                }
            } catch (Throwable error) {
                GuardDiagnostics.report("webApps", error);
            }
            out.remove("android"); // izbor aplikacije, nije pregledač
            webApps = out;
            webAppsAt = now;
        }
        return webApps;
    }

    /** Nova ili ažurirana aplikacija: spisak pregledača se pravi ponovo odmah, ne tek za 5 minuta. */
    public void resetApps() {
        webApps = null;
    }

    public boolean isUnknownBrowser(String pkg, boolean hasSiteRules) {
        return hasSiteRules && !isBrowser(pkg) && !context.getPackageName().equals(pkg) && webApps().contains(pkg);
    }

    /**
     * Reads only the browser's own address bar, found by its view id; page content is never read.
     * {@code activity} is the browser screen last opened, used to recognize history, bookmarks and
     * similar screens that have no address bar and cannot show a website.
     */
    public BrowserAddressState readAddress(AccessibilityNodeInfo root, String id, String activity) {
        String key = root.getPackageName() + ":" + root.getWindowId();
        BrowserAddressState state = addresses.get(key);
        if (state == null) {
            state = new BrowserAddressState();
            addresses.put(key, state);
        }
        try {
            readInto(state, root, id, activity);
        } catch (Throwable error) {
            // Zastareo čvor ili pregledač upravo menja ekran: adresa je nepoznata (posle kratkog čekanja
            // blokira se kao nepotvrđena), a provera ne pada.
            GuardDiagnostics.report("readAddress", error);
            state.missing(SystemClock.elapsedRealtime());
        }
        return state;
    }

    private void readInto(BrowserAddressState state, AccessibilityNodeInfo root, String id, String activity) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            if (BrowserAddressState.isInternalScreen(activity)) {
                state.internalScreen();
            } else if (state.host() == null) {
                // Traka nije pod poznatim imenom (npr. kartica otvorena iz druge aplikacije, nova verzija pregledača):
                // adresa se traži u gornjem delu prozora, bez čitanja stranice.
                String host = inAppHost(root);
                if (host != null) state.readable(host);
                else state.missing(SystemClock.elapsedRealtime());
            } else {
                state.missing(SystemClock.elapsedRealtime());
            }
            return;
        }
        AccessibilityNodeInfo node = nodes.get(0);
        if (node == null) {
            state.missing(SystemClock.elapsedRealtime());
        } else if (node.isFocused()) {
            state.editing();
        } else {
            CharSequence text = node.getText();
            String value = text == null ? "" : text.toString();
            if (BrowserAddressState.isInternalAddress(value)) state.internalScreen();
            else state.readable(Hosts.hostOf(WebAddress.real(value)));
        }
    }

    /** Reads only a small visible toolbar area, never the WebView page content. Null kad se adresa ne vidi. */
    public String inAppHost(AccessibilityNodeInfo root) {
        if (root == null) return null;
        try {
            android.graphics.Rect bounds = new android.graphics.Rect();
            root.getBoundsInScreen(bounds);
            int limitY = bounds.top + Math.max(bounds.height() / 4, Ui.dp(context, 160));
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            int seen = 0;
            while (!queue.isEmpty() && seen++ < 300) {
                AccessibilityNodeInfo node = queue.poll();
                if (node == null) continue;
                node.getBoundsInScreen(bounds);
                if (bounds.top > limitY) continue;
                CharSequence cls = node.getClassName();
                if (cls != null && cls.toString().contains("WebView")) continue;
                String host = Browsers.hostFromText(node.getText());
                if (host != null) return host;
                host = Browsers.hostFromText(node.getContentDescription());
                if (host != null) return host;
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.add(child);
                }
            }
        } catch (Throwable error) {
            GuardDiagnostics.report("inAppAddress", error);
        }
        return null;
    }

}
