package com.cuvar.app;

import java.util.Arrays;
import java.util.List;

/** Pokretanje: javac -d .review/schedule app/src/main/java/com/cuvar/app/DailySchedule.java tests/com/cuvar/app/DailyScheduleTest.java
 *  java -cp .review/schedule com.cuvar.app.DailyScheduleTest */
public final class DailyScheduleTest {
    private static int checks;

    public static void main(String[] args) {
        for (int minute = 0; minute < 1440; minute++) {
            check(DailySchedule.contains(1260, 540, minute) == (minute >= 1260 || minute < 540), "Noćni period", minute);
            check(DailySchedule.contains(540, 1020, minute) == (minute >= 540 && minute < 1020), "Dnevni period", minute);
            check(!DailySchedule.contains(540, 540, minute), "Prazan period", minute);
            check(DailySchedule.contains(0, 1, minute) == (minute == 0), "Početak u ponoć", minute);
            check(DailySchedule.contains(1439, 0, minute) == (minute == 1439), "Kraj u ponoć", minute);
        }
        check("21:00".equals(DailySchedule.label(1260)), "Format početka", 1260);
        check("09:00".equals(DailySchedule.label(540)), "Format kraja", 540);
        multipleRules();
        System.out.println("Prošlo: " + checks + " provera.");
    }

    /** Više režima: svaki sa svojim periodom (i preko ponoći) i svojim aplikacijama i sajtovima. */
    private static void multipleRules() {
        DailySchedule.Rule night = rule("noc", true, 22 * 60, 7 * 60, "com.game", "youtube.com");
        DailySchedule.Rule school = rule("skola", true, 8 * 60, 14 * 60, "com.chat", "tiktok.com");
        DailySchedule.Rule evening = rule("vece", true, 20 * 60, 23 * 60, "com.chat", "m.example.org");
        DailySchedule.Rule off = rule("iskljucen", false, 0, 1439, "com.mail", "mail.com");
        night.apps.add("com.video");
        List<DailySchedule.Rule> rules = Arrays.asList(night, school, evening, off);

        for (int minute = 0; minute < 1440; minute++) {
            boolean inNight = minute >= 22 * 60 || minute < 7 * 60;
            boolean inSchool = minute >= 8 * 60 && minute < 14 * 60;
            boolean inEvening = minute >= 20 * 60 && minute < 23 * 60;

            check(is(DailySchedule.blockingApp(rules, "com.game", minute), inNight ? night : null), "Igra noću", minute);
            check(is(DailySchedule.blockingApp(rules, "com.video", minute), inNight ? night : null), "Druga aplikacija noću", minute);
            DailySchedule.Rule chat = DailySchedule.blockingApp(rules, "com.chat", minute);
            check(is(chat, inSchool ? school : inEvening ? evening : null), "Čet u školi i uveče", minute);
            check(DailySchedule.blockingApp(rules, "com.mail", minute) == null, "Isključen režim", minute);
            check(DailySchedule.blockingApp(rules, "com.other", minute) == null, "Aplikacija van režima", minute);

            check(is(DailySchedule.blockingSite(rules, "youtube.com", minute), inNight ? night : null), "Sajt noću", minute);
            check(is(DailySchedule.blockingSite(rules, "m.youtube.com", minute), inNight ? night : null), "Poddomen noću", minute);
            check(DailySchedule.blockingSite(rules, "notyoutube.com", minute) == null, "Sličan domen", minute);
            check(is(DailySchedule.blockingSite(rules, "tiktok.com", minute), inSchool ? school : null), "Sajt u školi", minute);
            check(DailySchedule.blockingSite(rules, "example.org", minute) == null, "Roditeljski domen", minute);
            check(is(DailySchedule.blockingSite(rules, "a.m.example.org", minute), inEvening ? evening : null), "Poddomen uveče", minute);
            check(DailySchedule.blockingSite(rules, "mail.com", minute) == null, "Sajt isključenog režima", minute);
        }
        check("22:00–07:00".equals(night.label()), "Oznaka režima", 0);
        check("youtube.com".equals(DailySchedule.matchDomain("www.m.youtube.com", night.sites)), "Pronađen domen", 0);
        check(DailySchedule.blockingApp(Arrays.asList(), "com.game", 0) == null, "Bez režima", 0);
    }

    private static DailySchedule.Rule rule(String name, boolean enabled, int start, int end, String app, String site) {
        DailySchedule.Rule r = new DailySchedule.Rule(name, name, enabled, start, end);
        r.apps.add(app);
        r.sites.add(site);
        return r;
    }

    private static boolean is(DailySchedule.Rule actual, DailySchedule.Rule expected) {
        return actual == expected;
    }

    private static void check(boolean result, String label, int minute) {
        checks++;
        if (!result) throw new AssertionError(label + ": " + minute);
    }
}
