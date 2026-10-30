package com.cuvar.app;

/** Pouzdan sat: pomeranje sata telefona ne menja vreme režima i šifre. */
public final class TrustedClockTest {
    private static int checks;
    private static final long DAY = 86400000L;
    private static final long WALL = TrustedClock.SANE_WALL + 300 * DAY; // neki dan krajem 2026.

    public static void main(String[] args) {
        long el = 1000000L;
        long off = WALL - el;
        // Sat telefona je tačan i nije pomeren.
        check(TrustedClock.onRead(el, WALL, off, 0, el, () -> false) == TrustedClock.KEEP, "Ništa se ne menja");
        check(TrustedClock.onRead(el + 61000, WALL + 61000, off, 0, el, () -> false) == TrustedClock.SAVE,
                "Posle minuta se stanje čuva");

        // Ručno pomeren sat (bez vremena sa mreže): pouzdano vreme ostaje, samo se pamti pomeranje.
        long moved = WALL + 3 * 3600000L;
        check(TrustedClock.onRead(el, moved, off, 0, el, () -> false) == TrustedClock.SAVE,
                "Ručno pomeranje se odmah pamti, pomak ostaje");
        check(TrustedClock.onRead(el, moved, off, 3 * 3600000L, el, () -> false) == TrustedClock.KEEP,
                "Već zapamćeno pomeranje");

        // Vreme sa mreže: sat telefona je tačan i prati se.
        check(TrustedClock.onRead(el, moved, off, 0, el, () -> true) == TrustedClock.RESET, "Vreme sa mreže se prati");
        boolean[] asked = {false};
        TrustedClock.onRead(el, WALL, off, 0, el, () -> asked[0] = true);
        check(!asked[0], "Bez pomeranja se podešavanje ne čita");

        // Telefon upaljen pre nego što je dobio tačno vreme (sat u 1970.).
        check(TrustedClock.onRead(el, WALL, 0, 0, el, () -> false) == TrustedClock.RESET, "Sat je kasnije podešen");
        check(TrustedClock.onRead(el, 5000, 0, 5000 - el, el, () -> false) == TrustedClock.KEEP, "Sat još nije podešen");

        // Posle restarta: ručno pomeranje se oduzima, a vreme ne ide unazad.
        long skew = 2 * 3600000L;
        check(TrustedClock.offsetAfterBoot(WALL + skew, 100, 0, skew, false) == WALL - 100,
                "Zapamćeno pomeranje se oduzima");
        check(TrustedClock.offsetAfterBoot(WALL + skew, 100, 0, skew, true) == WALL + skew - 100,
                "Uz vreme sa mreže važi sat telefona");
        check(TrustedClock.offsetAfterBoot(WALL - DAY, 100, WALL, 0, false) == WALL - 100,
                "Sat vraćen za dan pa restart: vreme ne ide unazad");

        check(TrustedClock.sameBoot(true, 7, 7, 0, 0), "Isti broj paljenja");
        check(!TrustedClock.sameBoot(true, 8, 7, 0, 0), "Drugi broj paljenja");
        check(!TrustedClock.sameBoot(false, 7, 7, 0, 0), "Ništa nije sačuvano");
        check(TrustedClock.sameBoot(true, -1, -2, 500, 400), "Bez broja: vreme od paljenja raste");
        check(!TrustedClock.sameBoot(true, -1, -2, 300, 400), "Bez broja: vreme od paljenja je manje, novo paljenje");
        System.out.println("TrustedClockTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
