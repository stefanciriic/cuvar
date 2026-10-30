package com.cuvar.app;

import java.util.function.BooleanSupplier;

/**
 * Pravila "pouzdanog sata". Pouzdano vreme = vreme od paljenja telefona + pomak (offset).
 * U jednom paljenju pomeranje sata telefona ga ne menja; ručno pomeranje se pamti i oduzima i
 * posle restarta. Kad telefon uzima vreme sa mreže, sat telefona je tačan i prati se.
 */
final class TrustedClock {
    /** Sat telefona u nekom trenutku posle 1.1.2026; manje od toga znači da sat još nije podešen. */
    static final long SANE_WALL = 1767225600000L;

    /** Ništa se ne menja. */
    static final int KEEP = 0;
    /** Pomak ostaje, ali stanje treba sačuvati (prošao je minut ili je sat ručno pomeren). */
    static final int SAVE = 1;
    /** Pomak se ponovo uzima iz sata telefona (wall - el) i čuva. */
    static final int RESET = 2;

    private TrustedClock() {
    }

    /** Da li je sačuvan pomak iz istog paljenja telefona. Bez broja paljenja poredi se vreme od paljenja. */
    static boolean sameBoot(boolean hasSaved, int boot, int savedBoot, long el, long savedEl) {
        return hasSaved && (boot != -1 ? savedBoot == boot : el >= savedEl);
    }

    /**
     * Pomak posle novog paljenja telefona: sat telefona umanjen za ranije zapamćeno ručno pomeranje
     * (osim kad je vreme sa mreže), ali nikad pre poslednjeg zapamćenog pouzdanog vremena.
     */
    static long offsetAfterBoot(long wall, long el, long lastTrusted, long savedSkew, boolean autoTime) {
        long base = autoTime ? wall : wall - savedSkew;
        return Math.max(base, lastTrusted) - el;
    }

    /** Šta uraditi pri čitanju pouzdanog vremena. autoTime se pita samo kad zatreba. */
    static int onRead(long el, long wall, long offset, long savedSkew, long savedAtEl, BooleanSupplier autoTime) {
        long skew = wall - (el + offset);
        if (el + offset < SANE_WALL && wall >= SANE_WALL) return RESET; // upaljen pre nego što je dobio tačno vreme
        if (Math.abs(skew) > 60000L && autoTime.getAsBoolean()) return RESET; // vreme sa mreže je tačno
        if (el - savedAtEl > 60000L || Math.abs(skew - savedSkew) > 30000L) return SAVE; // ručno pomeren: odmah zapamti
        return KEEP;
    }
}
