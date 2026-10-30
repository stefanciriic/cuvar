package com.cuvar.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Browser package recognition and low-cost address observation for GuardService. */
final class BrowserMonitor {
    private static final Pattern IN_APP_BROWSER = Pattern.compile(
            "(?i).*(browserlite|inappbrowser|browseractivity|webviewactivity|webbrowser|customtab).*");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(:[0-9]+)?([/?#]\\S*)?$");

    static final Map<String, String> BROWSERS;
    static final Set<String> BROWSER_PACKAGES;

    static {
        Map<String, String> browsers = new LinkedHashMap<>();
        browsers.put("com.android.chrome", "com.android.chrome:id/url_bar");
        browsers.put("com.chrome.beta", "com.chrome.beta:id/url_bar");
        browsers.put("com.brave.browser", "com.brave.browser:id/url_bar");
        browsers.put("com.microsoft.emmx", "com.microsoft.emmx:id/url_bar");
        browsers.put("com.vivaldi.browser", "com.vivaldi.browser:id/url_bar");
        browsers.put("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text");
        browsers.put("org.mozilla.firefox", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view");
        browsers.put("com.chrome.dev", "com.chrome.dev:id/url_bar");
        browsers.put("com.chrome.canary", "com.chrome.canary:id/url_bar");
        browsers.put("com.kiwibrowser.browser", "com.kiwibrowser.browser:id/url_bar");
        BROWSERS = Collections.unmodifiableMap(browsers);
        BROWSER_PACKAGES = Collections.unmodifiableSet(new LinkedHashSet<>(browsers.keySet()));
    }

    private final Context context;
    private final Map<String, BrowserAddressState> addresses = new LinkedHashMap<String, BrowserAddressState>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BrowserAddressState> entry) {
            return size() > 32;
        }
    };
    private Set<String> webApps;
    private long webAppsAt;

    BrowserMonitor(Context context) {
        this.context = context.getApplicationContext();
    }

    static boolean isBrowser(String pkg) {
        return BROWSERS.containsKey(pkg);
    }

    static String addressViewId(String pkg) {
        return BROWSERS.get(pkg);
    }

    static boolean isInAppBrowserActivity(String activity) {
        return activity != null && IN_APP_BROWSER.matcher(activity).matches();
    }

    static boolean looksLikeDomain(String text) {
        return text != null && DOMAIN.matcher(text).matches();
    }

    Set<String> webApps() {
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
            } catch (Throwable ignored) {
            }
            out.remove("android"); // izbor aplikacije, nije pregledač
            webApps = out;
            webAppsAt = now;
        }
        return webApps;
    }

    /** Nova ili ažurirana aplikacija: spisak pregledača se pravi ponovo odmah, ne tek za 5 minuta. */
    void resetApps() {
        webApps = null;
    }

    boolean isUnknownBrowser(String pkg, boolean hasSiteRules) {
        return hasSiteRules && !isBrowser(pkg) && !context.getPackageName().equals(pkg) && webApps().contains(pkg);
    }

    /**
     * Reads only the browser's own address bar, found by its view id; page content is never read.
     * {@code activity} is the browser screen last opened, used to recognize history, bookmarks and
     * similar screens that have no address bar and cannot show a website.
     */
    BrowserAddressState readAddress(AccessibilityNodeInfo root, String id, String activity) {
        String key = root.getPackageName() + ":" + root.getWindowId();
        BrowserAddressState state = addresses.get(key);
        if (state == null) {
            state = new BrowserAddressState();
            addresses.put(key, state);
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            if (BrowserAddressState.isInternalScreen(activity)) state.internalScreen();
            else state.missing(SystemClock.elapsedRealtime());
            return state;
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
        return state;
    }

    /** Reads only a small visible toolbar area, never the WebView page content. */
    String inAppHost(AccessibilityNodeInfo root) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        root.getBoundsInScreen(bounds);
        int limitY = bounds.top + Math.max(bounds.height() / 4, Ui.dp(context, 160));
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen = 0;
        try {
            while (!queue.isEmpty() && seen++ < 300) {
                AccessibilityNodeInfo node = queue.poll();
                if (node == null) continue;
                node.getBoundsInScreen(bounds);
                if (bounds.top > limitY) continue;
                CharSequence cls = node.getClassName();
                if (cls != null && cls.toString().contains("WebView")) continue;
                String host = addHost(node.getText());
                if (host != null) return host;
                host = addHost(node.getContentDescription());
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

    private static String addHost(CharSequence value) {
        if (value == null || value.length() > 200) return null;
        String text = value.toString().trim();
        return looksLikeDomain(text) ? Hosts.hostOf(WebAddress.real(text)) : null;
    }
}
