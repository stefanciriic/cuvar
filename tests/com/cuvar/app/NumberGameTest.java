package com.cuvar.app;

import java.util.Arrays;
import java.util.Random;

/** Igrica „Brojevi redom“. */
public final class NumberGameTest {
    private static int checks;

    public static void main(String[] args) {
        NumberGame g = new NumberGame(new Random(1), 0);
        int[] sorted = g.cells.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < NumberGame.SIZE; i++) check(sorted[i] == i + 1, "Svaki broj tačno jednom");

        for (int i = 1; i < NumberGame.SIZE; i++) check(g.tap(i, 1000) == NumberGame.Tap.OK, "Redom " + i);
        check(g.tap(3, 1000) == NumberGame.Tap.IGNORED, "Već tapnut broj se ne broji");
        check(g.tap(NumberGame.SIZE, 2000) == NumberGame.Tap.DONE, "Pobeda");
        check(g.over() && g.tap(1, 2000) == NumberGame.Tap.IGNORED, "Posle kraja ništa");

        NumberGame w = new NumberGame(new Random(2), 0);
        check(w.tap(1, 10) == NumberGame.Tap.OK, "Prvi");
        check(w.tap(5, 20) == NumberGame.Tap.WRONG, "Preskočen broj je greška");
        check(w.over(), "Greška završava partiju");

        NumberGame t = new NumberGame(new Random(3), 100);
        check(t.leftMs(100) == NumberGame.TIME_MS, "Puno vreme");
        check(t.tap(1, 100 + NumberGame.TIME_MS) == NumberGame.Tap.LATE, "Isteklo vreme");
        check(t.over(), "Kraj posle isteka");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
