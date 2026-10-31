package com.cuvar.app;

/** Cena otključavanja po broju otključavanja u danu. */
public final class UnlockCostTest {
    private static int checks;

    public static void main(String[] args) {
        UnlockCost a = UnlockCost.next(0);
        check(a.side == 5 && a.timeMs == 40_000L && a.answers == 1, "Prvo otključavanje");
        UnlockCost b = UnlockCost.next(1);
        check(b.side == 5 && b.timeMs < a.timeMs && b.answers == 2, "Drugo je teže");
        UnlockCost c = UnlockCost.next(2);
        check(c.side == 6 && c.answers == 3, "Treće je najteže");
        check(UnlockCost.next(UnlockCost.MAX) == null, "Posle MAX nema otključavanja");
        check(UnlockCost.next(-1).side == 5, "Negativan broj");
        check(UnlockCost.emergency(7) != null && UnlockCost.emergency(7).side == 6, "Hitno uvek ima cenu");

        NumberGame g = new NumberGame(new java.util.Random(4), 0, c.side, c.timeMs);
        check(g.cells.length == 36, "Mreža 6x6");
        for (int i = 1; i < 36; i++) check(g.tap(i, 10) == NumberGame.Tap.OK, "Redom " + i);
        check(g.tap(36, 10) == NumberGame.Tap.DONE, "Pobeda na 6x6");
        check(new NumberGame(new java.util.Random(4), 0, 6, 45_000L).tap(1, 45_000L) == NumberGame.Tap.LATE, "Isteklo vreme 6x6");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
