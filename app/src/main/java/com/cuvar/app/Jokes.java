package com.cuvar.app;

import java.util.Random;

/** Šaljive poruke na ekranu za blokadu i pri pokušaju otključavanja. */
final class Jokes {

    private static final Random RANDOM = new Random();

    /** Istekao dnevni limit aplikacije ili sajta. */
    static final String[] TIME_UP = {
            "Telefon kaže: dosta je bilo. A telefon je pametan.",
            "Ovo nije kraj sveta. Samo kraj skrolovanja za danas.",
            "Algoritam će preživeti bez tebe do sutra. Obećavamo.",
            "Tvoj palac je tražio slobodan dan. Odobreno.",
            "Napolju postoji nešto što se zove sunce. Ima odlične recenzije.",
            "Čestitamo! Otključao si dostignuće: dnevni limit.",
            "Sutra je novi dan i novi limit. Danas je samo… danas.",
            "Ako ti je dosadno, to je odlično. Tako su nastale sve dobre ideje.",
    };

    /** Sajt koji je uvek blokiran. */
    static final String[] SITE = {
            "Ništa lično, samo si ga ti stavio na listu.",
            "Ovde nema ničega za tebe. Bukvalno, blokirali smo ga.",
            "Znaš ti dobro zašto je ovaj sajt na listi.",
            "Ovaj sajt je trenutno na odmoru. Od tebe.",
    };

    /** Aplikacija zaključana PIN-om. */
    static final String[] LOCK = {
            "Kuc, kuc. Ko je? PIN.",
            "Ova aplikacija je pod ključem. Ti imaš ključ. Valjda.",
            "Samo da proverimo da si to stvarno ti.",
    };

    /** Aktivan vremenski režim. */
    static final String[] SCHEDULE = {
            "Prošli ti je rekao da je ovo loša ideja u ovo doba.",
            "Režim radi tačno ono što si mu rekao. Ne ljuti se na njega.",
            "Ovo je trenutak kad spuštaš telefon i postaješ legenda.",
            "Telefon ima radno vreme. Sad je pauza.",
    };

    /** Pitanje pre unosa PIN-a na ekranu za blokadu. */
    static final String[] ARE_YOU_SURE = {
            "Malopre si bio odlučan da ovo blokiraš. Šta se promenilo?",
            "Ovo je trenutak kad ćeš sutra reći: zašto sam otključao?",
            "Tvoje buduće ja te gleda. Razočarano.",
            "Samo da proverim: ovo je hitno ili ti je samo dosadno?",
            "Možeš da otključaš. Ali možeš i da budeš heroj.",
            "Pet minuta, kažeš? Svi znamo kako se to završava.",
    };

    static final String[] YES = {
            "Da, znam šta radim",
            "Da, hitno je",
            "Otključaj, molim te",
    };

    static final String[] NO = {
            "Ne, u pravu si",
            "Dobro, pobedio si",
            "Idem da radim nešto korisno",
    };

    private Jokes() {
    }

    static String pick(String[] options) {
        return options[RANDOM.nextInt(options.length)];
    }
}
