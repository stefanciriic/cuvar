package com.cuvar.app;

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
        System.out.println("LinksTest: prošlo 5 provera.");
    }
}
