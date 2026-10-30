package com.cuvar.app;

/**
 * Spajanje pravila koje važi danas i novog podešavanja: uvek ostaje strožije od ta dva.
 * Tako pooštravanje važi odmah, a popuštanje tek od sledećeg jutra u 06:00.
 */
final class RuleMerge {
    /** Sajt nema pravilo. */
    static final int NO_SITE = -1;

    private RuleMerge() {
    }

    /** Manji od dva limita (minuta, otvaranja...); 0 ili manje znači "bez limita". */
    static int stricterLimit(int a, int b) {
        if (a <= 0) return Math.max(0, b);
        if (b <= 0) return a;
        return Math.min(a, b);
    }

    /**
     * Strožije od dva pravila za sajt: NO_SITE (-1) nema pravila, 0 je uvek blokiran,
     * više od 0 je dnevni limit u minutima.
     */
    static int stricterSite(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        if (a == 0 || b == 0) return 0;
        return Math.min(a, b);
    }

    /** Da li pravilo aplikacije uopšte nešto ograničava (inače se ne čuva). */
    /** Izuzeci (npr. bez noćne blokade): novi izuzetak čeka sutra, a skidanje izuzetka važi odmah. */
    static java.util.Set<String> stricterExempt(java.util.Set<String> now, java.util.Set<String> wanted) {
        java.util.Set<String> out = new java.util.HashSet<>(now);
        out.retainAll(wanted);
        return out;
    }

    static boolean anyAppRule(boolean lock, int limit, int opens, int session) {
        return lock || limit > 0 || opens > 0 || session > 0;
    }
}
