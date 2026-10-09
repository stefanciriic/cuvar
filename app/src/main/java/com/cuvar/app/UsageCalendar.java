package com.cuvar.app;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Čiste kalendarske operacije za pouzdano vreme koje Store prosledi. */
final class UsageCalendar {
    private UsageCalendar() { }

    private static String dayKey(Calendar c) {
        return String.format(Locale.US, "%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String rulesDay(long time, TimeZone zone, int changeHour) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(time);
        // Lokalnih 06:00 ostaje 06:00 i kada dan traje 23 ili 25 sati.
        if (c.get(Calendar.HOUR_OF_DAY) < changeHour) c.add(Calendar.DAY_OF_MONTH, -1);
        return dayKey(c);
    }

    /** Raspodela intervala [from, to) po lokalnim datumima, uključujući promene sata. */
    static Map<String, Long> split(long from, long to, TimeZone zone) {
        Map<String, Long> days = new LinkedHashMap<>();
        Calendar c = Calendar.getInstance(zone);
        while (from < to) {
            c.setTimeInMillis(from);
            String day = dayKey(c);
            c.add(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long end = Math.min(to, c.getTimeInMillis());
            if (end <= from) throw new IllegalStateException("Ponoć mora da sledi početku intervala");
            days.put(day, days.getOrDefault(day, 0L) + end - from);
            from = end;
        }
        return days;
    }
}
