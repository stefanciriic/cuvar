package com.cuvar.app.rules;

import java.util.ArrayList;
import java.util.List;

/** Pravilo jedne aplikacije: zaključavanje, dnevni limit, broj otvaranja i najduže u komadu (0 = bez). */
public final class AppRule {
    public static final AppRule NONE = new AppRule(false, 0, 0, 0);

    public final boolean lock;
    public final int limit;
    public final int opens;
    public final int session;

    public AppRule(boolean lock, int limit, int opens, int session) {
        this.lock = lock;
        this.limit = Math.max(0, limit);
        this.opens = Math.max(0, opens);
        this.session = Math.max(0, session);
    }

    /** Da li pravilo uopšte nešto ograničava (inače se ne čuva). */
    public boolean any() {
        return RuleMerge.anyAppRule(lock, limit, opens, session);
    }

    /** Strožije od dva pravila, deo po deo; null znači bez pravila. */
    public static AppRule stricter(AppRule a, AppRule b) {
        if (a == null) a = NONE;
        if (b == null) b = NONE;
        return new AppRule(a.lock || b.lock,
                RuleMerge.stricterLimit(a.limit, b.limit),
                RuleMerge.stricterLimit(a.opens, b.opens),
                RuleMerge.stricterLimit(a.session, b.session));
    }

    /** Popuštanja od pravila koje sada važi (now) do podešenog (wanted), rečima; prazno ako ih nema. */
    public static List<String> relaxations(AppRule now, AppRule wanted) {
        if (now == null) now = NONE;
        if (wanted == null) wanted = NONE;
        List<String> out = new ArrayList<>();
        if (now.lock && !wanted.lock) out.add("bez zaključavanja");
        if (now.limit != wanted.limit) {
            out.add(wanted.limit <= 0 ? "bez dnevnog limita" : "limit " + wanted.limit + " min umesto " + now.limit);
        }
        if (now.opens != wanted.opens) {
            out.add(wanted.opens <= 0 ? "bez ograničenja otvaranja" : "najviše " + wanted.opens + " otvaranja umesto " + now.opens);
        }
        if (now.session != wanted.session) {
            out.add(wanted.session <= 0 ? "bez ograničenja u komadu" : "najviše " + wanted.session + " min u komadu umesto " + now.session);
        }
        return out;
    }
}
