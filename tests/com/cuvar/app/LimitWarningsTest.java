package com.cuvar.app;

/** Upozorenje 5 i 1 minut pre isteka limita, jednom po pragu u danu. */
public final class LimitWarningsTest {
    private static int checks;
    private static final long MIN = 60000L;

    public static void main(String[] args) {
        LimitWarnings w = new LimitWarnings();
        check(!w.shouldWarn("app:x", "20261030", 6 * MIN), "Rano za upozorenje");
        check(w.shouldWarn("app:x", "20261030", 5 * MIN), "Upozorenje 5 min pre");
        check(!w.shouldWarn("app:x", "20261030", 4 * MIN), "Isto upozorenje samo jednom");
        check(w.shouldWarn("app:x", "20261030", MIN), "Upozorenje 1 min pre");
        check(!w.shouldWarn("app:x", "20261030", 30000L), "Poslednje samo jednom");
        check(!w.shouldWarn("app:x", "20261030", 0), "Istekao limit se ne najavljuje");
        check(w.shouldWarn("site:y.com", "20261030", 3 * MIN), "Druga stavka ima svoje upozorenje");
        check(w.shouldWarn("app:x", "20261031", 5 * MIN), "Novi dan, nova upozorenja");

        LimitWarnings late = new LimitWarnings();
        check(late.shouldWarn("app:z", "20261030", 50000L), "Kasno otvaranje: odmah poslednje upozorenje");
        check(!late.shouldWarn("app:z", "20261030", 4 * MIN), "Posle poslednjeg nema ranijeg");
        System.out.println("LimitWarningsTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
