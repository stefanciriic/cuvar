package com.cuvar.app;

/** Merenje opterećenja: broj provera, prosek, najduža i deo vremena. */
public final class LoadMeterTest {
    private static int checks;

    public static void main(String[] args) {
        LoadMeter m = new LoadMeter();
        m.start(1000);
        LoadMeter.Snapshot s = m.snapshot(1000);
        check(s.checks == 0 && s.avgMs() == 0 && s.perMinute() == 0 && s.busyPercent() == 0, "Na početku je sve nula");

        m.check(2_000_000L);
        m.check(4_000_000L);
        m.check(-5);
        m.event();
        m.event();
        s = m.snapshot(1000 + 60000);
        check(s.checks == 3, "Tri provere");
        check(s.events == 2, "Dva događaja");
        check(Math.abs(s.avgMs() - 2.0) < 1e-9, "Prosek 2 ms");
        check(Math.abs(s.maxMs() - 4.0) < 1e-9, "Najduža 4 ms");
        check(Math.abs(s.perMinute() - 3.0) < 1e-9, "Tri u minutu");
        check(Math.abs(s.busyPercent() - 0.01) < 1e-9, "6 ms od minuta je 0,01 %");

        m.start(5000);
        check(m.snapshot(5000).checks == 0, "Ponovno pokretanje kreće od nule");
        check(m.snapshot(4000).upMs == 0, "Vreme nikad negativno");

        check(LoadMeter.percent(30, 3000) == 1.0, "1 %");
        check(LoadMeter.percent(5, 0) == 0, "Bez vremena 0 %");
        System.out.println("LoadMeterTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
