package com.cuvar.app.rules;

import java.util.List;
import java.util.function.Predicate;

/** Vrste blokade i izbor one koja se prikazuje. GuardService.KIND_* su isti brojevi. */
public final class BlockKind {
    public static final int LOCK = 1;       // aplikacija zaključana (otključava se dnevnom šifrom)
    public static final int TIME = 2;       // istekao dnevni limit aplikacije
    public static final int SITE = 3;       // sajt uvek blokiran
    public static final int SITE_TIME = 4;  // istekao dnevni limit sajta
    public static final int SCHEDULE = 5;   // vremenski režim
    public static final int CODE = 6;       // van perioda režima, otključava se dnevnom šifrom
    public static final int DAY = 7;        // potrošen ukupni dnevni limit, ništa se ne otključava do ponoći
    public static final int NIGHT = 8;      // noćna blokada od 22:00, ništa se ne otključava do 06:00
    public static final int OPENS = 9;      // potrošena otvaranja aplikacije za danas
    public static final int BROWSER = 10;   // pregledač u kome Čuvar ne vidi adresu, dok postoje pravila za sajtove
    public static final int PAUSE = 11;     // kratka pauza pre otvaranja aplikacije sa pravilom, posle nje se nastavlja
    public static final int BREAK = 12;     // obavezna pauza posle najdužeg korišćenja u komadu
    public static final int CLONER = 13;    // aplikacija za kloniranje (Parallel Space i sl.), dok postoje pravila za aplikacije
    public static final int INAPP = 14;     // pregledač unutar aplikacije (Instagram, Facebook) u kome se ne vidi adresa
    public static final int ADDRESS = 15;   // podržan pregledač, ali adresa još nije potvrđena
    public static final int FOCUS = 16;     // ručni režim: dozvoljene su samo izabrane aplikacije/sajtovi
    /**
     * Pregledač (nepoznat, ugrađen u aplikaciju ili bez pročitane adrese) dok je neki sajt blokiran bez otključavanja
     * (aktivan režim ili potrošen limit sajta). Šifra bi tu otvorila i taj sajt, pa se ne otključava.
     */
    public static final int UNSEEN_SITE = 17;

    private BlockKind() {
    }

    /** Blokade koje se ne otključavaju ni šifrom: režim, noć, fokus, potrošen limit, otvaranja, pauza u komadu. */
    public static boolean hard(int kind) {
        return kind == SCHEDULE || kind == DAY || kind == NIGHT || kind == TIME || kind == SITE_TIME
                || kind == OPENS || kind == BREAK || kind == FOCUS || kind == UNSEEN_SITE;
    }

    /**
     * Blokada pregledača u kome Čuvar ne vidi sajt: obično se otključava šifrom, ali ne dok je neki sajt
     * blokiran bez otključavanja, jer bi se taj sajt onda otvorio u ovom pregledaču.
     */
    public static int unseenSite(int softKind, boolean hardSiteBlock) {
        return hardSiteBlock ? UNSEEN_SITE : softKind;
    }

    /** Jedno pravilo koje sada blokira aplikaciju ili sajt. */
    public static final class Block {
        public final String key;   // "app:paket" ili "site:domen"
        public final int kind;
        public final DailySchedule.Rule rule;
        public final boolean code; // otključava se dnevnom šifrom

        public Block(String key, int kind, DailySchedule.Rule rule, boolean code) {
            this.key = key;
            this.kind = kind;
            this.rule = rule;
            this.code = code;
        }
    }

    /**
     * Prva blokada koja sada važi, po redu od najstrožeg. Tvrda važi uvek; ostale ne važe dok je
     * njihova stavka otključana. Otključavanje jedne stavke ne otvara ostale. Null kad ništa ne blokira.
     */
    public static Block pick(List<Block> blocks, Predicate<String> unlocked) {
        for (Block b : blocks) {
            if (hard(b.kind) || !unlocked.test(b.key)) return b;
        }
        return null;
    }
}
