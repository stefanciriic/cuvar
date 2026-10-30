package com.cuvar.app;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Niz dana bez otključavanja i nedeljni zbir otključavanja. Dani su ključevi "yyyyMMdd";
 * {@code since} je dan od kada Čuvar broji otključavanja (pre toga se ne zna, pa se ne računa).
 */
final class UnlockStreak {
    private static final DateTimeFormatter KEY = DateTimeFormatter.BASIC_ISO_DATE;

    private UnlockStreak() { }

    /** Koliko je dana zaredom bez otključavanja, zaključno sa danas (danas se računa ako do sada nije bilo). */
    static int current(Map<String, Integer> counts, String today, String since) {
        LocalDate d = date(today);
        LocalDate first = date(since);
        if (d == null || first == null) return 0;
        int n = 0;
        while (!d.isBefore(first) && count(counts, d) == 0) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    /** Najduži niz dana bez otključavanja od početka brojanja. */
    static int best(Map<String, Integer> counts, String today, String since) {
        LocalDate d = date(since);
        LocalDate last = date(today);
        if (d == null || last == null) return 0;
        int best = 0;
        int run = 0;
        while (!d.isAfter(last)) {
            run = count(counts, d) == 0 ? run + 1 : 0;
            best = Math.max(best, run);
            d = d.plusDays(1);
        }
        return best;
    }

    /**
     * Otključavanja u 7 dana koji se završavaju {@code weeksBack} nedelja pre danas (0 = poslednjih 7 dana
     * sa danas), ili -1 ako je brojanje počelo posle prvog dana te nedelje.
     */
    static int week(Map<String, Integer> counts, String today, String since, int weeksBack) {
        LocalDate end = date(today);
        LocalDate first = date(since);
        if (end == null || first == null) return -1;
        end = end.minusDays(7L * weeksBack);
        LocalDate start = end.minusDays(6);
        if (start.isBefore(first)) return -1;
        int sum = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) sum += count(counts, d);
        return sum;
    }

    /** Dana bez otključavanja u poslednjih 7 dana sa danas (samo dani od početka brojanja). */
    static int cleanDaysThisWeek(Map<String, Integer> counts, String today, String since) {
        LocalDate end = date(today);
        LocalDate first = date(since);
        if (end == null || first == null) return 0;
        int n = 0;
        for (LocalDate d = end.minusDays(6); !d.isAfter(end); d = d.plusDays(1)) {
            if (!d.isBefore(first) && count(counts, d) == 0) n++;
        }
        return n;
    }

    /** Koliko od poslednjih 7 dana spada u brojanje (1 do 7). */
    static int countedThisWeek(String today, String since) {
        LocalDate end = date(today);
        LocalDate first = date(since);
        if (end == null || first == null) return 7;
        long days = java.time.temporal.ChronoUnit.DAYS.between(first, end) + 1;
        return (int) Math.max(1, Math.min(7, days));
    }

    static String key(LocalDate d) {
        return d.format(KEY);
    }

    private static int count(Map<String, Integer> counts, LocalDate d) {
        Integer v = counts.get(key(d));
        return v == null ? 0 : v;
    }

    private static LocalDate date(String key) {
        try {
            return key == null ? null : LocalDate.parse(key, KEY);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
