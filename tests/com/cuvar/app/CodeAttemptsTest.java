package com.cuvar.app;

/** Pokušaji dnevne šifre: 5 grešaka donosi 30 s čekanja, i posle restarta. */
public final class CodeAttemptsTest {
    private static int checks;

    public static void main(String[] args) {
        CodeAttempts a = new CodeAttempts();
        for (int i = 1; i <= 4; i++) check(!a.fail(1000), "Greška " + i + " još ne blokira");
        check(a.waitLeft(1000) == 0, "Posle 4 greške nema čekanja");
        check(a.fail(1000), "Peta greška blokira");
        check(a.fails == 0, "Brojač grešaka kreće ispočetka");
        check(a.waitLeft(1000) == 30000L, "Čeka se 30 s");
        check(a.waitLeft(21000) == 10000L, "Posle 20 s ostaje 10 s");
        check(!a.expire(30999), "Čekanje još traje");
        check(a.expire(31000), "Čekanje je isteklo");
        check(a.waitLeft(31000) == 0 && a.blockedUntil == 0, "Posle isteka nema čekanja");
        check(!a.expire(40000), "Isteklo čekanje se ne briše dvaput");

        a.fail(0);
        a.fail(0);
        a.success();
        check(a.fails == 0, "Tačna šifra briše greške");

        check(CodeAttempts.waitSeconds(30000L) == 31, "Natpis se zaokružuje naviše");
        check(CodeAttempts.waitSeconds(1) == 1, "Nikad 0 s dok se čeka");

        // Posle restarta: isteklo, isto paljenje telefona, novo paljenje.
        check(CodeAttempts.restore(5000, 900, true, 6000, 950) == 0, "Isteklo pre restarta");
        check(CodeAttempts.restore(9000, 900, true, 6000, 50) == 900, "Isto paljenje: sačuvano vreme od paljenja");
        check(CodeAttempts.restore(9000, 900, false, 6000, 50) == 3050, "Novo paljenje: ostatak po pouzdanom vremenu");
        System.out.println("CodeAttemptsTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
