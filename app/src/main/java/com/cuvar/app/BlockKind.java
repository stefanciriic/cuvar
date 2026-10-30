package com.cuvar.app;

import java.util.List;
import java.util.function.Predicate;

/** Vrste blokade i izbor one koja se prikazuje. GuardService.KIND_* su isti brojevi. */
final class BlockKind {
    static final int LOCK = 1;       // aplikacija zaključana (otključava se dnevnom šifrom)
    static final int TIME = 2;       // istekao dnevni limit aplikacije
    static final int SITE = 3;       // sajt uvek blokiran
    static final int SITE_TIME = 4;  // istekao dnevni limit sajta
    static final int SCHEDULE = 5;   // vremenski režim
    static final int CODE = 6;       // van perioda režima, otključava se dnevnom šifrom
    static final int DAY = 7;        // potrošen ukupni dnevni limit, ništa se ne otključava do ponoći
    static final int NIGHT = 8;      // noćna blokada od 22:00, ništa se ne otključava do 06:00
    static final int OPENS = 9;      // potrošena otvaranja aplikacije za danas
    static final int BROWSER = 10;   // pregledač u kome Čuvar ne vidi adresu, dok postoje pravila za sajtove
    static final int PAUSE = 11;     // kratka pauza pre otvaranja aplikacije sa pravilom, posle nje se nastavlja
    static final int BREAK = 12;     // obavezna pauza posle najdužeg korišćenja u komadu
    static final int CLONER = 13;    // aplikacija za kloniranje (Parallel Space i sl.), dok postoje pravila za aplikacije
    static final int INAPP = 14;     // pregledač unutar aplikacije (Instagram, Facebook) u kome se ne vidi adresa
    static final int ADDRESS = 15;   // podržan pregledač, ali adresa još nije potvrđena
    static final int FOCUS = 16;     // ručni režim: dozvoljene su samo izabrane aplikacije/sajtovi

    private BlockKind() {
    }

    /** Blokade koje se ne otključavaju ni šifrom: režim, noć, fokus, potrošen limit, otvaranja, pauza u komadu. */
    static boolean hard(int kind) {
        return kind == SCHEDULE || kind == DAY || kind == NIGHT || kind == TIME || kind == SITE_TIME
                || kind == OPENS || kind == BREAK || kind == FOCUS;
    }

    /** Jedno pravilo koje sada blokira aplikaciju ili sajt. */
    static final class Block {
        final String key;   // "app:paket" ili "site:domen"
        final int kind;
        final DailySchedule.Rule rule;
        final boolean code; // otključava se dnevnom šifrom

        Block(String key, int kind, DailySchedule.Rule rule, boolean code) {
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
    static Block pick(List<Block> blocks, Predicate<String> unlocked) {
        for (Block b : blocks) {
            if (hard(b.kind) || !unlocked.test(b.key)) return b;
        }
        return null;
    }
}
