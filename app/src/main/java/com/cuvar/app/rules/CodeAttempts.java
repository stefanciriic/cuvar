package com.cuvar.app.rules;

/**
 * Pokušaji unosa dnevne šifre: posle 5 pogrešnih ide 30 s čekanja. Vreme je "vreme od paljenja"
 * (SystemClock.elapsedRealtime), pa pomeranje sata ne skraćuje čekanje.
 */
public final class CodeAttempts {
    public static final int MAX_FAILS = 5;
    public static final long WAIT_MS = 30000L;

    public int fails;
    /** Do kada se čeka (vreme od paljenja), 0 kad se ne čeka. */
    public long blockedUntil;

    /** Čekanje je isteklo: briše se. Vraća true kad se stanje promenilo i treba ga sačuvati. */
    public boolean expire(long now) {
        if (blockedUntil > 0L && now >= blockedUntil) {
            blockedUntil = 0L;
            return true;
        }
        return false;
    }

    /** Koliko je još ostalo čekanja (0 ako se ne čeka). */
    public long waitLeft(long now) {
        return Math.max(0L, blockedUntil - now);
    }

    /** Sekunde za poruku: zaokruženo naviše, nikad 0 dok se čeka. */
    public static long waitSeconds(long left) {
        return left / 1000 + 1;
    }

    public void success() {
        fails = 0;
    }

    /** Pogrešna šifra. Vraća true kad je ovo bila peta greška i počinje čekanje. */
    public boolean fail(long now) {
        fails++;
        if (fails >= MAX_FAILS) {
            fails = 0;
            blockedUntil = now + WAIT_MS;
            return true;
        }
        return false;
    }

    /**
     * Čekanje sačuvano pre restarta procesa ili telefona. U istom paljenju telefona važi sačuvano vreme
     * od paljenja; posle restarta telefona ostatak se računa po pouzdanom vremenu.
     */
    public static long restore(long savedWall, long savedEl, boolean sameBoot, long wallNow, long elNow) {
        if (savedWall <= wallNow) return 0L;
        return sameBoot ? Math.max(0L, savedEl) : elNow + (savedWall - wallNow);
    }
}
