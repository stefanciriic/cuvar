package com.cuvar.app.rules;

/**
 * "Najduže u komadu": posle toliko minuta korišćenja aplikacije ide obavezna pauza od 15 minuta.
 * Izlazak kraći od pauze ne prekida komad, inače bi se ograničenje zaobišlo kratkim izlaskom.
 */
public final class SessionLimit {
    /** Koliko traje obavezna pauza posle najdužeg dozvoljenog korišćenja u komadu. */
    public static final long BREAK_MS = 15 * 60000L;

    /** Stanje jedne aplikacije, po pouzdanom vremenu. */
    public static final class State {
        public long used;        // ms u trenutnom komadu
        public long lastSeen;    // kada je aplikacija poslednji put korišćena
        public long breakUntil;  // do kada traje pauza, 0 ako je nema

        public State(long used, long lastSeen, long breakUntil) {
            this.used = used;
            this.lastSeen = lastSeen;
            this.breakUntil = breakUntil;
        }
    }

    private SessionLimit() {
    }

    /**
     * Dodaje ms korišćenja. Vraća koliko je ostalo do pauze, ili 0 kad pauza upravo počinje ili već traje
     * (tada se stanje ne menja).
     */
    public static long add(State s, long capMs, long now, long ms) {
        if (s.breakUntil > now) return 0;
        if (now - s.lastSeen > BREAK_MS) s.used = 0L; // dovoljno dug izlazak: novi komad
        long used = s.used + ms;
        s.lastSeen = now;
        if (used >= capMs) {
            s.used = 0L;
            s.breakUntil = now + BREAK_MS;
            return 0;
        }
        s.used = used;
        return capMs - used;
    }

    /** Koliko je ostalo pauze. Pauza duža od 15 minuta znači da je sat pomeren, pa se ne računa. */
    public static long breakLeft(long breakUntil, long now) {
        long left = breakUntil - now;
        return left > 0 && left <= BREAK_MS ? left : 0;
    }

    /** Da li zapis više ne treba: nema pravila, nema pauze i komad je davno završen. */
    public static boolean stale(boolean hasRule, long breakUntil, long lastSeen, long now) {
        boolean onBreak = breakUntil > now;
        return !hasRule && !onBreak && (lastSeen <= 0L || now - lastSeen > BREAK_MS);
    }
}
