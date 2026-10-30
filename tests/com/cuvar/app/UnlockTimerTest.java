package com.cuvar.app;

/** Otključavanje šifrom: 5 minuta korišćenja, pa sat vremena bez novog otključavanja. */
public final class UnlockTimerTest {
    private static int checks;
    private static final long MIN = 60000L;

    public static void main(String[] args) {
        check(UnlockTimer.useLeft(-1) == 0, "Bez otključavanja nema vremena");
        check(UnlockTimer.useLeft(0) == 5 * MIN, "Odmah posle otključavanja ima 5 min");
        check(UnlockTimer.useLeft(4 * MIN) == MIN, "Posle 4 min ostaje 1 min");
        check(UnlockTimer.useLeft(5 * MIN) == 0, "Posle 5 min je isteklo");

        check(UnlockTimer.busyLeft(-1) == 0, "Bez otključavanja nema pauze");
        check(UnlockTimer.busyLeft(0) == 65 * MIN, "Korišćenje i pauza traju 65 min");
        check(UnlockTimer.busyLeft(30 * MIN) == 35 * MIN, "Usred pauze");

        // Druga stavka čeka odmah, ista tek kad njenih 5 minuta istekne.
        check(UnlockTimer.cooldownFor(2 * MIN, true) == 0, "Ista stavka se koristi bez pauze");
        check(UnlockTimer.cooldownFor(2 * MIN, false) == 63 * MIN, "Druga stavka čeka");
        check(UnlockTimer.cooldownFor(10 * MIN, true) == 55 * MIN, "Ista stavka posle 5 min čeka");
        check(UnlockTimer.cooldownFor(-1, false) == 0, "Bez otključavanja nema čekanja");

        check(!UnlockTimer.finished(64 * MIN), "Pauza još traje");
        check(UnlockTimer.finished(65 * MIN), "Pauza je gotova");

        // Sat vraćen unazad pa restart: ne dobija se novih 5 minuta.
        check(UnlockTimer.sinceAfterRestart(1000, 5000) == 5 * MIN, "Sat vraćen unazad: kreće pauza");
        check(UnlockTimer.sinceAfterRestart(10 * MIN, 0) == 10 * MIN, "Normalan restart");
        System.out.println("UnlockTimerTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
