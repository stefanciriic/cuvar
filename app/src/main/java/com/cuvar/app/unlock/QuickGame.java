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
    /** Srednja strelica, boja slova (Stroop), znak koji se razlikuje, zapamćena polja i redosled polja. */
    public enum Kind { ARROW, COLOR, ODD, MEMORY, SEQUENCE }

    /** Strelice za igricu „Strelice“: prve dve na lakšim nivoima, sve četiri na najtežem. */
    public static final String[] ARROWS = {"←", "→", "↑", "↓"};

    /** Boje za igricu „Boje“: dobro se vide na tamnoj pozadini. */
    public static final int[] COLORS = {0xFFEF5350, 0xFF66BB6A, 0xFF42A5F5, 0xFFFFEE58, 0xFFBA68C8, 0xFFFFA726};
    public static final String[] COLOR_NAMES = {"CRVENA", "ZELENA", "PLAVA", "ŽUTA", "LJUBIČASTA", "NARANDŽASTA"};

    /** Parovi znakova za „Uljeza“: samo cifre i simboli, da ih ćirilica ne menja. */
    private static final String[][] ODD_PAIRS = {
            {"6", "9"}, {"8", "3"}, {"5", "6"}, {"8", "9"}, {"0", "8"}, {"69", "96"}, {"12", "21"}, {"88", "89"}, {"161", "191"}};

    private static final int[] ROUNDS = {6, 7, 8};
    private static final long[] TIME_MS = {18_000L, 18_000L, 20_000L};
    /** Pamćenje i Niz: manje zadataka jer svaki prvo treba pogledati. */
    private static final int[] SHOW_ROUNDS = {3, 3, 4};
    private static final long[] SHOW_TIME_MS = {25_000L, 30_000L, 35_000L};

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
        /** ODD, MEMORY, SEQUENCE: broj polja u redu mreže. */
        public final int side;
        /** MEMORY: polja koja treba naći (bilo kojim redom); SEQUENCE: polja redom. Inače null. */
        public final int[] targets;
        public final boolean ordered;

        Round(String prompt, int ink, String[] options, int[] colors, int answer, int side) {
            this(prompt, ink, options, colors, answer, side, null, false);
        }

        Round(String prompt, int ink, String[] options, int[] colors, int answer, int side, int[] targets, boolean ordered) {
            this.prompt = prompt;
            this.ink = ink;
            this.options = options;
            this.colors = colors;
            this.answer = answer;
            this.side = side;
            this.targets = targets;
            this.ordered = ordered;
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
    /** Koliko je polja ovog zadatka već tapnuto (MEMORY, SEQUENCE). */
    private int step;
    private boolean[] hit;

    public QuickGame(Kind kind, int level, Random random, long nowMs) {
        this.kind = kind;
        this.level = level = Math.max(0, Math.min(2, level));
        this.rounds = rounds(kind, level);
        this.timeMs = timeMs(kind, level);
        this.random = random;
        this.started = nowMs;
        round = make(kind, level, random);
    }

    /** Koliko zadataka ima partija za datu igricu i nivo. */
    public static int rounds(Kind kind, int level) {
        level = Math.max(0, Math.min(2, level));
        return shows(kind) ? SHOW_ROUNDS[level] : ROUNDS[level];
    }

    public static long timeMs(Kind kind, int level) {
        level = Math.max(0, Math.min(2, level));
        return shows(kind) ? SHOW_TIME_MS[level] : TIME_MS[level];
    }

    /** Igrica koja prvo pokazuje polja, pa ih sakrije. */
    public static boolean shows(Kind kind) {
        return kind == Kind.MEMORY || kind == Kind.SEQUENCE;
    }

    /** Da li je polje ovog zadatka već tapnuto (MEMORY, SEQUENCE). */
    public boolean hit(int option) {
        return hit != null && option >= 0 && option < hit.length && hit[option];
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
        if (round.targets != null) {
            if (hit == null) hit = new boolean[round.count()];
            if (option < 0 || option >= hit.length) return NumberGame.Tap.IGNORED;
            if (hit[option]) return NumberGame.Tap.IGNORED;
            boolean ok = round.ordered ? round.targets[step] == option : contains(round.targets, option);
            if (!ok) {
                over = true;
                return NumberGame.Tap.WRONG;
            }
            hit[option] = true;
            step++;
            if (step < round.targets.length) return NumberGame.Tap.OK;
        } else if (option != round.answer) {
            over = true;
            return NumberGame.Tap.WRONG;
        }
        step = 0;
        hit = null;
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
        if (kind == Kind.MEMORY) return cells(level == 2 ? 5 : 4, memoryTiles(level), false, r);
        if (kind == Kind.SEQUENCE) return cells(level == 2 ? 4 : 3, sequenceTiles(level), true, r);
        return odd(level, r);
    }

    /** Niz: koliko polja zasvetli redom (mreža 3x3, na najtežem 4x4). */
    public static int sequenceTiles(int level) {
        return 5 + Math.max(0, Math.min(2, level));
    }

    /** Pamćenje: koliko polja zasvetli (mreža 4x4, na najtežem 5x5). */
    public static int memoryTiles(int level) {
        return 6 + Math.max(0, Math.min(2, level));
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    /** Prazna mreža sa n različitih polja za pamćenje (redom ili ne). */
    private static Round cells(int side, int n, boolean ordered, Random r) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < side * side; i++) all.add(i);
        Collections.shuffle(all, r);
        int[] targets = new int[n];
        for (int i = 0; i < n; i++) targets[i] = all.get(i);
        String[] blank = new String[side * side];
        java.util.Arrays.fill(blank, "");
        return new Round(null, 0, blank, null, targets[0], side, targets, ordered);
    }

    private static Round arrow(int level, Random r) {
        int dirs = level == 0 ? 2 : 4;
        int len = 7;
        int mid = r.nextInt(dirs);
        // Okolne strelice su uglavnom drugačije od srednje, da zbune.
        int around = r.nextInt(4) == 0 ? mid : (mid + 1 + r.nextInt(dirs - 1)) % dirs;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int d = i == len / 2 ? mid : level > 0 && r.nextInt(3) == 0 ? r.nextInt(dirs) : around;
            if (i > 0) b.append(' ');
            b.append(ARROWS[d]);
        }
        String[] options = new String[dirs];
        System.arraycopy(ARROWS, 0, options, 0, dirs);
        return new Round(b.toString(), 0, options, null, mid, 0);
    }

    private static Round color(int level, Random r) {
        int n = level == 0 ? 4 : 6;
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
        int side = level == 2 ? 6 : 5;
        String[] pair = ODD_PAIRS[r.nextInt(ODD_PAIRS.length)];
        boolean flip = r.nextBoolean();
        String base = flip ? pair[1] : pair[0], other = flip ? pair[0] : pair[1];
        String[] cells = new String[side * side];
        int answer = r.nextInt(cells.length);
        for (int i = 0; i < cells.length; i++) cells[i] = i == answer ? other : base;
        return new Round(null, 0, cells, null, answer, side);
    }
}
