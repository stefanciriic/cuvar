package com.cuvar.app;

/** Najduže u komadu: posle limita pauza od 15 min; kratak izlazak ne prekida komad. */
public final class SessionLimitTest {
    private static int checks;
    private static final long MIN = 60000L;

    public static void main(String[] args) {
        long cap = 30 * MIN;
        SessionLimit.State s = new SessionLimit.State(0, 0, 0);
        long t = 100 * MIN;
        check(SessionLimit.add(s, cap, t, 10 * MIN) == 20 * MIN, "Posle 10 min ostaje 20");
        check(SessionLimit.add(s, cap, t + 5 * MIN, 5 * MIN) == 15 * MIN, "Izlazak od 5 min ne prekida komad");
        check(SessionLimit.add(s, cap, t + 30 * MIN, 5 * MIN) == 25 * MIN, "Izlazak duži od 15 min: novi komad");
        check(SessionLimit.add(s, cap, t + 31 * MIN, 25 * MIN) == 0, "Komad je potrošen: pauza");
        check(s.used == 0 && s.breakUntil == t + 46 * MIN, "Pauza traje 15 min");

        long seen = s.lastSeen;
        check(SessionLimit.add(s, cap, t + 40 * MIN, MIN) == 0, "Tokom pauze nema korišćenja");
        check(s.lastSeen == seen && s.used == 0, "Tokom pauze se stanje ne menja");

        check(SessionLimit.breakLeft(t + 46 * MIN, t + 40 * MIN) == 6 * MIN, "Ostatak pauze");
        check(SessionLimit.breakLeft(t + 46 * MIN, t + 46 * MIN) == 0, "Pauza je gotova");
        check(SessionLimit.breakLeft(t + 100 * MIN, t) == 0, "Pauza duža od 15 min (pomeren sat) se ne računa");

        check(SessionLimit.stale(false, 0, t, t + 16 * MIN), "Bez pravila i davno: briše se");
        check(!SessionLimit.stale(true, 0, t, t + 16 * MIN), "Sa pravilom ostaje");
        check(!SessionLimit.stale(false, t + MIN, t, t), "Tokom pauze ostaje");
        check(!SessionLimit.stale(false, 0, t, t + 10 * MIN), "Skoro korišćena ostaje");
        check(SessionLimit.stale(false, 0, 0, t), "Nikad korišćena: briše se");
        System.out.println("SessionLimitTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
