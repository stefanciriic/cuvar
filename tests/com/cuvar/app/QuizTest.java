package com.cuvar.app;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Pokretanje: javac -encoding UTF-8 -d .review/quiz app/src/main/java/com/cuvar/app/Quiz.java app/src/main/java/com/cuvar/app/QuizBank.java tests/com/cuvar/app/QuizTest.java
 *  java -cp .review/quiz com.cuvar.app.QuizTest */
public final class QuizTest {
    private static int checks;

    public static void main(String[] args) {
        bank();
        countries();
        generated();
        noRepeat();
        System.out.println("Prošlo: " + checks + " provera.");
    }

    /** Svaki red banke je ispravno zapisan, bez duplikata. */
    private static void bank() {
        check(QuizBank.NAMES.length == QuizBank.GROUPS.length, "Broj naziva grupa", "");
        Set<String> seen = new HashSet<>();
        Random r = new Random(1);
        int total = 0;
        for (int g = 0; g < QuizBank.GROUPS.length; g++) {
            for (String entry : QuizBank.GROUPS[g]) {
                total++;
                String[] p = entry.split("\\|", -1);
                check(p.length == 2 || p.length == 5, "Dva ili pet delova", entry);
                for (String part : p) check(!part.trim().isEmpty() && part.equals(part.trim()), "Prazan deo ili razmak", entry);
                check(seen.add(p[0] + "|" + p[1]), "Duplikat", entry);
                if (p.length == 2) {
                    check(p[1].matches("\\d{1,8}"), "Odgovor koji se kuca mora biti broj", entry);
                } else {
                    Set<String> distinct = new HashSet<>();
                    for (int i = 1; i < p.length; i++) distinct.add(p[i]);
                    check(distinct.size() == 4, "Ponuđeni odgovori se ponavljaju", entry);
                }
                Quiz.Question q = Quiz.parse(QuizBank.NAMES[g], entry, r);
                question(q);
            }
        }
        check(total >= 300, "Banka ima bar 300 pitanja (ima " + total + ")", "");
    }

    private static void countries() {
        Set<String> names = new HashSet<>();
        Set<String> capitals = new HashSet<>();
        Set<String> continents = new HashSet<>();
        for (String k : QuizBank.CONTINENTS) continents.add(k);
        for (String row : QuizBank.COUNTRIES) {
            String[] c = row.split("\\|", -1);
            check(c.length == 2 || c.length == 3, "Država|grad|kontinent", row);
            check(names.add(c[0]), "Država se ponavlja", row);
            check(capitals.add(c[1]), "Glavni grad se ponavlja", row);
            check(!c[0].equals(c[1]), "Grad se zove isto kao država", row);
            if (c.length == 3) check(continents.contains(c[2]), "Nepoznat kontinent", row);
        }
    }

    /** Nasumična pitanja: tačan odgovor je među ponuđenima, a računica je tačna. */
    private static void generated() {
        Random r = new Random(7);
        for (int i = 0; i < 20000; i++) {
            Quiz.Question q = Quiz.next(r);
            question(q);
            if (q.text.startsWith("Koliko je ") && q.category.startsWith("Računanje")) {
                String e = q.text.substring(10, q.text.length() - 1);
                long expect;
                if (e.contains("% od ")) {
                    String[] p = e.split("% od ");
                    long pct = Long.parseLong(p[0]), base = Long.parseLong(p[1]);
                    check(base * pct % 100 == 0, "Procenat nije ceo broj", q.text);
                    expect = base * pct / 100;
                } else {
                    expect = new Calc(e).value();
                }
                check(Long.parseLong(q.answer) == expect, "Pogrešan rezultat", q.text + " = " + q.answer);
                check(expect >= 0, "Negativan rezultat", q.text);
            }
            if (q.category.equals("Rimski brojevi")) {
                String rom = q.text.substring(q.text.lastIndexOf(' ') + 1, q.text.length() - 1);
                check(fromRoman(rom) == Long.parseLong(q.answer), "Rimski broj", q.text);
                check(Quiz.toRoman(Integer.parseInt(q.answer)).equals(rom), "Rimski zapis", q.text);
            }
        }
        check(Quiz.toRoman(1984).equals("MCMLXXXIV"), "1984", "");
        check(Quiz.toRoman(2026).equals("MMXXVI"), "2026", "");
    }

    /** Isto pitanje se ne javlja dva puta u nizu od NO_REPEAT. */
    private static void noRepeat() {
        Random r = new Random(3);
        java.util.ArrayDeque<String> last = new java.util.ArrayDeque<>();
        for (int i = 0; i < 5000; i++) {
            Quiz.Question q = Quiz.next(r);
            String key = q.text + "|" + q.answer;
            check(!last.contains(key), "Ponovljeno pitanje", q.text);
            last.addLast(key);
            if (last.size() > Quiz.NO_REPEAT) last.removeFirst();
        }
    }

    private static void question(Quiz.Question q) {
        check(q.text != null && !q.text.isEmpty(), "Prazno pitanje", q.category);
        check(q.isCorrect(q.answer), "Tačan odgovor se ne prihvata", q.text);
        if (q.typed()) {
            check(q.answer.matches("\\d{1,8}"), "Odgovor koji se kuca mora biti broj do 8 cifara", q.text + " " + q.answer);
            check(!q.isCorrect(String.valueOf(Long.parseLong(q.answer) + 1)), "Pogrešan broj se prihvata", q.text);
        } else {
            List<String> c = q.choices;
            check(c.size() == 4, "Četiri ponuđena odgovora", q.text + " " + c);
            check(new HashSet<>(c).size() == c.size(), "Ponuđeni odgovori se ponavljaju", q.text + " " + c);
            check(c.contains(q.answer), "Tačan odgovor nije ponuđen", q.text);
            int right = 0;
            for (String s : c) if (q.isCorrect(s)) right++;
            check(right == 1, "Tačno jedan tačan odgovor", q.text);
        }
    }

    private static long fromRoman(String s) {
        String sym = "IVXLCDM";
        int[] val = {1, 5, 10, 50, 100, 500, 1000};
        long sum = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = val[sym.indexOf(s.charAt(i))];
            int next = i + 1 < s.length() ? val[sym.indexOf(s.charAt(i + 1))] : 0;
            sum += v < next ? -v : v;
        }
        return sum;
    }

    /** Mali nezavisni računar za + − × ÷ ² i zagrade. */
    private static final class Calc {
        private final String s;
        private int i;

        Calc(String s) {
            this.s = s.replace(" ", "");
        }

        long value() {
            long v = sum();
            check(i == s.length(), "Neočitan izraz", s);
            return v;
        }

        private long sum() {
            long v = product();
            while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '−')) {
                char op = s.charAt(i++);
                long w = product();
                v = op == '+' ? v + w : v - w;
            }
            return v;
        }

        private long product() {
            long v = atom();
            while (i < s.length() && (s.charAt(i) == '×' || s.charAt(i) == '÷')) {
                char op = s.charAt(i++);
                long w = atom();
                if (op == '÷') {
                    check(w != 0 && v % w == 0, "Deljenje nije bez ostatka", s);
                    v = v / w;
                } else {
                    v = v * w;
                }
            }
            return v;
        }

        private long atom() {
            long v;
            if (s.charAt(i) == '(') {
                i++;
                v = sum();
                i++; // ')'
            } else {
                int start = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                v = Long.parseLong(s.substring(start, i));
            }
            if (i < s.length() && s.charAt(i) == '²') {
                i++;
                v = v * v;
            }
            return v;
        }
    }

    private static void check(boolean ok, String what, String detail) {
        checks++;
        if (!ok) {
            throw new AssertionError(what + ": " + detail);
        }
    }
}
