package com.cuvar.app.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Poznati pregledači i prepoznavanje adrese iz teksta, bez Android-a (testira se na računaru). */
public final class Browsers {
    private static final Pattern IN_APP_BROWSER = Pattern.compile(
            "(?i).*(browserlite|inappbrowser|browseractivity|webviewactivity|webbrowser|customtab).*");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(:[0-9]+)?([/?#]\\S*)?$");

    /** paket pregledača -> ID polja za adresu */
    public static final Map<String, String> BROWSERS;
    public static final Set<String> BROWSER_PACKAGES;

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
        // Adrese za ove pregledače nisu proverene na telefonu; ako se ne nađu, važi isto što i bez pročitane adrese.
        browsers.put("org.mozilla.firefox_beta", "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view");
        browsers.put("org.mozilla.fenix", "org.mozilla.fenix:id/mozac_browser_toolbar_url_view");
        browsers.put("org.mozilla.focus", "org.mozilla.focus:id/mozac_browser_toolbar_url_view");
        browsers.put("com.opera.browser", "com.opera.browser:id/url_field");
        browsers.put("com.opera.mini.native", "com.opera.mini.native:id/url_field");
        browsers.put("com.duckduckgo.mobile.android", "com.duckduckgo.mobile.android:id/omnibarTextInput");
        BROWSERS = Collections.unmodifiableMap(browsers);
        BROWSER_PACKAGES = Collections.unmodifiableSet(new LinkedHashSet<>(browsers.keySet()));
    }

    private Browsers() {
    }

    public static boolean isBrowser(String pkg) {
        return pkg != null && BROWSERS.containsKey(pkg);
    }

    /** ID polja za adresu u pregledaču, ili null kad paket nije poznat pregledač. */
    public static String addressViewId(String pkg) {
        return pkg == null ? null : BROWSERS.get(pkg);
    }

    /** Ekran aplikacije koji je ugrađen pregledač (Facebook, Instagram, Custom Tabs...). */
    public static boolean isInAppBrowserActivity(String activity) {
        return activity != null && IN_APP_BROWSER.matcher(activity).matches();
    }

    public static boolean looksLikeDomain(String text) {
        return text != null && DOMAIN.matcher(text).matches();
    }

    /** Domen iz teksta sa trake ugrađenog pregledača, ili null kad tekst nije adresa (ili je predugačak). */
    public static String hostFromText(CharSequence value) {
        if (value == null || value.length() > 200) return null;
        String text = value.toString().trim();
        return looksLikeDomain(text) ? Hosts.hostOf(WebAddress.real(text)) : null;
    }
}
