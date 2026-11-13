package com.cuvar.app.rules;

import java.util.Collection;
import java.util.Map;

/**
 * Ukupni dnevni limit: zajedničko vreme za sve aplikacije i sajtove sa sopstvenim pravilom (ne samo
 * u režimu). Vreme u ostalim aplikacijama (mape, pozivi, poruke...) ga ne troši. Kad se pređe, te
 * aplikacije i sajtovi su zaključani do ponoći bez ikakvog otključavanja. Smanjenje važi odmah, povećanje je moguće jednom
 * dnevno i samo za deo limita (što je limit veći, to manji deo), a isključivanje važi tek od sutra.
 */
public final class DayLimit {
    /** Najveći ukupni dnevni limit u minutima (4 h); ni povećanjem ne sme preko. */
    public static final int MAX = 240;
    /** Ponuđene vrednosti u minutima. */
    public static final int[] CHOICES = {60, 90, 120, 180, 240};
    /** Predložena vrednost. */
    public static final int SUGGESTED = 180;
    /** Koliko pre isteka stiže upozorenje. */
    public static final long WARN_MS = 15 * 60000L;

    private DayLimit() {
    }

    /**
     * Koliko procenata limita sme da se doda jednom dnevno: do 1 h 30 %, na 2 h 20 %, od 4 h 10 %,
     * a između toga ravnomerno.
     */
    public static double raisePercent(int limitMin) {
        double h = limitMin / 60.0;
        if (h <= 1) return 30;
        if (h <= 2) return 30 - 10 * (h - 1);
        if (h <= 4) return 20 - 5 * (h - 2);
        return 10;
    }

    /** Najviše minuta koje danas sme da se doda limitu (0 ako limit nije uključen ili je na MAX). */
    public static int maxRaise(int limitMin) {
        if (limitMin <= 0 || limitMin >= MAX) return 0;
        int add = Math.max(1, (int) Math.round(limitMin * raisePercent(limitMin) / 100.0));
        return Math.min(add, MAX - limitMin);
    }

    /** Limit ograničen na MAX (0 = isključen ostaje 0). */
    public static int clamp(int min) {
        return min > MAX ? MAX : min;
    }

    /** Granica za zbir limita aplikacija i sajtova: ukupni limit, a bez njega MAX. */
    public static int budget(int dayLimitMin) {
        return dayLimitMin > 0 ? Math.min(dayLimitMin, MAX) : MAX;
    }

    /**
     * Da li sme da se sačuva limit stavke: bez limita, smanjenje ili isto uvek prolaze (i kad je zbir
     * već preko), a povećanje samo ako zbir ostane u granici.
     */
    public static boolean allowedItemLimit(int wanted, int before, int othersSum, int budget) {
        return wanted <= 0 || wanted <= before || othersSum + wanted <= budget;
    }

    /**
     * Najmanji limit (> 0) roditeljskih sajtova domena sa liste, ili 0. Poddomen troši roditeljsko vreme,
     * pa limit poddomena do te vrednosti ne povećava zbir.
     */
    public static int parentLimit(String domain, Map<String, Integer> siteLimits) {
        int min = 0;
        for (Map.Entry<String, Integer> e : siteLimits.entrySet()) {
            int l = e.getValue() == null ? 0 : e.getValue();
            if (l > 0 && domain.endsWith("." + e.getKey()) && (min == 0 || l < min)) min = l;
        }
        return min;
    }

    /** Koliko minuta još staje u granicu (za poruku). */
    public static int roomFor(int othersSum, int budget) {
        return Math.max(0, budget - othersSum);
    }

    /** Da li nova vrednost važi odmah: samo ako je strožija od trenutne. */
    public static boolean appliesNow(int current, int wanted) {
        return wanted > 0 && (current <= 0 || wanted <= current);
    }

    /** Limit koji važi za dan today ("yyyyMMdd"): zakazana vrednost važi od dana from. */
    public static int effective(int current, int next, String from, String today) {
        return from != null && next >= 0 && today.compareTo(from) >= 0 ? next : current;
    }

    /** Da li je limit potrošen. */
    public static boolean reached(int limitMin, long usedMs) {
        return limitMin > 0 && usedMs >= limitMin * 60000L;
    }

    /** Da li treba upozoriti da je ostalo malo vremena. */
    public static boolean warn(int limitMin, long usedMs) {
        return limitMin > 0 && !reached(limitMin, usedMs) && usedMs >= limitMin * 60000L - WARN_MS;
    }

    /**
     * Vreme pod pravilima za dan izmeren dok se ono nije beležilo zasebno: zbir aplikacija sa pravilom
     * i sajtova sa pravilom (ključ "site:domen"). Poddomen se ne sabira uz roditeljski domen sa pravilom,
     * jer roditelj već sadrži njegovo vreme.
     */
    public static long guardedEstimate(Map<String, Long> day, Collection<String> apps, Collection<String> sites) {
        long total = 0;
        for (String pkg : apps) total += day.getOrDefault(pkg, 0L);
        for (String site : sites) {
            boolean nested = false;
            for (String parent : sites) nested |= site.endsWith("." + parent);
            if (!nested) total += day.getOrDefault(ItemKey.site(site), 0L);
        }
        return total;
    }

    /**
     * Da li ukupni limit troši i zaključava aplikaciju: ima sopstveno pravilo za ceo dan (zaključaj, limit,
     * otvaranja, u komadu, ili povezan sajt sa pravilom) a nije van ukupnog limita, ili je pregledač/kloner
     * koji se ne prati. Njega izuzetak ne oslobađa: u njemu se ne vidi sajt ni kopija, pa bi skinuo limit sa svega.
     * Aplikacija samo u režimu nije pod ukupnim limitom.
     */
    public static boolean coversApp(boolean ownRule, boolean unwatched, boolean free) {
        return (ownRule && !free) || unwatched;
    }

    /**
     * Da li izuzimanje aplikacije iz ukupnog limita važi odmah: jedna aplikacija dnevno (usedDay/usedPkg je
     * poslednje izuzimanje odmah). Istog dana ista aplikacija može ponovo, ostale čekaju 06:00.
     */
    public static boolean freeNow(String usedDay, String usedPkg, String today, String pkg) {
        return today == null || !today.equals(usedDay) || (pkg != null && pkg.equals(usedPkg));
    }

    /** "3 h", "1 h 30 min" ili "Isključen". */
    public static String label(int min) {
        if (min <= 0) return "Isključen";
        if (min % 60 == 0) return (min / 60) + " h";
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }
}
