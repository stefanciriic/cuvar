package com.cuvar.app;

import java.util.Collection;
import java.util.Map;

/**
 * Ukupni dnevni limit: zajedničko vreme za sve aplikacije i sajtove sa pravilima. Vreme u ostalim
 * aplikacijama (mape, pozivi, poruke...) ga ne troši. Kad se pređe, aplikacije i sajtovi sa pravilima
 * su zaključani do ponoći bez ikakvog otključavanja. Smanjenje važi odmah, povećanje je moguće jednom
 * dnevno i samo za deo limita (što je limit veći, to manji deo), a isključivanje važi tek od sutra.
 */
final class DayLimit {
    /** Ponuđene vrednosti u minutima. */
    static final int[] CHOICES = {60, 90, 120, 180, 240, 300};
    /** Predložena vrednost. */
    static final int SUGGESTED = 180;
    /** Koliko pre isteka stiže upozorenje. */
    static final long WARN_MS = 15 * 60000L;

    private DayLimit() {
    }

    /**
     * Koliko procenata limita sme da se doda jednom dnevno: do 1 h 30 %, na 2 h 20 %, od 4 h 10 %,
     * a između toga ravnomerno.
     */
    static double raisePercent(int limitMin) {
        double h = limitMin / 60.0;
        if (h <= 1) return 30;
        if (h <= 2) return 30 - 10 * (h - 1);
        if (h <= 4) return 20 - 5 * (h - 2);
        return 10;
    }

    /** Najviše minuta koje danas sme da se doda limitu (0 ako limit nije uključen). */
    static int maxRaise(int limitMin) {
        if (limitMin <= 0) return 0;
        return Math.max(1, (int) Math.round(limitMin * raisePercent(limitMin) / 100.0));
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

    /**
     * Vreme pod pravilima za dan izmeren dok se ono nije beležilo zasebno: zbir aplikacija sa pravilom
     * i sajtova sa pravilom (ključ "site:domen"). Poddomen se ne sabira uz roditeljski domen sa pravilom,
     * jer roditelj već sadrži njegovo vreme.
     */
    static long guardedEstimate(Map<String, Long> day, Collection<String> apps, Collection<String> sites) {
        long total = 0;
        for (String pkg : apps) total += day.getOrDefault(pkg, 0L);
        for (String site : sites) {
            boolean nested = false;
            for (String parent : sites) nested |= site.endsWith("." + parent);
            if (!nested) total += day.getOrDefault("site:" + site, 0L);
        }
        return total;
    }

    /** "3 h", "1 h 30 min" ili "Isključen". */
    static String label(int min) {
        if (min <= 0) return "Isključen";
        if (min % 60 == 0) return (min / 60) + " h";
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }
}
