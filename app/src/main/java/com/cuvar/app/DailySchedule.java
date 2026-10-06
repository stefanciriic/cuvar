package com.cuvar.app;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Svakodnevni period po lokalnom vremenu telefona; kraj nije uključen. */
final class DailySchedule {
    /** Jedan vremenski režim sa sopstvenim periodom, aplikacijama i domenima. */
    static final class Rule {
        final String id;
        String name;
        boolean enabled;
        int start;
        int end;
        final Set<String> apps = new LinkedHashSet<>();
        final Set<String> sites = new LinkedHashSet<>();

        Rule(String id, String name, boolean enabled, int start, int end) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.start = start;
            this.end = end;
        }

        boolean active(int minute) {
            return enabled && contains(start, end, minute);
        }

        String label() {
            return DailySchedule.label(start) + "–" + DailySchedule.label(end);
        }
    }

    static boolean contains(int start, int end, int minute) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    static String label(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    /** Prvi uključen režim koji u datom minutu blokira aplikaciju, ili null. */
    static Rule blockingApp(List<Rule> rules, String pkg, int minute) {
        for (Rule r : rules) if (r.active(minute) && r.apps.contains(pkg)) return r;
        return null;
    }

    /** Prvi uključen režim koji u datom minutu blokira host (i poddomene), ili null. */
    static Rule blockingSite(List<Rule> rules, String host, int minute) {
        for (Rule r : rules) if (r.active(minute) && matchDomain(host, r.sites) != null) return r;
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
}
