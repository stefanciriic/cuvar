package com.cuvar.app.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Popuštanje pravila (režimi, limiti, zaključavanje, zaštita, izuzeci, razdvajanje): traži se samo 06:00–10:00
 * i važi tek sutra u 06:00. Do tada podešavanje od pre zahteva ostaje sačuvano kao „hold“.
 */
public final class ModeHolds {
    private ModeHolds() { }

    public static final int WINDOW_START = 6 * 60;
    public static final int WINDOW_END = 10 * 60;
    /**
     * 30 h: zahtev u 06:00 (i na danu od 25 h) pada posle sutrašnjeg 06:00, a zahtev u 09:59 pre sutrašnjeg,
     * pa je until uvek sutrašnji dan za pravila.
     */
    public static final long WAIT_MS = 30L * 60 * 60 * 1000;

    /** Podešavanje od pre zahteva; važi dok je dan za pravila pre until ("yyyyMMdd"). */
    public static final class Hold {
        public final String until;
        public final EnforcedRules rules;

        public Hold(String until, EnforcedRules rules) {
            this.until = until;
            this.rules = rules;
        }
    }

    public static boolean inWindow(int minuteOfDay) {
        return minuteOfDay >= WINDOW_START && minuteOfDay < WINDOW_END;
    }

    /** Dan za pravila od kog izlazak zatražen u now prestaje da se drži (sutra). */
    public static String until(long now, TimeZone zone) {
        return UsageCalendar.rulesDay(now + WAIT_MS, zone, DailyCode.NIGHT_END_HOUR);
    }

    /** Da li izmena režima old -> now (null = brisanje) slabi režim koji nešto blokira. */
    public static boolean loosens(DailySchedule.Rule old, DailySchedule.Rule now) {
        if (old == null || !old.enabled || (old.apps.isEmpty() && old.sites.isEmpty())) return false;
        return now == null || !DailySchedule.covers(now, old);
    }

    /**
     * Deo režima koji je trajao u 05:59 i još traje bez prekida, samo za dan u kome je period počeo; inače null.
     * Pre 06:00 (otkazivanje, prvo pokretanje) dovoljno je da upravo traje. Period koji je počeo posle 06:00
     * se ne zadržava, i kad roll() stigne kasno (telefon je spavao).
     */
    public static DailySchedule.Rule carry(DailySchedule.Rule r, int minute, int weekday) {
        if (!r.active(minute, weekday)) return null;
        if (minute >= WINDOW_START) {
            if (!r.active(WINDOW_START - 1, weekday)) return null;
            for (int m = WINDOW_START; m < minute; m++) {
                if (!DailySchedule.contains(r.start, r.end, m)) return null;
            }
        }
        boolean afterMidnight = r.start > r.end && minute < r.end;
        DailySchedule.Rule c = DailySchedule.copy(r);
        c.days = 1 << (afterMidnight ? (weekday + 6) % 7 : weekday);
        return c;
    }

    /**
     * Zadržani deo (carry) koji je prošao: jedan dan, sada ne traje i ne pokriva ga ni podešeni režim sa istim
     * id-em (pravi jednodnevni režim) ni živ snimak. Takav ne čeka ništa, pa se ne prikazuje kao zakazana promena.
     */
    public static boolean expiredCarry(DailySchedule.Rule s, List<DailySchedule.Rule> desired, List<Hold> live,
                                       int minute, int weekday) {
        if (Integer.bitCount(s.days) != 1 || s.active(minute, weekday)) return false;
        for (DailySchedule.Rule r : desired) if (r.id.equals(s.id) && DailySchedule.covers(r, s)) return false;
        for (Hold h : live) {
            for (DailySchedule.Rule r : h.rules.schedules) {
                if (r.id.equals(s.id) && DailySchedule.covers(r, s)) return false;
            }
        }
        return true;
    }

    /**
     * Pravila od jutra: podešeno, strožije od zadržanih snimaka, uz delove starih režima koji upravo traju
     * (režim 23–08 ne pušta stavku u 06:00 dok još traje).
     */
    public static EnforcedRules morning(EnforcedRules desired, List<DailySchedule.Rule> oldSchedules,
                                        List<Hold> holds, String day, int minute, int weekday) {
        EnforcedRules kept = null;
        for (Hold h : live(holds, day)) kept = kept == null ? h.rules : EnforcedRules.tighten(kept, h.rules, false);
        EnforcedRules out = kept == null ? desired : EnforcedRules.tighten(kept, desired, false);
        List<DailySchedule.Rule> carried = new ArrayList<>();
        for (DailySchedule.Rule r : oldSchedules) {
            DailySchedule.Rule c = carry(r, minute, weekday);
            if (c != null) carried.add(c);
        }
        if (carried.isEmpty()) return out;
        return new EnforcedRules(out.apps, out.sites, DailySchedule.tighten(carried, out.schedules), out.night,
                out.protect, out.full, out.nightFree, out.dayFree, out.links);
    }

    /** Snimak sa još jednim izuzetkom od ukupnog limita (izuzetak koji je već odmah važeći, DayLimit.freeNow). */
    public static EnforcedRules withDayFree(EnforcedRules r, String key) {
        java.util.Set<String> free = new java.util.HashSet<>(r.dayFree);
        free.add(key);
        return new EnforcedRules(r.apps, r.sites, r.schedules, r.night, r.protect, r.full, r.nightFree, free, r.links);
    }

    /** Zadržani snimci koji još važe na dan day. */
    public static List<Hold> live(List<Hold> holds, String day) {
        List<Hold> out = new ArrayList<>();
        for (Hold h : holds) if (day.compareTo(h.until) < 0) out.add(h);
        return out;
    }
}
