package com.cuvar.app;

import com.cuvar.app.rules.QuickCover;

/** Zavesa pre blokade se uvek skida: kad je aplikacija viđena, kad je korisnik otišao ili posle najviše 1,5 s. */
public final class QuickCoverTest {
    private static int checks;

    public static void main(String[] args) {
        String app = "com.example.blokirana";
        check(QuickCover.lift(true, app, app, 0), "Aplikacija viđena i nije blokirana: zavesa odmah ide");
        check(!QuickCover.lift(true, "com.launcher", app, 50), "Android još pokazuje stari prozor: zavesa ostaje");
        check(QuickCover.lift(true, "com.launcher", app, QuickCover.LAG_MS), "Posle kašnjenja druga aplikacija: zavesa ide");
        check(!QuickCover.lift(true, null, app, 500), "Ne zna se šta je napred: zavesa ostaje kratko");
        check(QuickCover.lift(true, null, app, QuickCover.MAX_MS), "Najduže 1,5 s");
        check(QuickCover.lift(false, null, app, 0), "Ekran ugašen ili zaključan: zavesa ide");
        check(QuickCover.lift(true, null, app, -5), "Pogrešno vreme: zavesa ide");
        System.out.println("QuickCoverTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
