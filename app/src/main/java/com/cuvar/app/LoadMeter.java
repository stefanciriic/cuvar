package com.cuvar.app;

/**
 * Koliko radi servis Čuvara: broj provera ekrana i vreme koje one traju, od pokretanja servisa.
 * Prikazuje se u Podešavanja → Opterećenje telefona. Ne pamti ništa o aplikacijama ni sajtovima.
 */
final class LoadMeter {
    static final LoadMeter GUARD = new LoadMeter();

    private long startedMs;
    private long checks;
    private long events;
    private long totalNs;
    private long maxNs;

    /** Servis je (ponovo) pokrenut: brojanje kreće od nule. nowMs je vreme od paljenja telefona. */
    synchronized void start(long nowMs) {
        startedMs = nowMs;
        checks = 0;
        events = 0;
        totalNs = 0;
        maxNs = 0;
    }

    synchronized void event() {
        events++;
    }

    /** Jedna provera ekrana je trajala ns nanosekundi. */
    synchronized void check(long ns) {
        if (ns < 0) ns = 0;
        checks++;
        totalNs += ns;
        if (ns > maxNs) maxNs = ns;
    }

    synchronized Snapshot snapshot(long nowMs) {
        return new Snapshot(Math.max(0L, nowMs - startedMs), checks, events, totalNs, maxNs);
    }

    /** Stanje u jednom trenutku, sa izračunatim prosecima. */
    static final class Snapshot {
        final long upMs, checks, events, totalNs, maxNs;

        Snapshot(long upMs, long checks, long events, long totalNs, long maxNs) {
            this.upMs = upMs;
            this.checks = checks;
            this.events = events;
            this.totalNs = totalNs;
            this.maxNs = maxNs;
        }

        double perMinute() {
            return upMs <= 0 ? 0 : checks * 60000.0 / upMs;
        }

        double avgMs() {
            return checks == 0 ? 0 : totalNs / 1e6 / checks;
        }

        double maxMs() {
            return maxNs / 1e6;
        }

        /** Deo vremena (u procentima) koji su provere zauzele. */
        double busyPercent() {
            return upMs <= 0 ? 0 : totalNs / 1e6 * 100.0 / upMs;
        }
    }

    /** Deo vremena u procentima, npr. CPU vreme procesa prema vremenu otkako proces radi. */
    static double percent(long partMs, long wholeMs) {
        return wholeMs <= 0 ? 0 : partMs * 100.0 / wholeMs;
    }
}
