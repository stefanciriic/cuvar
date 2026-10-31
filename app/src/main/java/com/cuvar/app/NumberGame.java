package com.cuvar.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Igrica „Brojevi redom“ pre otključavanja: brojevi 1..SIZE razbacani po mreži,
 * tapkaju se redom pre isteka vremena. Greška ili isteklo vreme = nova partija.
 * Bez Android klasa (tests/NumberGameTest).
 */
final class NumberGame {
    static final int SIDE = 5;
    static final int SIZE = SIDE * SIDE;
    static final long TIME_MS = 40_000L;

    enum Tap { OK, WRONG, DONE, LATE, IGNORED }

    /** Broj u svakom polju mreže, red po red. */
    final int side;
    final int size;
    final int[] cells;
    private final long started;
    private final long timeMs;
    private int next = 1;
    private boolean over;

    NumberGame(Random r, long nowMs) {
        this(r, nowMs, SIDE, TIME_MS);
    }

    NumberGame(Random r, long nowMs, int side, long timeMs) {
        this.side = side;
        this.size = side * side;
        this.timeMs = timeMs;
        cells = new int[size];
        List<Integer> n = new ArrayList<>();
        for (int i = 1; i <= size; i++) n.add(i);
        Collections.shuffle(n, r);
        for (int i = 0; i < size; i++) cells[i] = n.get(i);
        started = nowMs;
    }

    /** Sledeći broj koji treba tapnuti. */
    int next() {
        return next;
    }

    boolean over() {
        return over;
    }

    long leftMs(long nowMs) {
        return Math.max(0L, timeMs - (nowMs - started));
    }

    /** Tap na broj; posle kraja partije tapovi se ne broje. */
    Tap tap(int number, long nowMs) {
        if (over) return Tap.IGNORED;
        if (leftMs(nowMs) == 0) {
            over = true;
            return Tap.LATE;
        }
        if (number < next) return Tap.IGNORED; // već tapnut
        if (number != next) {
            over = true;
            return Tap.WRONG;
        }
        next++;
        if (next > size) {
            over = true;
            return Tap.DONE;
        }
        return Tap.OK;
    }
}
