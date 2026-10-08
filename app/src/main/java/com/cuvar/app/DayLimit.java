package com.cuvar.app;

/**
 * Ukupni dnevni limit na telefonu. Kad se pređe, aplikacije i sajtovi sa pravilima su zaključani
 * do ponoći bez ikakvog otključavanja. Limit se danas može samo smanjiti (ili tek uključiti);
 * povećanje i isključivanje važe tek od sutra.
 */
final class DayLimit {
    /** Ponuđene vrednosti u minutima; 0 = isključen. */
    static final int[] CHOICES = {0, 60, 90, 120, 180, 240, 300};
    /** Predložena vrednost. */
    static final int SUGGESTED = 180;
    /** Koliko pre isteka stiže upozorenje. */
    static final long WARN_MS = 15 * 60000L;

    private DayLimit() {
    }

    /** Da li nova vrednost važi odmah: samo ako je strožija od trenutne. */
    static boolean appliesNow(int current, int wanted) {
        return wanted > 0 && (current <= 0 || wanted <= current);
    }

    /** Limit koji važi za dan today ("yyyyMMdd"): zakazana vrednost važi od dana from. */
    static int effective(int current, int next, String from, String today) {
        return from != null && next >= 0 && today.compareTo(from) >= 0 ? next : current;
    }

    /** Da li je limit potrošen. */
    static boolean reached(int limitMin, long usedMs) {
        return limitMin > 0 && usedMs >= limitMin * 60000L;
    }

    /** Da li treba upozoriti da je ostalo malo vremena. */
    static boolean warn(int limitMin, long usedMs) {
        return limitMin > 0 && !reached(limitMin, usedMs) && usedMs >= limitMin * 60000L - WARN_MS;
    }

    /** "3 h", "1 h 30 min" ili "Isključen". */
    static String label(int min) {
        if (min <= 0) return "Isključen";
        if (min % 60 == 0) return (min / 60) + " h";
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }
}
