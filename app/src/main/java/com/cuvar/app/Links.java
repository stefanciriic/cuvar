package com.cuvar.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplikacija i njen sajt kao jedna stavka (npr. LinkedIn = aplikacija i linkedin.com):
 * pravila jedne važe i za drugu, a vreme se broji zajedno.
 */
final class Links {
    private Links() { }

    /** Poznati parovi; povezuju se sami, a mogu se razdvojiti ili promeniti. */
    static final Map<String, String> KNOWN = new HashMap<>();

    static {
        KNOWN.put("com.google.android.youtube", "youtube.com");
        KNOWN.put("com.instagram.android", "instagram.com");
        KNOWN.put("com.facebook.katana", "facebook.com");
        KNOWN.put("com.facebook.lite", "facebook.com");
        KNOWN.put("com.reddit.frontpage", "reddit.com");
        KNOWN.put("com.linkedin.android", "linkedin.com");
        KNOWN.put("com.zhiliaoapp.musically", "tiktok.com");
        KNOWN.put("com.ss.android.ugc.trill", "tiktok.com");
        KNOWN.put("com.twitter.android", "x.com");
        KNOWN.put("com.pinterest", "pinterest.com");
        KNOWN.put("tv.twitch.android.app", "twitch.tv");
        KNOWN.put("com.netflix.mediaclient", "netflix.com");
    }

    static String pair(String pkg, String domain) {
        return pkg + "|" + domain;
    }

    static String pkgOf(String pair) {
        return pair.substring(0, pair.indexOf('|'));
    }

    static String domainOf(String pair) {
        return pair.substring(pair.indexOf('|') + 1);
    }

    static List<String> domainsOf(Collection<String> pairs, String pkg) {
        List<String> out = new ArrayList<>();
        for (String p : pairs) if (pkgOf(p).equals(pkg)) out.add(domainOf(p));
        return out;
    }

    static List<String> appsOf(Collection<String> pairs, String domain) {
        List<String> out = new ArrayList<>();
        for (String p : pairs) if (domainOf(p).equals(domain)) out.add(pkgOf(p));
        return out;
    }

    /** Režimi u kojima je uz aplikaciju i njen sajt, i obrnuto. */
    static List<DailySchedule.Rule> expand(List<DailySchedule.Rule> rules, Collection<String> pairs) {
        List<DailySchedule.Rule> out = new ArrayList<>();
        for (DailySchedule.Rule r : rules) {
            DailySchedule.Rule c = DailySchedule.copy(r, r.id);
            for (String p : pairs) {
                String pkg = pkgOf(p), dom = domainOf(p);
                if (r.apps.contains(pkg)) c.sites.add(dom);
                if (r.sites.contains(dom)) c.apps.add(pkg);
            }
            out.add(c);
        }
        return out;
    }
}
