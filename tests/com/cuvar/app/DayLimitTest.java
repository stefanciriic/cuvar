package com.cuvar.app;

/** Pokretanje: javac -d .review/daylimit app/src/main/java/com/cuvar/app/DayLimit.java tests/com/cuvar/app/DayLimitTest.java
 *  java -cp .review/daylimit com.cuvar.app.DayLimitTest */
public final class DayLimitTest {
    private static int checks;

    public static void main(String[] args) {
        // Strožiji limit (ili uključivanje) važi odmah, blaži ili isključen tek od sutra.
        check(DayLimit.appliesNow(0, 180), "Uključivanje važi odmah");
        check(DayLimit.appliesNow(180, 120), "Smanjenje važi odmah");
        check(DayLimit.appliesNow(180, 180), "Ista vrednost");
        check(!DayLimit.appliesNow(180, 240), "Povećanje čeka sutra");
        check(!DayLimit.appliesNow(180, 0), "Isključivanje čeka sutra");

        check(DayLimit.effective(180, 240, "20261009", "20261008") == 180, "Danas važi stara vrednost");
        check(DayLimit.effective(180, 240, "20261009", "20261009") == 240, "Sutra važi nova vrednost");
        check(DayLimit.effective(180, 0, "20261009", "20261012") == 0, "Isključen posle nekoliko dana");
        check(DayLimit.effective(180, -1, null, "20261012") == 180, "Bez zakazane promene");

        long min = 60000L;
        check(!DayLimit.reached(0, 1000 * min), "Isključen limit nikad nije potrošen");
        check(!DayLimit.reached(180, 179 * min), "Pre limita");
        check(DayLimit.reached(180, 180 * min), "Tačno na limitu");
        check(!DayLimit.warn(180, 164 * min), "Rano za upozorenje");
        check(DayLimit.warn(180, 165 * min), "Upozorenje 15 min ranije");
        check(!DayLimit.warn(180, 180 * min), "Posle limita nema upozorenja");

        check("3 h".equals(DayLimit.label(180)), "Natpis 3 h");
        check("1 h 30 min".equals(DayLimit.label(90)), "Natpis 1 h 30 min");
        check("Isključen".equals(DayLimit.label(0)), "Natpis isključen");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
