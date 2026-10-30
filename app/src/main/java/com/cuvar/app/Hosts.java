package com.cuvar.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Host iz ukucane ili pročitane adrese i glavni domen hosta. */
final class Hosts {
    private Hosts() {
    }

    /** Iz adrese vadi čist domen: "https://www.Facebook.com/x" -> "facebook.com". */
    static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        // Adresa ukucana ćirilicom (линкедин.цом) postaje latinica (linkedin.com).
        String s = Translit.toLatin(url).replaceAll("[\\u200b-\\u200f\\u202a-\\u202e\\u2066-\\u2069]", "")
                .trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.contains(" ")) {
            return null;
        }
        int i = s.indexOf("://");
        if (i >= 0) {
            s = s.substring(i + 3);
        }
        int cut = s.length();
        for (char ch : new char[]{'/', '?', '#'}) {
            int k = s.indexOf(ch);
            if (k >= 0 && k < cut) {
                cut = k;
            }
        }
        s = s.substring(0, cut);
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(0, colon);
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? null : s;
    }

    /** Drugi nivo ispod državnog domena, npr. "co" u "bbc.co.uk" ili "org" u "nesto.org.rs". */
    private static final Set<String> SECOND_LEVEL = new HashSet<>(Arrays.asList(
            "co", "com", "org", "net", "edu", "gov", "ac", "in", "or", "ne", "go"));

    /**
     * Glavni domen hosta, da se poddomeni sabiraju zajedno:
     * "m.youtube.com" -> "youtube.com", "news.bbc.co.uk" -> "bbc.co.uk". Null ako ne liči na domen.
     */
    static String mainDomain(String host) {
        if (host == null || host.indexOf('.') < 0 || !host.matches("[\\p{L}\\p{N}.-]+")) {
            return null;
        }
        if (host.matches("[0-9.]+")) {
            return host; // IP adresa
        }
        List<String> parts = new ArrayList<>();
        for (String part : host.split("\\.")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        int n = parts.size();
        if (n < 2) {
            return null;
        }
        int keep = n >= 3 && parts.get(n - 1).length() == 2 && SECOND_LEVEL.contains(parts.get(n - 2)) ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = n - Math.min(keep, n); i < n; i++) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
