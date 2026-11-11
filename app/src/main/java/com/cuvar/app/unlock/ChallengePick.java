package com.cuvar.app.unlock;

import java.util.Random;

/**
 * Bira šta se rešava za sledeći pokušaj otključavanja. Kod "games" i "mix" nikad isto kao prošli put;
 * izabrana pitalica ili konkretna igrica ostaje ista. Bez Android klasa (tests/ChallengePickTest).
 * Vrednosti su iste kao Ui.CHALLENGE_QUIZ, Ui.CHALLENGE_GAMES i Ui.CHALLENGE_MIX.
 */
public final class ChallengePick {
    public static final String QUIZ = "quiz";
    public static final String GAMES = "games";
    public static final String MIX = "mix";

    private ChallengePick() {
    }

    /** Sledeći izazov: QUIZ ili jedna od igrica iz games. last je prošli izbor (null ako ga nema). */
    public static String pick(String challenge, String[] games, String last, Random r) {
        if (GAMES.equals(challenge)) return otherGame(games, last, r);
        if (MIX.equals(challenge)) {
            // Posle pitalica uvek igrica; inače otprilike pola-pola, ali ne ista igrica.
            if (QUIZ.equals(last) || r.nextBoolean()) return otherGame(games, last, r);
            return QUIZ;
        }
        return challenge;
    }

    /** Nasumična igrica, različita od last kad lista ima više od jedne. */
    private static String otherGame(String[] games, String last, Random r) {
        if (games.length == 1) return games[0];
        int skip = -1;
        for (int i = 0; i < games.length; i++) if (games[i].equals(last)) skip = i;
        if (skip < 0) return games[r.nextInt(games.length)];
        int i = r.nextInt(games.length - 1);
        return games[i >= skip ? i + 1 : i];
    }
}
