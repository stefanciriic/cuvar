package com.cuvar.app;

/**
 * Cena otključavanja raste sa brojem otključavanja tog dana: igrica je teža, pitanja traže više
 * tačnih zaredom, a posle MAX otključavanja nema više do sutra. Bez Android klasa (tests/UnlockCostTest).
 */
final class UnlockCost {
    /** Koliko otključavanja šifrom ima u jednom danu. */
    static final int MAX = 3;

    final int side;
    final long timeMs;
    final int answers;

    private UnlockCost(int side, long timeMs, int answers) {
        this.side = side;
        this.timeMs = timeMs;
        this.answers = answers;
    }

    /** Cena sledećeg otključavanja kad ih je danas već bilo done; null kad ih više nema. */
    static UnlockCost next(int done) {
        if (done < 0) done = 0;
        if (done >= MAX) return null;
        if (done == 0) return new UnlockCost(5, 40_000L, 1);
        if (done == 1) return new UnlockCost(5, 32_000L, 2);
        return new UnlockCost(6, 45_000L, 3);
    }

    /** Hitno otključavanje ima svoje ograničenje, pa i posle MAX dobija najtežu igricu. */
    static UnlockCost emergency(int done) {
        UnlockCost c = next(done);
        return c != null ? c : next(MAX - 1);
    }
}
