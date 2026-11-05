package com.cuvar.app;

import com.cuvar.app.unlock.NumberGame;
import com.cuvar.app.unlock.QuickGame;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Kratke igrice: Strelice, Boje, Uljez. */
public final class QuickGameTest {
    private static int checks;

    public static void main(String[] args) {
        for (QuickGame.Kind k : QuickGame.Kind.values()) {
            for (int level = 0; level <= 2; level++) {
                for (int seed = 0; seed < 200; seed++) {
                    QuickGame g = new QuickGame(k, level, new Random(seed), 0);
                    QuickGame.Round r = g.round();
                    check(r.answer >= 0 && r.answer < r.count(), "Tačan odgovor je među ponuđenima " + k);
                    if (k == QuickGame.Kind.ARROW) arrow(r);
                    if (k == QuickGame.Kind.COLOR) color(r);
                    if (k == QuickGame.Kind.ODD) odd(r);
                    if (QuickGame.shows(k)) cells(r, k, level);
                }
                // Cela partija tačnim odgovorima.
                QuickGame g = new QuickGame(k, level, new Random(7), 0);
                for (int i = 0; i < g.rounds; i++) {
                    int[] t = g.round().targets != null ? g.round().targets : new int[]{g.round().answer};
                    for (int j = 0; j < t.length; j++) {
                        boolean last = i == g.rounds - 1 && j == t.length - 1;
                        check(g.tap(t[j], 100) == (last ? NumberGame.Tap.DONE : NumberGame.Tap.OK), "Tačno " + k);
                    }
                }
                check(g.over() && g.tap(0, 300) == NumberGame.Tap.IGNORED, "Posle kraja ništa");
            }
        }
        check(QuickGame.rounds(QuickGame.Kind.ODD, 0) < QuickGame.rounds(QuickGame.Kind.ODD, 2), "Teže posle više otključavanja");

        QuickGame w = new QuickGame(QuickGame.Kind.ARROW, 0, new Random(3), 0);
        int wrong = (w.round().answer + 1) % w.round().count();
        check(w.tap(wrong, 10) == NumberGame.Tap.WRONG && w.over(), "Pogrešan odgovor završava partiju");

        QuickGame m = new QuickGame(QuickGame.Kind.MEMORY, 0, new Random(5), 0);
        int[] mt = m.round().targets;
        check(m.tap(mt[2], 10) == NumberGame.Tap.OK && m.hit(mt[2]), "Pamćenje: bilo kojim redom");
        check(m.tap(mt[2], 10) == NumberGame.Tap.IGNORED, "Već tapnuto polje se ne broji");
        int off = 0;
        while (contains(mt, off)) off++;
        check(m.tap(off, 20) == NumberGame.Tap.WRONG, "Pamćenje: pogrešno polje");

        QuickGame q = new QuickGame(QuickGame.Kind.SEQUENCE, 1, new Random(6), 0);
        check(q.tap(q.round().targets[1], 10) == NumberGame.Tap.WRONG, "Niz: pogrešan redosled");

        QuickGame t = new QuickGame(QuickGame.Kind.ODD, 1, new Random(4), 100);
        check(t.tap(t.round().answer, 100 + t.timeMs) == NumberGame.Tap.LATE && t.over(), "Isteklo vreme");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void arrow(QuickGame.Round r) {
        String[] a = r.prompt.split(" ");
        check(a.length % 2 == 1, "Neparan broj strelica");
        check(a[a.length / 2].equals(r.options[r.answer]), "Tačna je srednja strelica");
        check(r.options.length == 2 || r.options.length == 4, "Dva ili četiri smera");
    }

    private static void color(QuickGame.Round r) {
        check(r.colors[r.answer] == r.ink, "Tačna je boja slova");
        int word = -1;
        for (int i = 0; i < QuickGame.COLOR_NAMES.length; i++) if (QuickGame.COLOR_NAMES[i].equals(r.prompt)) word = i;
        check(word >= 0 && QuickGame.COLORS[word] != r.ink, "Reč nije ista kao boja slova");
        Set<Integer> s = new HashSet<>();
        boolean wordShown = false;
        for (int c : r.colors) {
            check(s.add(c), "Različite boje");
            wordShown |= c == QuickGame.COLORS[word];
        }
        check(wordShown, "Boja iz reči je ponuđena (zamka)");
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    private static void cells(QuickGame.Round r, QuickGame.Kind k, int level) {
        check(r.targets.length == (k == QuickGame.Kind.MEMORY ? QuickGame.memoryTiles(level) : QuickGame.sequenceTiles(level)) && r.ordered == (k == QuickGame.Kind.SEQUENCE), "Broj polja za pamćenje");
        Set<Integer> s = new HashSet<>();
        for (int t : r.targets) check(t >= 0 && t < r.count() && s.add(t), "Različita polja u mreži");
    }

    private static void odd(QuickGame.Round r) {
        check(r.options.length == r.side * r.side, "Puna mreža");
        int diff = 0;
        String base = r.options[(r.answer + 1) % r.options.length];
        for (String o : r.options) if (!o.equals(base)) diff++;
        check(diff == 1 && !r.options[r.answer].equals(base), "Tačno jedan se razlikuje");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
