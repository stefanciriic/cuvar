package com.cuvar.app.unlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Kratke igrice pre otključavanja: niz od nekoliko brzih zadataka koje treba rešiti redom pre isteka
 * vremena. Greška ili isteklo vreme = nova partija. Težina (level 0..2) raste sa brojem otključavanja
 * u danu. Bez Android klasa (tests/QuickGameTest).
 */
public final class QuickGame {
    /** Srednja strelica, boja slova (Stroop) i znak koji se razlikuje. */
    public enum Kind { ARROW, COLOR, ODD }

    /** Strelice za igricu „Strelice“: prve dve na lakšim nivoima, sve četiri na najtežem. */
    public static final String[] ARROWS = {"←", "→", "↑", "↓"};

    /** Boje za igricu „Boje“: dobro se vide na tamnoj pozadini. */
    public static final int[] COLORS = {0xFFEF5350, 0xFF66BB6A, 0xFF42A5F5, 0xFFFFEE58, 0xFFBA68C8, 0xFFFFA726};
    public static final String[] COLOR_NAMES = {"CRVENA", "ZELENA", "PLAVA", "ŽUTA", "LJUBIČASTA", "NARANDŽASTA"};

    /** Parovi znakova za „Uljeza“: samo cifre i simboli, da ih ćirilica ne menja. */
    private static final String[][] ODD_PAIRS = {
            {"6", "9"}, {"8", "3"}, {"5", "6"}, {"1", "7"}, {"0", "8"}, {"69", "96"}, {"★", "☆"}, {"▲", "△"}, {"12", "21"}};

    private static final int[] ROUNDS = {4, 5, 6};
    private static final long[] TIME_MS = {20_000L, 20_000L, 25_000L};

    /** Jedan zadatak: šta piše, ponuđeni odgovori i koji je tačan. */
    public static final class Round {
        /** ARROW: niz strelica, tačna je srednja; COLOR: naziv boje; ODD: null. */
        public final String prompt;
        /** COLOR: boja kojom je reč napisana. */
        public final int ink;
        /** ARROW: ponuđeni smerovi; ODD: znak u svakom polju mreže; COLOR: null. */
        public final String[] options;
        /** COLOR: ponuđene boje. */
        public final int[] colors;
        public final int answer;
        /** ODD: broj polja u redu mreže. */
        public final int side;

        Round(String prompt, int ink, String[] options, int[] colors, int answer, int side) {
            this.prompt = prompt;
            this.ink = ink;
            this.options = options;
            this.colors = colors;
            this.answer = answer;
            this.side = side;
        }

        public int count() {
            return options != null ? options.length : colors.length;
        }
    }

    public final Kind kind;
    public final int level;
    public final int rounds;
    public final long timeMs;
    private final Random random;
    private final long started;
    private int done;
    private boolean over;
    private Round round;

    public QuickGame(Kind kind, int level, Random random, long nowMs) {
        this.kind = kind;
        this.level = level = Math.max(0, Math.min(2, level));
        this.rounds = ROUNDS[level];
        this.timeMs = TIME_MS[level];
        this.random = random;
        this.started = nowMs;
        round = make(kind, level, random);
    }

    /** Koliko zadataka ima partija za dati nivo. */
    public static int rounds(int level) {
        return ROUNDS[Math.max(0, Math.min(2, level))];
    }

    public static long timeMs(int level) {
        return TIME_MS[Math.max(0, Math.min(2, level))];
    }

    public Round round() {
        return round;
    }

    /** Koliko je zadataka rešeno. */
    public int done() {
        return done;
    }

    public boolean over() {
        return over;
    }

    public long leftMs(long nowMs) {
        return Math.max(0L, timeMs - (nowMs - started));
    }

    /** Tap na ponuđeni odgovor (indeks); tačan vodi na sledeći zadatak. */
    public NumberGame.Tap tap(int option, long nowMs) {
        if (over) return NumberGame.Tap.IGNORED;
        if (leftMs(nowMs) == 0) {
            over = true;
            return NumberGame.Tap.LATE;
        }
        if (option != round.answer) {
            over = true;
            return NumberGame.Tap.WRONG;
        }
        done++;
        if (done >= rounds) {
            over = true;
            return NumberGame.Tap.DONE;
        }
        round = make(kind, level, random);
        return NumberGame.Tap.OK;
    }

    static Round make(Kind kind, int level, Random r) {
        if (kind == Kind.ARROW) return arrow(level, r);
        if (kind == Kind.COLOR) return color(level, r);
        return odd(level, r);
    }

    private static Round arrow(int level, Random r) {
        int dirs = level == 2 ? 4 : 2;
        int len = level == 0 ? 5 : 7;
        int mid = r.nextInt(dirs);
        // Okolne strelice su uglavnom drugačije od srednje, da zbune.
        int around = r.nextInt(4) == 0 ? mid : (mid + 1 + r.nextInt(dirs - 1)) % dirs;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int d = i == len / 2 ? mid : level == 2 && r.nextInt(3) == 0 ? r.nextInt(dirs) : around;
            if (i > 0) b.append(' ');
            b.append(ARROWS[d]);
        }
        String[] options = new String[dirs];
        System.arraycopy(ARROWS, 0, options, 0, dirs);
        return new Round(b.toString(), 0, options, null, mid, 0);
    }

    private static Round color(int level, Random r) {
        int n = level == 0 ? 3 : level == 1 ? 4 : 6;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < COLORS.length; i++) idx.add(i);
        Collections.shuffle(idx, r);
        List<Integer> shown = new ArrayList<>(idx.subList(0, n));
        int ink = shown.get(0);
        // Reč je uvek druga boja: tapka se boja slova, ne ono što piše.
        int word = shown.get(1 + r.nextInt(n - 1));
        Collections.shuffle(shown, r);
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) colors[i] = COLORS[shown.get(i)];
        return new Round(COLOR_NAMES[word], COLORS[ink], null, colors, shown.indexOf(ink), 0);
    }

    private static Round odd(int level, Random r) {
        int side = level == 2 ? 5 : 4;
        String[] pair = ODD_PAIRS[r.nextInt(ODD_PAIRS.length)];
        boolean flip = r.nextBoolean();
        String base = flip ? pair[1] : pair[0], other = flip ? pair[0] : pair[1];
        String[] cells = new String[side * side];
        int answer = r.nextInt(cells.length);
        for (int i = 0; i < cells.length; i++) cells[i] = i == answer ? other : base;
        return new Round(null, 0, cells, null, answer, side);
    }
}
