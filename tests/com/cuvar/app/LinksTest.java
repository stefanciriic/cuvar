package com.cuvar.app;

import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.web.Links;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LinksTest {
    public static void main(String[] args) {
        List<String> pairs = Arrays.asList(Links.pair("com.linkedin.android", "linkedin.com"));
        if (!Links.domainsOf(pairs, "com.linkedin.android").equals(Collections.singletonList("linkedin.com")))
            throw new AssertionError("domen aplikacije");
        if (!Links.appsOf(pairs, "linkedin.com").equals(Collections.singletonList("com.linkedin.android")))
            throw new AssertionError("aplikacija sajta");

        DailySchedule.Rule app = new DailySchedule.Rule("a", "Rad", true, 9 * 60, 17 * 60);
        app.apps.add("com.linkedin.android");
        DailySchedule.Rule site = new DailySchedule.Rule("b", "Veče", true, 18 * 60, 20 * 60);
        site.sites.add("linkedin.com");
        List<DailySchedule.Rule> out = Links.expand(Arrays.asList(app, site), pairs);
        if (!out.get(0).sites.contains("linkedin.com")) throw new AssertionError("režim aplikacije ne pokriva sajt");
        if (!out.get(1).apps.contains("com.linkedin.android")) throw new AssertionError("režim sajta ne pokriva aplikaciju");
        if (app.sites.contains("linkedin.com")) throw new AssertionError("original je izmenjen");
        List<String> yt = Arrays.asList(Links.pair("com.google.android.youtube", "youtube.com"));
        if (!Links.appsOf(yt, "m.youtube.com").equals(Collections.singletonList("com.google.android.youtube")))
            throw new AssertionError("poddomen nije deo stavke");
        if (!Links.appsOf(yt, "notyoutube.com").isEmpty()) throw new AssertionError("tuđi domen je deo stavke");
        DailySchedule.Rule mobile = new DailySchedule.Rule("c", "Mob", true, 0, 60);
        mobile.sites.add("m.youtube.com");
        if (!Links.expand(Arrays.asList(mobile), yt).get(0).apps.contains("com.google.android.youtube"))
            throw new AssertionError("režim poddomena ne pokriva aplikaciju");
        String p = Links.pair("com.reddit.frontpage", "reddit.com");
        if (!"com.reddit.frontpage".equals(Links.pkgOf(p))) throw new AssertionError("paket iz para");
        if (!"reddit.com".equals(Links.domainOf(p))) throw new AssertionError("domen iz para");
        if (!Links.covers("reddit.com", "old.reddit.com")) throw new AssertionError("poddomen je pokriven");
        if (Links.covers("reddit.com", "notreddit.com")) throw new AssertionError("sličan domen nije pokriven");
        if (Links.covers("old.reddit.com", "reddit.com")) throw new AssertionError("glavni domen nije pokriven poddomenom");
        List<String> fb = Arrays.asList(Links.pair("com.facebook.katana", "facebook.com"), Links.pair("com.facebook.lite", "facebook.com"));
        if (!Links.appsOf(fb, "facebook.com").equals(Arrays.asList("com.facebook.katana", "com.facebook.lite")))
            throw new AssertionError("dve aplikacije istog sajta");
        if (!Links.domainsOf(fb, "com.other").isEmpty()) throw new AssertionError("aplikacija bez para");
        for (java.util.Map.Entry<String, String> e : Links.KNOWN.entrySet()) {
            String k = Links.pair(e.getKey(), e.getValue());
            if (!e.getKey().equals(Links.pkgOf(k)) || !e.getValue().equals(Links.domainOf(k))) throw new AssertionError("poznat par " + e.getKey());
            if (!e.getValue().contains(".") || e.getValue().contains("|")) throw new AssertionError("poznat domen " + e.getValue());
        }
        System.out.println("LinksTest: prošlo 16 provera.");
    }
}
