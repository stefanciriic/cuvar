package com.cuvar.app;

import com.cuvar.app.rules.NotifCounts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Brojanje notifikacija po danu i aplikaciji. */
public final class NotifCountsTest {
    private static int checks;

    public static void main(String[] args) {
        check(NotifCounts.counts(false, false, false, false), "Obična notifikacija");
        check(!NotifCounts.counts(true, false, false, false), "Trajna se ne broji");
        check(!NotifCounts.counts(false, true, false, false), "Zbirna se ne broji");
        check(!NotifCounts.counts(false, false, true, true), "Tiha izmena se ne broji");
        check(NotifCounts.counts(false, false, false, true), "Nova poruka u istoj notifikaciji se broji");

        Map<String, Map<String, Integer>> d = new HashMap<>();
        NotifCounts.add(d, "20261031", "com.whatsapp");
        NotifCounts.add(d, "20261031", "com.whatsapp");
        NotifCounts.add(d, "20261030", "com.viber.voip");
        NotifCounts.add(d, "20261001", "old.app");
        NotifCounts.add(d, "20261031", "com.linkedin.android");
        check(!d.containsKey("20261001"), "Stari dani se brišu");
        check(d.get("20261031").get("com.whatsapp") == 2, "Dve od WhatsApp-a");

        Map<String, Map<String, Integer>> back = NotifCounts.decode(NotifCounts.encode(d));
        check(back.equals(d), "Zapis i čitanje");
        check(NotifCounts.decode("").isEmpty() && NotifCounts.decode("x;:;1:a=b").get("1").isEmpty(), "Loš zapis");

        Map<String, Integer> per = NotifCounts.perDay(d);
        check(per.get("20261031") == 3 && per.get("20261030") == 1, "Zbir po danu");
        List<Map.Entry<String, Integer>> top = NotifCounts.topApps(d, "20261031", 7);
        check(top.get(0).getKey().equals("com.whatsapp") && top.size() == 3, "Najviše notifikacija");
        check(NotifCounts.topApps(d, "20261031", 1).size() == 2, "Samo danas");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
