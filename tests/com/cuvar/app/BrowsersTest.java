package com.cuvar.app;

import com.cuvar.app.web.Browsers;

/** Poznati pregledači, ugrađeni pregledači u aplikacijama i adresa iz teksta trake. */
public final class BrowsersTest {
    private static int checks;

    public static void main(String[] args) {
        // Poznati pregledači i polje za adresu.
        check(Browsers.isBrowser("com.android.chrome"), "Chrome je pregledač");
        check(Browsers.isBrowser("org.mozilla.firefox") && Browsers.isBrowser("com.sec.android.app.sbrowser"), "Firefox i Samsung");
        check(!Browsers.isBrowser("com.whatsapp") && !Browsers.isBrowser(null) && !Browsers.isBrowser(""), "Ostalo nije pregledač");
        check("com.android.chrome:id/url_bar".equals(Browsers.addressViewId("com.android.chrome")), "Chrome traka");
        check(Browsers.addressViewId("com.whatsapp") == null && Browsers.addressViewId(null) == null, "Bez trake za ostalo");
        for (String pkg : Browsers.BROWSER_PACKAGES) {
            String id = Browsers.addressViewId(pkg);
            check(id != null && id.startsWith(pkg + ":id/"), "ID trake pripada svom pregledaču: " + pkg);
        }
        check(Browsers.BROWSER_PACKAGES.size() == Browsers.BROWSERS.size(), "Spisak paketa i mapa se slažu");
        boolean fixed = false;
        try {
            Browsers.BROWSERS.put("x", "y");
        } catch (UnsupportedOperationException e) {
            fixed = true;
        }
        check(fixed, "Spisak se ne menja spolja");

        // Ugrađeni pregledač u aplikaciji.
        check(Browsers.isInAppBrowserActivity("com.facebook.browser.lite.BrowserLiteActivity"), "Facebook BrowserLite");
        check(Browsers.isInAppBrowserActivity("com.instagram.simplewebview.SimpleWebViewActivity"), "Instagram WebView");
        check(Browsers.isInAppBrowserActivity("org.chromium.chrome.browser.customtabs.CustomTabActivity"), "Custom Tabs");
        check(Browsers.isInAppBrowserActivity("x.InAppBrowserActivity"), "InAppBrowser, bez obzira na velika slova");
        check(!Browsers.isInAppBrowserActivity("com.whatsapp.HomeActivity") && !Browsers.isInAppBrowserActivity(null),
                "Obična aktivnost nije pregledač");

        // Da li tekst liči na adresu.
        check(Browsers.looksLikeDomain("youtube.com"), "domen");
        check(Browsers.looksLikeDomain("https://m.youtube.com/watch?v=1"), "puna adresa");
        check(Browsers.looksLikeDomain("example.co.uk:8080/a"), "port i putanja");
        check(!Browsers.looksLikeDomain("Pretraži ili upiši adresu"), "tekst sa razmacima nije adresa");
        check(!Browsers.looksLikeDomain("localhost") && !Browsers.looksLikeDomain("a.b"), "bez prave završnice nije adresa");
        check(!Browsers.looksLikeDomain(null) && !Browsers.looksLikeDomain(""), "prazno nije adresa");

        // Domen iz teksta trake ugrađenog pregledača.
        check("youtube.com".equals(Browsers.hostFromText("  https://www.YouTube.com/watch?v=1  ")), "čist domen iz adrese");
        check("instagram.com".equals(Browsers.hostFromText("instagram.com")), "samo domen");
        check(Browsers.hostFromText("Podeli") == null, "dugme nije adresa");
        check(Browsers.hostFromText(null) == null, "null");
        StringBuilder longText = new StringBuilder("a.com/");
        while (longText.length() <= 200) longText.append('x');
        check(Browsers.hostFromText(longText) == null, "predugačak tekst (sadržaj stranice) se ne čita kao adresa");
        System.out.println("BrowsersTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
