package com.cuvar.app;

/**
 * Čija je muzika koja svira: aplikacija koja je bila napred kad je muzika krenula.
 * Ekran blokade zaustavlja samo muziku zaključane aplikacije, ne Spotify i slične u pozadini.
 */
final class MusicOwner {
    /** Muzika je već svirala pre prve provere, pa se ne zna ko ju je pokrenuo: smatra se tuđom. */
    static final String UNKNOWN = "";

    private MusicOwner() {
    }

    /**
     * Novi vlasnik posle provere. Kad muzika krene, vlasnik je aplikacija napred; dok svira, vlasnik ostaje;
     * kad stane, nema vlasnika. first: ovo je prva provera, pa se ne zna da li je muzika tek krenula.
     */
    static String next(boolean first, boolean wasPlaying, boolean playing, String owner, String front) {
        if (!playing) return null;
        if (first) return UNKNOWN;
        if (!wasPlaying) return front == null ? UNKNOWN : front;
        return owner == null ? UNKNOWN : owner;
    }

    /** Da li muzika pripada nekoj drugoj aplikaciji, pa je ekran blokade za {@code front} ne sme prekinuti. */
    static boolean belongsToOther(boolean playing, String owner, String front) {
        return playing && front != null && !front.equals(owner);
    }
}
