package com.cuvar.app;

import java.util.HashMap;
import java.util.Map;

/** Niz dana bez otključavanja i nedeljni zbir. */
public final class UnlockStreakTest {
    private static int checks;

    public static void main(String[] args) {
        Map<String, Integer> c = new HashMap<>();
        check(UnlockStreak.current(c, "20261030", "20261030") == 1, "Prvi dan bez otključavanja");
        check(UnlockStreak.current(c, "20261030", "20261025") == 6, "Šest dana od početka");
        c.put("20261027", 2);
        check(UnlockStreak.current(c, "20261030", "20261020") == 3, "Niz posle otključavanja");
        check(UnlockStreak.best(c, "20261030", "20261020") == 7, "Najbolji niz pre otključavanja");
        c.put("20261030", 1);
        check(UnlockStreak.current(c, "20261030", "20261020") == 0, "Danas otključano");
        check(UnlockStreak.current(c, "20261101", "20261020") == 2, "Preko kraja meseca");

        check(UnlockStreak.week(c, "20261030", "20261020", 0) == 3, "Ova nedelja");
        check(UnlockStreak.week(c, "20261030", "20261017", 1) == 0, "Prošla nedelja");
        check(UnlockStreak.week(c, "20261030", "20261025", 1) == -1, "Prošla nedelja pre brojanja");
        check(UnlockStreak.cleanDaysThisWeek(c, "20261030", "20261020") == 5, "Čisti dani u nedelji");
        check(UnlockStreak.cleanDaysThisWeek(c, "20261030", "20261029") == 1, "Čisti dani od početka");
        check(UnlockStreak.countedThisWeek("20261030", "20261029") == 2, "Dva dana brojanja");
        check(UnlockStreak.countedThisWeek("20261030", "20261001") == 7, "Cela nedelja");
        check(UnlockStreak.current(c, "x", "20261020") == 0, "Loš datum");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
