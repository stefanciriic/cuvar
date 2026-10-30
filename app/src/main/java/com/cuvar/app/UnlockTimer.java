package com.cuvar.app;

/**
 * Računica otključavanja dnevnom šifrom: 5 minuta korišćenja, pa sat vremena bez novog otključavanja.
 * "since" je koliko je prošlo od otključavanja, ili negativno kad otključavanja nema.
 */
final class UnlockTimer {
    /** Koliko se dugo sme koristiti posle otključavanja šifrom. */
    static final long USE_MS = 5 * 60000L;
    /** Koliko posle toga nema nikakvog otključavanja. */
    static final long COOLDOWN_MS = 60 * 60000L;

    private UnlockTimer() {
    }

    /** Koliko je još ostalo od 5 minuta korišćenja (0 ako nije otključano ili je isteklo). */
    static long useLeft(long since) {
        return since >= 0 && since < USE_MS ? USE_MS - since : 0;
    }

    /** Da li su i korišćenje i pauza posle njega završeni, pa zapis može da se obriše. */
    static boolean finished(long since) {
        return since >= USE_MS + COOLDOWN_MS;
    }

    /** Koliko još traju korišćenje i pauza zajedno (0 ako otključavanja nema). */
    static long busyLeft(long since) {
        return since >= 0 ? Math.max(0L, USE_MS + COOLDOWN_MS - since) : 0;
    }

    /**
     * Pauza koju ovo otključavanje nameće stavci koja se upravo otvara. Druga stavka čeka odmah,
     * a ista stavka tek kad njenih 5 minuta istekne.
     */
    static long cooldownFor(long since, boolean sameItem) {
        if (since < 0 || (sameItem && since < USE_MS)) return 0;
        return busyLeft(since);
    }

    /**
     * Koliko je prošlo po pouzdanom vremenu posle restarta telefona. Ako je sat vraćen unazad,
     * kreće cela pauza, bez novih 5 minuta korišćenja.
     */
    static long sinceAfterRestart(long now, long unlockedAt) {
        long since = now - unlockedAt;
        return since < 0 ? USE_MS : since;
    }
}
