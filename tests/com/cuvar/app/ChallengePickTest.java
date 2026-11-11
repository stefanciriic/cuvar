package com.cuvar.app;

import com.cuvar.app.unlock.ChallengePick;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Izbor izazova pri otključavanju: kod nasumičnog nikad isto kao prošli put. */
public final class ChallengePickTest {
    private static int checks;
    private static final String[] GAMES = {"numbers", "arrows", "color", "odd", "memory", "sequence"};

    public static void main(String[] args) {
        Random r = new Random(1);
        // "games": uvek igrica, nikad ista zaredom, sve se pojavljuju.
        Set<String> seen = new HashSet<>();
        String last = null;
        for (int i = 0; i < 2000; i++) {
            String p = ChallengePick.pick(ChallengePick.GAMES, GAMES, last, r);
            check(isGame(p), "Games daje igricu");
            check(!p.equals(last), "Games nikad isto zaredom");
            seen.add(p);
            last = p;
        }
        check(seen.size() == GAMES.length, "Sve igrice se pojavljuju");
        check(isGame(ChallengePick.pick(ChallengePick.GAMES, GAMES, null, r)), "Games bez prošlog");
        check(ChallengePick.pick(ChallengePick.GAMES, GAMES, ChallengePick.QUIZ, r) != null, "Games posle pitalica");
        String[] one = {"numbers"};
        check(ChallengePick.pick(ChallengePick.GAMES, one, "numbers", r).equals("numbers"), "Jedna igrica ostaje ista");

        // "mix": nikad isto zaredom, posle pitalica igrica, otprilike pola-pola.
        last = null;
        int quiz = 0;
        seen.clear();
        for (int i = 0; i < 4000; i++) {
            String p = ChallengePick.pick(ChallengePick.MIX, GAMES, last, r);
            check(p.equals(ChallengePick.QUIZ) || isGame(p), "Mix daje igricu ili pitalice");
            check(!p.equals(last), "Mix nikad isto zaredom");
            if (ChallengePick.QUIZ.equals(last)) check(isGame(p), "Posle pitalica igrica");
            if (p.equals(ChallengePick.QUIZ)) quiz++;
            seen.add(p);
            last = p;
        }
        check(seen.size() == GAMES.length + 1, "Mix daje sve");
        check(quiz > 1000 && quiz < 2000, "Mix otprilike pola-pola: " + quiz);
        int fromNull = 0;
        for (int i = 0; i < 2000; i++) if (ChallengePick.QUIZ.equals(ChallengePick.pick(ChallengePick.MIX, GAMES, null, r))) fromNull++;
        check(fromNull > 800 && fromNull < 1200, "Mix na početku pola-pola: " + fromNull);

        // Izabrano podešavanje ostaje isto.
        for (int i = 0; i < 50; i++) {
            check(ChallengePick.pick(ChallengePick.QUIZ, GAMES, ChallengePick.QUIZ, r).equals(ChallengePick.QUIZ), "Pitalice ostaju");
            check(ChallengePick.pick("memory", GAMES, "memory", r).equals("memory"), "Izabrana igrica ostaje");
        }
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static boolean isGame(String p) {
        for (String g : GAMES) if (g.equals(p)) return true;
        return false;
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
