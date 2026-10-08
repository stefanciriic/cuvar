package com.cuvar.app;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Pokretanje: javac -d .review/schedule app/src/main/java/com/cuvar/app/DailySchedule.java app/src/main/java/com/cuvar/app/DailyCode.java tests/com/cuvar/app/DailyScheduleTest.java
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
        weekDays();
        dailyCode();
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

            check(is(DailySchedule.blockingApp(rules, "com.game", minute, 0), inNight ? night : null), "Igra noću", minute);
            check(is(DailySchedule.blockingApp(rules, "com.video", minute, 0), inNight ? night : null), "Druga aplikacija noću", minute);
            DailySchedule.Rule chat = DailySchedule.blockingApp(rules, "com.chat", minute, 0);
            check(is(chat, inSchool ? school : inEvening ? evening : null), "Čet u školi i uveče", minute);
            check(DailySchedule.blockingApp(rules, "com.mail", minute, 0) == null, "Isključen režim", minute);
            check(DailySchedule.blockingApp(rules, "com.other", minute, 0) == null, "Aplikacija van režima", minute);

            check(is(DailySchedule.blockingSite(rules, "youtube.com", minute, 0), inNight ? night : null), "Sajt noću", minute);
            check(is(DailySchedule.blockingSite(rules, "m.youtube.com", minute, 0), inNight ? night : null), "Poddomen noću", minute);
            check(DailySchedule.blockingSite(rules, "notyoutube.com", minute, 0) == null, "Sličan domen", minute);
            check(is(DailySchedule.blockingSite(rules, "tiktok.com", minute, 0), inSchool ? school : null), "Sajt u školi", minute);
            check(DailySchedule.blockingSite(rules, "example.org", minute, 0) == null, "Roditeljski domen", minute);
            check(is(DailySchedule.blockingSite(rules, "a.m.example.org", minute, 0), inEvening ? evening : null), "Poddomen uveče", minute);
            check(DailySchedule.blockingSite(rules, "mail.com", minute, 0) == null, "Sajt isključenog režima", minute);
        }
        check("22:00–07:00".equals(night.label()), "Oznaka režima", 0);
        check("youtube.com".equals(DailySchedule.matchDomain("www.m.youtube.com", night.sites)), "Pronađen domen", 0);
        check(DailySchedule.blockingApp(Arrays.asList(), "com.game", 0, 0) == null, "Bez režima", 0);
    }

    /** Dani u nedelji (0 = ponedeljak) i dnevna šifra van perioda. */
    private static void weekDays() {
        DailySchedule.Rule work = rule("posao", true, 9 * 60, 17 * 60, "com.game", "youtube.com");
        work.days = DailySchedule.WORK_DAYS;
        work.code = true;
        DailySchedule.Rule fridayNight = rule("petak", true, 22 * 60, 6 * 60, "com.chat", "tiktok.com");
        fridayNight.days = 1 << 4;
        List<DailySchedule.Rule> rules = Arrays.asList(work, fridayNight);
        for (int day = 0; day < 7; day++) {
            for (int minute = 0; minute < 1440; minute++) {
                boolean inWork = day < 5 && minute >= 9 * 60 && minute < 17 * 60;
                check(is(DailySchedule.blockingApp(rules, "com.game", minute, day), inWork ? work : null), "Radno vreme dan " + day, minute);
                check(is(DailySchedule.blockingSite(rules, "m.youtube.com", minute, day), inWork ? work : null), "Sajt u radno vreme dan " + day, minute);
                boolean inFriday = (day == 4 && minute >= 22 * 60) || (day == 5 && minute < 6 * 60);
                check(is(DailySchedule.blockingApp(rules, "com.chat", minute, day), inFriday ? fridayNight : null), "Petak uveče do subote ujutru dan " + day, minute);
            }
        }
        check("radnim danima".equals(work.daysLabel()), "Oznaka radnih dana", 0);
        check("pet".equals(fridayNight.daysLabel()), "Oznaka jednog dana", 0);
        check(is(DailySchedule.codeApp(rules, "com.game"), work), "Aplikacija sa šifrom", 0);
        check(DailySchedule.codeApp(rules, "com.chat") == null, "Aplikacija bez šifre", 0);
        check(is(DailySchedule.codeSite(rules, "www.youtube.com"), work), "Sajt sa šifrom", 0);
        work.enabled = false;
        check(DailySchedule.codeApp(rules, "com.game") == null, "Isključen režim nema šifru", 0);
    }

    private static void dailyCode() {
        TimeZone zone = TimeZone.getTimeZone("Europe/Belgrade");
        Calendar c = Calendar.getInstance(zone);
        c.clear();
        c.set(2026, Calendar.OCTOBER, 8, 16, 59);
        check("20261007".equals(DailyCode.dayKey(c.getTimeInMillis(), zone)), "Pre 17h važi jučerašnja", 0);
        c.set(2026, Calendar.OCTOBER, 8, 17, 0);
        check("20261008".equals(DailyCode.dayKey(c.getTimeInMillis(), zone)), "U 17h nova", 0);
        c.set(2026, Calendar.OCTOBER, 9, 8, 0);
        check("20261008".equals(DailyCode.dayKey(c.getTimeInMillis(), zone)), "Ujutru važi jučerašnja", 0);
        c.set(2026, Calendar.JANUARY, 1, 3, 0);
        check("20251231".equals(DailyCode.dayKey(c.getTimeInMillis(), zone)), "Preko nove godine", 0);
        byte[] key = new byte[32];
        byte[] other = new byte[32];
        other[0] = 1;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int d = 1; d <= 28; d++) {
            String day = String.format("202602%02d", d);
            String code = DailyCode.code(key, day);
            check(code.matches("[0-9]{6}"), "Šest cifara", d);
            check(code.equals(DailyCode.code(key, day)), "Ista šifra za isti dan", d);
            check(!code.equals(DailyCode.code(other, day)), "Drugi ključ, druga šifra", d);
            seen.add(code);
        }
        check(seen.size() == 28, "Svaki dan nova šifra", 0);
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
