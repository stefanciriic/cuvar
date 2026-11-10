package com.cuvar.app.web;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prava adresa iza zaobilaznih oblika: Google Prevodilac (www-reddit-com.translate.goog),
 * Google AMP (google.com/amp/s/www.reddit.com/...) i poznati zamenski sajtovi (yewtu.be za YouTube).
 * Bez ovoga bi blokiran sajt prošao kroz tuđu adresu.
 */
public final class WebAddress {
    private static final Pattern GOOGLE = Pattern.compile("^(www\\.)?google\\.[a-z.]+$");
    private static final Pattern AMP = Pattern.compile("^/amp/(?:s/)?([a-z0-9-]+(?:\\.[a-z0-9-]+)+)(.*)$");
    private static final Pattern AMP_CDN = Pattern.compile("^/[cv]/(?:s/)?([a-z0-9-]+(?:\\.[a-z0-9-]+)+)(.*)$");
    /** Zamenski sajtovi (isti sadržaj pod drugim imenom): početak ili ceo naziv → pravi sajt. */
    private static final String[][] ALIASES = {
            {"yewtu.be", "youtube.com"},
            {"youtu.be", "youtube.com"},
            {"youtube-nocookie.com", "youtube.com"},
            {"piped.video", "youtube.com"},
            {"invidious.", "youtube.com"},
            {"piped.", "youtube.com"},
            {"redlib.", "reddit.com"},
            {"libreddit.", "reddit.com"},
            {"teddit.net", "reddit.com"},
            {"redd.it", "reddit.com"},
            {"picuki.com", "instagram.com"},
            {"imginn.com", "instagram.com"},
            {"instagr.am", "instagram.com"},
            {"fb.com", "facebook.com"},
            {"m.me", "facebook.com"},
            {"vm.tiktok.com", "tiktok.com"},
    };

    private WebAddress() { }

    /** Adresa kakva se vidi u traci pregledača → adresa sajta koji se zaista gleda. */
    public static String real(String text) {
        if (text == null) return null;
        String s = text.trim().toLowerCase(Locale.ROOT);
        String scheme = "";
        int i = s.indexOf("://");
        if (i >= 0) {
            scheme = s.substring(0, i + 3);
            s = s.substring(i + 3);
        }
        int cut = s.length();
        for (char ch : new char[]{'/', '?', '#'}) {
            int k = s.indexOf(ch);
            if (k >= 0 && k < cut) cut = k;
        }
        String host = s.substring(0, cut);
        String rest = s.substring(cut);
        // "korisnik@sajt" i tačka na kraju ("yewtu.be.") su ista adresa: bez ovoga zaobilazna adresa ne bi bila prepoznata.
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.startsWith("www.")) host = host.substring(4);

        if (GOOGLE.matcher(host).matches()) {
            Matcher m = AMP.matcher(rest);
            if (m.matches()) return scheme + m.group(1) + m.group(2);
        }
        if (host.endsWith("ampproject.org")) {
            Matcher m = AMP_CDN.matcher(rest);
            if (m.matches()) return scheme + m.group(1) + m.group(2);
        }
        if (host.endsWith(".translate.goog")) {
            String label = host.substring(0, host.length() - ".translate.goog".length());
            if (!label.isEmpty() && !label.contains(".")) {
                return scheme + label.replace("--", "\u0000").replace('-', '.').replace('\u0000', '-') + rest;
            }
        }
        for (String[] alias : ALIASES) {
            boolean prefix = alias[0].endsWith(".");
            if (prefix ? host.startsWith(alias[0]) : (host.equals(alias[0]) || host.endsWith("." + alias[0]))) {
                return scheme + alias[1] + rest;
            }
        }
        return text.trim();
    }
}
