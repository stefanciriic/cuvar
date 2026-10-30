package com.cuvar.app;

/** Zaobilazne adrese vode na pravi sajt. */
public final class WebAddressTest {
    private static int checks;

    public static void main(String[] args) {
        eq("www-reddit-com.translate.goog/r/all?_x_tr_sl=en", "www.reddit.com/r/all?_x_tr_sl=en", "Prevodilac");
        eq("https://www-my--site-com.translate.goog/", "https://www.my-site.com/", "Prevodilac sa crticom");
        eq("google.com/amp/s/www.reddit.com/r/pics", "www.reddit.com/r/pics", "Google AMP");
        eq("https://www.google.rs/amp/s/m.youtube.com/watch", "https://m.youtube.com/watch", "Google AMP .rs");
        eq("www-reddit-com.cdn.ampproject.org/c/s/www.reddit.com/r/x", "www.reddit.com/r/x", "AMP CDN");
        eq("yewtu.be/watch?v=1", "youtube.com/watch?v=1", "Invidious");
        eq("invidious.nerdvpn.de", "youtube.com", "Invidious instanca");
        eq("youtu.be/abc", "youtube.com/abc", "Kratak YouTube link");
        eq("redlib.example.org/r/all", "reddit.com/r/all", "Redlib");
        eq("www.picuki.com/profile/x", "instagram.com/profile/x", "Instagram preglednik");
        eq("reddit.com/r/all", "reddit.com/r/all", "Obična adresa ostaje");
        eq("google.com/search?q=amp", "google.com/search?q=amp", "Google pretraga ostaje");
        eq("translate.google.com", "translate.google.com", "Prevodilac sam ostaje");
        eq("notfb.com", "notfb.com", "Samo tačan domen");
        check(WebAddress.real(null) == null, "Prazno");
        check(WebAddress.isProxy("web.archive.org"), "Arhiva");
        check(WebAddress.isProxy("www.croxyproxy.com"), "Naziv sa proxy");
        check(WebAddress.isProxy("archive.ph"), "archive.ph");
        check(WebAddress.isProxy("unblockit.foo"), "unblockit.*");
        check(WebAddress.isProxy("webcache.googleusercontent.com"), "Google keš");
        check(!WebAddress.isProxy("rik.parlament.gov.rs"), "Obican sajt nije proxy");
        check(!WebAddress.isProxy("notarchive.org"), "Samo tačan domen");
        check(!WebAddress.isProxy("www.reddit.com"), "Reddit nije proxy");
        check(!WebAddress.isProxy(null), "Prazno nije proxy");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void eq(String in, String want, String what) {
        String got = WebAddress.real(in);
        check(want.equals(got), what + ": " + got);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
