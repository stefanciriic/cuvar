package com.cuvar.app.rules;

/**
 * Zavesa pre blokade: čim Android javi da se otvara blokirana aplikacija, ekran se odmah prekrije praznom
 * pozadinom, pa tek onda stigne pravi ekran blokade. Ovde je samo odluka kada se zavesa skida.
 */
public final class QuickCover {
    /** Najduže koliko zavesa sme da stoji (ne hvata dodir, ali sakriva ekran). */
    public static final long MAX_MS = 1500L;
    /** Koliko Android sme da kasni sa prozorom: dok traje, druga aplikacija na vrhu ne skida zavesu. */
    public static final long LAG_MS = 300L;

    private QuickCover() {
    }

    /**
     * Da li se zavesa skida posle provere (kad ekran blokade nije prikazan).
     * seen: aplikacija koju je provera videla napred (null = ne zna se), cover: zbog koje je zavesa stavljena.
     */
    public static boolean lift(boolean screenUsable, String seen, String cover, long sinceMs) {
        if (!screenUsable || sinceMs >= MAX_MS || sinceMs < 0) return true;
        if (seen == null) return false;
        // Aplikacija je viđena i nije blokirana; druga aplikacija posle kašnjenja znači da je korisnik otišao.
        return seen.equals(cover) || sinceMs >= LAG_MS;
    }
}
