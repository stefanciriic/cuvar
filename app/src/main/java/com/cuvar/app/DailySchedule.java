package com.cuvar.app;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Period u izabranim danima u nedelji po vremenu telefona; kraj nije uključen. */
final class DailySchedule {
    /** Dani kao bitovi: 0 = ponedeljak … 6 = nedelja. */
    static final int ALL_DAYS = 0b1111111;
    static final int WORK_DAYS = 0b0011111;
    static final int WEEKEND = 0b1100000;
    static final String[] DAY_NAMES = {"pon", "uto", "sre", "čet", "pet", "sub", "ned"};

    /** Jedan vremenski režim sa sopstvenim periodom, aplikacijama i domenima. */
    static final class Rule {
        final String id;
        String name;
        boolean enabled;
        int start;
        int end;
        int days = ALL_DAYS;
        /** Van perioda aplikacije i sajtovi ovog režima otključavaju se samo dnevnom šifrom. */
        boolean code;
        final Set<String> apps = new LinkedHashSet<>();
        final Set<String> sites = new LinkedHashSet<>();

        Rule(String id, String name, boolean enabled, int start, int end) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.start = start;
            this.end = end;
        }

        /** Period koji prelazi ponoć pripada danu u kome je počeo. */
        boolean active(int minute, int day) {
            if (!enabled || !contains(start, end, minute)) return false;
            boolean afterMidnight = start > end && minute < end;
            return hasDay(afterMidnight ? (day + 6) % 7 : day);
        }

        boolean hasDay(int day) {
            return (days & (1 << day)) != 0;
        }

        String label() {
            return DailySchedule.label(start) + "–" + DailySchedule.label(end);
        }

        /** "radnim danima", "svakog dana" ili "pon, sre, pet". */
        String daysLabel() {
            if (days == ALL_DAYS) return "svakog dana";
            if (days == WORK_DAYS) return "radnim danima";
            if (days == WEEKEND) return "vikendom";
            StringBuilder sb = new StringBuilder();
            for (int d = 0; d < 7; d++) {
                if (!hasDay(d)) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(DAY_NAMES[d]);
            }
            return sb.length() == 0 ? "nijednog dana" : sb.toString();
        }
    }

    static boolean contains(int start, int end, int minute) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    static String label(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    /** Prvi uključen režim koji u datom minutu i danu (0 = ponedeljak) blokira aplikaciju, ili null. */
    static Rule blockingApp(List<Rule> rules, String pkg, int minute, int day) {
        for (Rule r : rules) if (r.active(minute, day) && r.apps.contains(pkg)) return r;
        return null;
    }

    /** Prvi uključen režim koji u datom minutu i danu blokira host (i poddomene), ili null. */
    static Rule blockingSite(List<Rule> rules, String host, int minute, int day) {
        for (Rule r : rules) if (r.active(minute, day) && matchDomain(host, r.sites) != null) return r;
        return null;
    }

    /** Uključen režim sa dnevnom šifrom koji sadrži aplikaciju, ili null. */
    static Rule codeApp(List<Rule> rules, String pkg) {
        for (Rule r : rules) if (r.enabled && r.code && r.apps.contains(pkg)) return r;
        return null;
    }

    /** Uključen režim sa dnevnom šifrom koji sadrži host (i poddomene), ili null. */
    static Rule codeSite(List<Rule> rules, String host) {
        for (Rule r : rules) if (r.enabled && r.code && matchDomain(host, r.sites) != null) return r;
        return null;
    }

    /** Najduži domen sa liste koji pokriva host (npr. m.youtube.com -> youtube.com) ili null. */
    static String matchDomain(String host, Collection<String> domains) {
        String best = null;
        for (String d : domains) {
            if ((host.equals(d) || host.endsWith("." + d)) && (best == null || d.length() > best.length())) best = d;
        }
        return best;
    }

    static Rule copy(Rule r) {
        return copy(r, r.id);
    }

    static Rule copy(Rule r, String id) {
        Rule c = new Rule(id, r.name, r.enabled, r.start, r.end);
        c.days = r.days;
        c.code = r.code;
        c.apps.addAll(r.apps);
        c.sites.addAll(r.sites);
        return c;
    }

    /** Cela nedelja je bitna: ponedeljak 00–01 nije deo ponedeljka 23–02. */
    static boolean coversWeek(Rule a, Rule b) {
        for (int day = 0; day < 7; day++) {
            for (int minute = 0; minute < 1440; minute++) {
                if (b.active(minute, day) && !a.active(minute, day)) return false;
            }
        }
        return true;
    }

    /** Da li a već sadrži sva ograničenja b, uključujući šifru van perioda. */
    private static boolean coversRule(Rule a, Rule b) {
        if (!b.enabled) return true;
        if (!a.enabled || (b.code && !a.code) || !a.apps.containsAll(b.apps)) return false;
        for (String site : b.sites) if (matchDomain(site, a.sites) == null) return false;
        return coversWeek(a, b);
    }

    /**
     * Do jutra važi unija starih i novih ograničenja. Kada se unija ne može predstaviti
     * jednim periodom, zadržavaju se oba segmenta sa istim id-em. Ne spajaju se odvojeno
     * dani, periodi i aplikacije jer bi to izgubilo njihovu međusobnu vezu.
     */
    static List<Rule> tighten(List<Rule> existing, List<Rule> desired) {
        List<Rule> out = new ArrayList<>();
        for (Rule r : existing) addSegment(out, copy(r));
        for (Rule r : desired) {
            for (Rule old : out) if (old.id.equals(r.id)) old.name = r.name;
            addSegment(out, copy(r));
        }
        return out;
    }

    private static void addSegment(List<Rule> out, Rule candidate) {
        for (Iterator<Rule> it = out.iterator(); it.hasNext();) {
            Rule old = it.next();
            if (!old.id.equals(candidate.id)) continue;
            if (coversRule(candidate, old)) it.remove();
            else if (coversRule(old, candidate)) return;
        }
        out.add(candidate);
    }
}
