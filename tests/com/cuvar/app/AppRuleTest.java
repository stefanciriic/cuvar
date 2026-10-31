package com.cuvar.app;

import com.cuvar.app.rules.AppRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pooštravanje odmah, popuštanje tek sutra: spajanje pravila aplikacije i opis popuštanja. */
public final class AppRuleTest {
    private static int checks;

    public static void main(String[] args) {
        AppRule none = AppRule.NONE;
        check(!none.any(), "Prazno pravilo ništa ne ograničava");
        check(new AppRule(true, 0, 0, 0).any() && new AppRule(false, 0, 3, 0).any() && new AppRule(false, 0, 0, 20).any(),
                "Svaki deo je pravilo");
        AppRule neg = new AppRule(false, -5, -1, -9);
        check(neg.limit == 0 && neg.opens == 0 && neg.session == 0 && !neg.any(), "Negativno je bez limita");

        AppRule now = new AppRule(false, 30, 5, 0);
        AppRule wanted = new AppRule(true, 60, 3, 15);
        AppRule m = AppRule.stricter(now, wanted);
        check(m.lock, "Zaključavanje važi odmah");
        check(m.limit == 30, "Duži limit čeka sutra");
        check(m.opens == 3, "Manje otvaranja važi odmah");
        check(m.session == 15, "Novo ograničenje u komadu važi odmah");
        check(same(AppRule.stricter(wanted, now), m), "Redosled nije bitan");
        check(same(AppRule.stricter(null, wanted), wanted) && same(AppRule.stricter(now, null), now), "Null je bez pravila");
        check(same(AppRule.stricter(m, m), m), "Isto pravilo ostaje isto");

        check(AppRule.relaxations(m, m).isEmpty(), "Bez promene nema popuštanja");
        List<String> r = AppRule.relaxations(new AppRule(true, 30, 3, 15), new AppRule(false, 60, 0, 10));
        check(r.equals(Arrays.asList("bez zaključavanja", "limit 60 min umesto 30", "bez ograničenja otvaranja",
                "najviše 10 min u komadu umesto 15")), "Opis popuštanja: " + r);
        check(AppRule.relaxations(new AppRule(false, 30, 0, 0), AppRule.NONE).equals(Collections.singletonList("bez dnevnog limita")),
                "Skidanje limita");
        check(AppRule.relaxations(null, null).isEmpty(), "Null bez promene");
        check(AppRule.relaxations(new AppRule(false, 0, 0, 20), new AppRule(false, 0, 0, 0))
                .equals(Collections.singletonList("bez ograničenja u komadu")), "Skidanje ograničenja u komadu");
        check(AppRule.relaxations(new AppRule(false, 0, 2, 0), new AppRule(false, 0, 4, 0))
                .equals(Collections.singletonList("najviše 4 otvaranja umesto 2")), "Više otvaranja");

        // Bilo koja dva pravila: spojeno nije blaže ni od jednog, i od spojenog do bilo kog nema pooštravanja.
        java.util.Random rnd = new java.util.Random(5);
        for (int i = 0; i < 5000; i++) {
            AppRule a = random(rnd), b = random(rnd), s = AppRule.stricter(a, b);
            check(atLeastAsStrict(s, a) && atLeastAsStrict(s, b), "Spojeno nije blaže");
        }
        System.out.println("AppRuleTest: " + checks + " provera prošlo.");
    }

    private static AppRule random(java.util.Random r) {
        return new AppRule(r.nextBoolean(), r.nextInt(4) * 15, r.nextInt(4), r.nextInt(3) * 10);
    }

    private static boolean atLeastAsStrict(AppRule s, AppRule x) {
        return (!x.lock || s.lock) && limitOk(s.limit, x.limit) && limitOk(s.opens, x.opens) && limitOk(s.session, x.session);
    }

    private static boolean limitOk(int s, int x) {
        return x <= 0 || (s > 0 && s <= x);
    }

    private static boolean same(AppRule a, AppRule b) {
        return a.lock == b.lock && a.limit == b.limit && a.opens == b.opens && a.session == b.session;
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
    }
}
