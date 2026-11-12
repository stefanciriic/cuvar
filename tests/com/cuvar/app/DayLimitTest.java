package com.cuvar.app;

import com.cuvar.app.rules.DayLimit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Pokretanje: javac -d .review/daylimit app/src/main/java/com/cuvar/app/rules/DayLimit.java tests/com/cuvar/app/DayLimitTest.java
 *  java -cp .review/daylimit com.cuvar.app.DayLimitTest */
public final class DayLimitTest {
    private static int checks;

    public static void main(String[] args) {
        // Strožiji limit (ili uključivanje) važi odmah, blaži ili isključen tek od sutra.
        check(DayLimit.appliesNow(0, 180), "Uključivanje važi odmah");
        check(DayLimit.appliesNow(180, 120), "Smanjenje važi odmah");
        check(DayLimit.appliesNow(180, 180), "Ista vrednost");
        check(!DayLimit.appliesNow(180, 240), "Povećanje čeka sutra");
        check(!DayLimit.appliesNow(180, 0), "Isključivanje čeka sutra");

        check(DayLimit.effective(180, 240, "20261009", "20261008") == 180, "Danas važi stara vrednost");
        check(DayLimit.effective(180, 240, "20261009", "20261009") == 240, "Sutra važi nova vrednost");
        check(DayLimit.effective(180, 0, "20261009", "20261012") == 0, "Isključen posle nekoliko dana");
        check(DayLimit.effective(180, -1, null, "20261012") == 180, "Bez zakazane promene");

        long min = 60000L;
        check(!DayLimit.reached(0, 1000 * min), "Isključen limit nikad nije potrošen");
        check(!DayLimit.reached(180, 179 * min), "Pre limita");
        check(DayLimit.reached(180, 180 * min), "Tačno na limitu");
        check(!DayLimit.warn(180, 164 * min), "Rano za upozorenje");
        check(DayLimit.warn(180, 165 * min), "Upozorenje 15 min ranije");
        check(!DayLimit.warn(180, 180 * min), "Posle limita nema upozorenja");

        // Povećanje: što je limit veći, to manji procenat.
        check(DayLimit.maxRaise(0) == 0, "Bez limita nema povećanja");
        check(DayLimit.maxRaise(30) == 9, "30 min: +30 %");
        check(DayLimit.maxRaise(60) == 18, "1 h: +30 %");
        check(DayLimit.maxRaise(120) == 24, "2 h: +20 %");
        check(DayLimit.maxRaise(180) == 27, "3 h: +15 %");
        check(DayLimit.maxRaise(240) == 0, "4 h je najviše: nema povećanja");
        check(DayLimit.maxRaise(600) == 0, "Preko 4 h nema povećanja");
        check(DayLimit.maxRaise(230) == 10, "Povećanje ne prelazi 4 h");
        check(DayLimit.maxRaise(210) == 26, "3 h 30 min: +12,5 %");
        for (int m = 1; m <= 300; m++) {
            check(m + DayLimit.maxRaise(m) <= Math.max(m, DayLimit.MAX), "Povećanje nikad preko MAX");
        }
        for (int c : DayLimit.CHOICES) check(c <= DayLimit.MAX, "Izbor najviše 4 h");

        // Gornja granica: stari 5 h postaje 4 h, isključen ostaje isključen.
        check(DayLimit.clamp(300) == 240, "5 h postaje 4 h");
        check(DayLimit.clamp(180) == 180, "3 h ostaje");
        check(DayLimit.clamp(0) == 0, "Isključen ostaje 0");
        check(DayLimit.budget(120) == 120, "Granica zbira je ukupni limit");
        check(DayLimit.budget(0) == 240, "Bez ukupnog limita granica je 4 h");
        check(DayLimit.budget(300) == 240, "Granica ni tada preko 4 h");

        // Zbir limita aplikacija i sajtova ne sme preći granicu.
        check(!DayLimit.allowedItemLimit(300, 0, 0, 120), "5 h na jednu aplikaciju uz ukupni 2 h");
        check(DayLimit.allowedItemLimit(60, 0, 60, 120), "Druga aplikacija od 1 h staje u 2 h");
        check(!DayLimit.allowedItemLimit(60, 0, 120, 120), "Treća aplikacija od 1 h ne staje");
        check(DayLimit.allowedItemLimit(0, 60, 500, 120), "Bez limita uvek može");
        check(DayLimit.allowedItemLimit(30, 60, 500, 120), "Smanjenje uvek može, i kad je zbir preko");
        check(DayLimit.allowedItemLimit(60, 60, 500, 120), "Ista vrednost uvek može");
        check(!DayLimit.allowedItemLimit(90, 60, 100, 120), "Povećanje preko granice ne može");
        check(DayLimit.allowedItemLimit(20, 0, 100, 120), "Tačno do granice može");
        Map<String, Integer> limits = new HashMap<>();
        limits.put("youtube.com", 120);
        limits.put("m.youtube.com", 60);
        limits.put("reddit.com", 0);
        limits.put("tube.com", 90);
        check(DayLimit.parentLimit("music.m.youtube.com", limits) == 60, "Najmanji limit roditelja");
        check(DayLimit.parentLimit("www.youtube.com", limits) == 120, "Roditelj youtube.com");
        check(DayLimit.parentLimit("youtube.com", limits) == 0, "Sam sebi nije roditelj, tube.com nije roditelj");
        check(DayLimit.parentLimit("old.reddit.com", limits) == 0, "Blokiran roditelj nema limit");
        check(DayLimit.roomFor(100, 120) == 20, "Ostalo 20 min");
        check(DayLimit.roomFor(500, 120) == 0, "Nema mesta, nikad negativno");
        for (int m = 1; m < 600; m++) {
            check(DayLimit.raisePercent(m + 1) <= DayLimit.raisePercent(m), "Procenat ne raste");
        }

        // Limit troši samo vreme pod pravilima: sat vremena mapa i poruka ne ulazi u zbir.
        Map<String, Long> day = new HashMap<>();
        day.put("com.google.android.apps.maps", 60 * min);
        day.put("com.whatsapp", 20 * min);
        day.put("com.instagram.android", 25 * min);
        day.put("com.android.chrome", 40 * min);
        day.put("site:reddit.com", 15 * min);
        day.put("site:old.reddit.com", 5 * min);
        day.put("site:example.org", 9 * min);
        day.put("web:reddit.com", 15 * min);
        day.put("web:wikipedia.org", 20 * min);
        long guarded = DayLimit.guardedEstimate(day, Arrays.asList("com.instagram.android", "com.reddit.frontpage"),
                Arrays.asList("reddit.com", "old.reddit.com"));
        check(guarded == 40 * min, "Aplikacija i sajt sa pravilom, bez mapa, pregledača i sajtova bez pravila");
        check(!DayLimit.reached(99, guarded), "Mape ne troše limit");
        check(DayLimit.reached(99, 145 * min), "Sve vreme na telefonu bi ga potrošilo");
        check(DayLimit.guardedEstimate(day, Collections.<String>emptyList(), Arrays.asList("old.reddit.com")) == 5 * min,
                "Poddomen sa pravilom bez roditelja se računa sam");
        check(DayLimit.guardedEstimate(day, Collections.<String>emptyList(), Collections.<String>emptyList()) == 0,
                "Bez pravila nema vremena pod pravilima");
        check(DayLimit.guardedEstimate(new HashMap<String, Long>(), Arrays.asList("com.instagram.android"),
                Arrays.asList("reddit.com")) == 0, "Dan bez merenja");

        check("3 h".equals(DayLimit.label(180)), "Natpis 3 h");
        check("1 h 30 min".equals(DayLimit.label(90)), "Natpis 1 h 30 min");
        check("Isključen".equals(DayLimit.label(0)), "Natpis isključen");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
