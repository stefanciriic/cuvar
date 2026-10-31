package com.cuvar.app.rules;

import java.util.HashSet;
import java.util.Set;

/**
 * Upozorenje 5 i 1 minut pre isteka limita aplikacije ili sajta, jednom po pragu u danu,
 * da se stigne završiti bez naglog prekida.
 */
public final class LimitWarnings {
    public static final long FIRST_MS = 5 * 60000L;
    public static final long LAST_MS = 60000L;

    private final Set<String> warned = new HashSet<>();
    private String day;

    /** Da li sada treba upozoriti za ovu stavku; left je koliko je ostalo do limita. */
    public boolean shouldWarn(String key, String today, long left) {
        if (left <= 0 || left > FIRST_MS) return false;
        if (!today.equals(day)) {
            warned.clear();
            day = today;
        }
        boolean last = left <= LAST_MS;
        if (!warned.add(key + (last ? "|1" : "|5"))) return false;
        warned.add(key + "|5"); // posle poslednjeg upozorenja nema više ranijeg
        return true;
    }
}
