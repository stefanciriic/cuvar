package com.cuvar.app;

import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

public final class UsageCalendarTest {
    private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Belgrade");
    private static int checks;

    public static void main(String[] args) {
        long midnight = at(2026, 10, 10, 0, 0);
        Map<String, Long> split = UsageCalendar.split(midnight - 1500, midnight + 2500, ZONE);
        check(split.size() == 2 && split.get("20261009") == 1500L && split.get("20261010") == 2500L, "Deli ponoć u milisekundi");
        split = UsageCalendar.split(midnight - 1500, midnight, ZONE);
        check(split.size() == 1 && split.get("20261009") == 1500L, "Kraj intervala nije uključen");
        split = UsageCalendar.split(midnight, midnight + 2500, ZONE);
        check(split.size() == 1 && split.get("20261010") == 2500L, "Početak u ponoć");
        check(UsageCalendar.split(midnight, midnight, ZONE).isEmpty(), "Prazan interval");
        check(UsageCalendar.split(midnight + 1, midnight, ZONE).isEmpty(), "Obrnut interval");

        checkDayLength(2026, 3, 29, 23);
        checkDayLength(2026, 10, 25, 25);
        checkDayLength(2026, 10, 9, 24);
        checkBoundary(2026, 3, 29, "20260328", "20260329");
        checkBoundary(2026, 10, 25, "20261024", "20261025");
        checkBoundary(2026, 1, 1, "20251231", "20260101");

        long from = at(2026, 10, 24, 23, 30), to = at(2026, 10, 26, 0, 30);
        split = UsageCalendar.split(from, to, ZONE);
        check(split.size() == 3, "Više datuma preko zimskog računanja vremena");
        check(split.get("20261024") == 30 * 60000L, "Prvi delimičan dan");
        check(split.get("20261025") == 25 * 3600000L, "Ceo dan sa vraćanjem sata");
        check(split.get("20261026") == 30 * 60000L, "Poslednji delimičan dan");
        long total = 0;
        for (long ms : split.values()) total += ms;
        check(total == to - from, "Nema izgubljenog ili dupliranog vremena");
        System.out.println("UsageCalendarTest: prošlo " + checks + " provera.");
    }

    private static void checkDayLength(int year, int month, int day, int hours) {
        long from = at(year, month, day, 0, 0), to = at(year, month, day + 1, 0, 0);
        Map<String, Long> split = UsageCalendar.split(from, to, ZONE);
        check(split.size() == 1 && split.values().iterator().next() == hours * 3600000L, "Trajanje dana " + month + "/" + day);
    }

    private static void checkBoundary(int year, int month, int day, String before, String after) {
        for (int minute = 0; minute < 1440; minute++) {
            String expected = minute < 6 * 60 ? before : after;
            check(expected.equals(UsageCalendar.rulesDay(at(year, month, day, minute / 60, minute % 60), ZONE, 6)),
                    "Pravila menjaju dan u lokalnih 06:00: " + month + "/" + day + " minut " + minute);
        }
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(ZONE);
        c.clear();
        c.set(year, month - 1, day, hour, minute);
        return c.getTimeInMillis();
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
