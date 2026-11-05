package com.cuvar.app;

import com.cuvar.app.unlock.NumberGame;
import com.cuvar.app.unlock.QuickGame;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Kratke igrice: Računanje, Boje, Uljez. */
public final class QuickGameTest {
    private static int checks;

    public static void main(String[] args) {
        for (QuickGame.Kind k : QuickGame.Kind.values()) {
            for (int level = 0; level <= 2; level++) {
                for (int seed = 0; seed < 200; seed++) {
                    QuickGame g = new QuickGame(k, level, new Random(seed), 0);
                    QuickGame.Round r = g.round();
                    check(r.answer >= 0 && r.answer < r.count(), "Tačan odgovor je među ponuđenima " + k);
                    if (k == QuickGame.Kind.SUM) sum(r);
                    if (k == QuickGame.Kind.COLOR) color(r);
                    if (k == QuickGame.Kind.ODD) odd(r);
                }
                // Cela partija tačnim odgovorima.
                QuickGame g = new QuickGame(k, level, new Random(7), 0);
                for (int i = 1; i < g.rounds; i++) check(g.tap(g.round().answer, 100) == NumberGame.Tap.OK, "Tačno " + k);
                check(g.tap(g.round().answer, 200) == NumberGame.Tap.DONE, "Pobeda " + k);
                check(g.over() && g.tap(0, 300) == NumberGame.Tap.IGNORED, "Posle kraja ništa");
            }
        }
        check(QuickGame.rounds(0) < QuickGame.rounds(2), "Teže posle više otključavanja");

        QuickGame w = new QuickGame(QuickGame.Kind.SUM, 0, new Random(3), 0);
        int wrong = (w.round().answer + 1) % w.round().count();
        check(w.tap(wrong, 10) == NumberGame.Tap.WRONG && w.over(), "Pogrešan odgovor završava partiju");

        QuickGame t = new QuickGame(QuickGame.Kind.ODD, 1, new Random(4), 100);
        check(t.tap(t.round().answer, 100 + t.timeMs) == NumberGame.Tap.LATE && t.over(), "Isteklo vreme");
        System.out.println("Prošlo: " + checks + " provera.");
    }

    private static void sum(QuickGame.Round r) {
        String[] p = r.prompt.split(" ");
        int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[2]);
        int result = p[1].equals("+") ? a + b : a - b;
        check(result >= 0, "Rezultat nije negativan");
        check(Integer.parseInt(r.options[r.answer]) == result, "Tačan zbir " + r.prompt);
        Set<String> s = new HashSet<>();
        for (String o : r.options) check(s.add(o) && Integer.parseInt(o) >= 0, "Različiti ponuđeni brojevi");
        check(r.options.length == 4, "Četiri ponude");
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
