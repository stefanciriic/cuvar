package com.cuvar.app.rules;

/**
 * Cena otključavanja raste sa brojem otključavanja tog dana: igrica je teža, pitanja traže više
 * tačnih zaredom, a posle MAX otključavanja nema više do sutra. Bez Android klasa (tests/UnlockCostTest).
 */
public final class UnlockCost {
    /** Koliko otključavanja šifrom ima u jednom danu. */
    public static final int MAX = 3;

    public final int side;
    public final long timeMs;
    public final int answers;

    private UnlockCost(int side, long timeMs, int answers) {
        this.side = side;
        this.timeMs = timeMs;
        this.answers = answers;
    }

    /** Cena sledećeg otključavanja kad ih je danas već bilo done; null kad ih više nema. */
    public static UnlockCost next(int done) {
        if (done < 0) done = 0;
        if (done >= MAX) return null;
        if (done == 0) return new UnlockCost(5, 35_000L, 1);
        if (done == 1) return new UnlockCost(6, 50_000L, 2);
        return new UnlockCost(6, 42_000L, 3);
    }

    /** Hitno otključavanje ima svoje ograničenje, pa i posle MAX dobija najtežu igricu. */
    public static UnlockCost emergency(int done) {
        UnlockCost c = next(done);
        return c != null ? c : next(MAX - 1);
    }
}
