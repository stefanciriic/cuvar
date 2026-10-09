package com.cuvar.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Pitanja pre otključavanja. Deo pitanja je iz banke (QuizBank), a deo se pravi nasumično:
 * računanje, nizovi brojeva, rimski brojevi, merne jedinice, dani, sat i glavni gradovi.
 * Bez Android klasa, da bi se moglo proveriti običnim javac-om (tests/QuizTest).
 */
final class Quiz {

    /** Jedno pitanje. Ako je choices null, odgovor je broj koji se kuca. */
    static final class Question {
        final String category;
        final String text;
        final List<String> choices;
        final String answer;

        Question(String category, String text, List<String> choices, String answer) {
            this.category = category;
            this.text = text;
            this.choices = choices;
            this.answer = answer;
        }

        boolean typed() {
            return choices == null;
        }

        boolean isCorrect(String given) {
            if (given == null) return false;
            if (!typed()) return answer.equals(given);
            try {
                return Long.parseLong(given.trim()) == Long.parseLong(answer);
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    /** Koliko poslednjih pitanja se ne ponavlja. */
    static final int NO_REPEAT = 40;

    private static final Random RANDOM = new Random();
    private static final Deque<String> RECENT = new ArrayDeque<>();

    static final String[] DAYS = {"ponedeljak", "utorak", "sreda", "četvrtak", "petak", "subota", "nedelja"};

    private Quiz() {
    }

    static synchronized Question next() {
        return next(RANDOM);
    }

    /** Novo pitanje koje se nije pojavilo među poslednjih NO_REPEAT. */
    static synchronized Question next(Random r) {
        Question q = null;
        for (int i = 0; i < 50; i++) {
            q = any(r);
            if (!RECENT.contains(q.text + "|" + q.answer)) break;
        }
        RECENT.addLast(q.text + "|" + q.answer);
        while (RECENT.size() > NO_REPEAT) {
            RECENT.removeFirst();
        }
        return q;
    }

    private static Question any(Random r) {
        int roll = r.nextInt(100);
        // Pitanje treba da bude kratka mentalna pauza, a ne test programiranja.
        // Java/Claude grupe ostaju u banci za budući namenski izbor, ali nisu
        // deo podrazumevanog toka otključavanja.
        if (roll < 20) return math(r);
        if (roll < 35) return geography(r);
        if (roll < 78) return fromBank(r);
        switch (r.nextInt(5)) {
            case 0: return sequence(r);
            case 1: return roman(r);
            case 2: return units(r);
            case 3: return days(r);
            default: return clock(r);
        }
    }

    // ---------- Banka ----------

    /** Opšte znanje, bez Java i Claude pitanja. */
    static Question fromBank(Random r) {
        return fromGroups(r, 0, QuizBank.GENERAL);
    }

    /** Nasumično pitanje iz grupa from..to-1, svako pitanje podjednako verovatno. */
    static Question fromGroups(Random r, int from, int to) {
        int total = 0;
        for (int g = from; g < to; g++) total += QuizBank.GROUPS[g].length;
        int pick = r.nextInt(total);
        for (int g = from; g < to; g++) {
            String[] group = QuizBank.GROUPS[g];
            if (pick < group.length) return parse(QuizBank.NAMES[g], group[pick], r);
            pick -= group.length;
        }
        throw new IllegalStateException();
    }

    /** „Pitanje|tačan|netačan|netačan|netačan“ ili „Pitanje|broj“ (odgovor se kuca). */
    static Question parse(String category, String entry, Random r) {
        String[] p = entry.split("\\|");
        if (p.length == 2) {
            return new Question(category, p[0], null, p[1]);
        }
        List<String> choices = new ArrayList<>();
        for (int i = 1; i < p.length; i++) choices.add(p[i]);
        Collections.shuffle(choices, r);
        return new Question(category, p[0], choices, p[1]);
    }

    // ---------- Računanje ----------

    static Question math(Random r) {
        int level = r.nextInt(3);
        String label = "Računanje · " + (level == 0 ? "lako" : level == 1 ? "srednje" : "teško");
        int a, b, c;
        String expr;
        long ans;
        int kind = r.nextInt(5);
        if (level == 0) {
            switch (kind) {
                case 0:
                    a = between(r, 2, 60); b = between(r, 2, 40);
                    expr = a + " + " + b; ans = a + b; break;
                case 1:
                    a = between(r, 10, 90); b = between(r, 2, a);
                    expr = a + " − " + b; ans = a - b; break;
                case 2:
                case 3:
                    a = between(r, 2, 10); b = between(r, 2, 10);
                    expr = a + " × " + b; ans = (long) a * b; break;
                default:
                    b = between(r, 2, 10); c = between(r, 2, 10);
                    expr = (b * c) + " ÷ " + b; ans = c; break;
            }
        } else if (level == 1) {
            switch (kind) {
                case 0:
                    a = between(r, 100, 999); b = between(r, 15, 999);
                    expr = a + " + " + b; ans = a + b; break;
                case 1:
                    a = between(r, 200, 999); b = between(r, 15, a);
                    expr = a + " − " + b; ans = a - b; break;
                case 2:
                    a = between(r, 11, 99); b = between(r, 3, 9);
                    expr = a + " × " + b; ans = (long) a * b; break;
                case 3:
                    b = between(r, 3, 12); c = between(r, 6, 25);
                    expr = (b * c) + " ÷ " + b; ans = c; break;
                default:
                    a = between(r, 2, 30); b = between(r, 2, 9); c = between(r, 2, 9);
                    expr = a + " + " + b + " × " + c; ans = a + (long) b * c; break;
            }
        } else {
            switch (kind) {
                case 0:
                    a = between(r, 12, 49); b = between(r, 11, 29);
                    expr = a + " × " + b; ans = (long) a * b; break;
                case 1:
                    a = between(r, 3, 9); b = between(r, 12, 30); c = between(r, 2, a * b);
                    expr = a + " × " + b + " − " + c; ans = (long) a * b - c; break;
                case 2:
                    a = between(r, 11, 25);
                    expr = a + "²"; ans = (long) a * a; break;
                case 3:
                    a = between(r, 5, 40); b = between(r, 5, 40); c = between(r, 3, 9);
                    expr = "(" + a + " + " + b + ") × " + c; ans = (long) (a + b) * c; break;
                default:
                    b = between(r, 3, 9); c = between(r, 12, 99);
                    expr = (b * c) + " ÷ " + b; ans = c; break;
            }
        }
        if (level > 0 && r.nextInt(6) == 0) {
            int[] pcts = {10, 20, 25, 50, 75};
            int pct = pcts[r.nextInt(pcts.length)];
            int base = 4 * between(r, 5, 60); // deljivo sa 4, pa je i 25% i 75% ceo broj
            if (pct == 10 || pct == 20) base = 10 * between(r, 2, 40);
            return new Question(label, "Koliko je " + pct + "% od " + base + "?", null,
                    String.valueOf((long) base * pct / 100));
        }
        return new Question(label, "Koliko je " + expr + "?", null, String.valueOf(ans));
    }

    // ---------- Glavni gradovi i kontinenti ----------

    static Question geography(Random r) {
        String[] c = QuizBank.COUNTRIES[r.nextInt(QuizBank.COUNTRIES.length)].split("\\|");
        int kind = r.nextInt(5);
        if (kind == 4 && c.length > 2) {
            List<String> choices = new ArrayList<>();
            choices.add(c[2]);
            List<String> others = new ArrayList<>();
            for (String k : QuizBank.CONTINENTS) if (!k.equals(c[2])) others.add(k);
            Collections.shuffle(others, r);
            choices.addAll(others.subList(0, 3));
            Collections.shuffle(choices, r);
            return new Question("Geografija", "Na kom kontinentu je država " + c[0] + "?", choices, c[2]);
        }
        boolean reverse = kind >= 2;
        int field = reverse ? 0 : 1;
        List<String> choices = new ArrayList<>();
        choices.add(c[field]);
        // Netačni odgovori su po mogućstvu sa istog kontinenta, da ne bude prelako.
        List<String> same = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String row : QuizBank.COUNTRIES) {
            String[] o = row.split("\\|");
            if (o[0].equals(c[0])) continue;
            boolean near = c.length > 2 && o.length > 2 && o[2].equals(c[2]);
            (near ? same : rest).add(o[field]);
        }
        Collections.shuffle(same, r);
        Collections.shuffle(rest, r);
        same.addAll(rest);
        for (String o : same) {
            if (choices.size() == 4) break;
            if (!choices.contains(o)) choices.add(o);
        }
        Collections.shuffle(choices, r);
        String text = reverse ? c[1] + " je glavni grad koje države?"
                : "Država: " + c[0] + ". Koji je njen glavni grad?";
        return new Question("Glavni gradovi", text, choices, c[field]);
    }

    // ---------- Ostalo nasumično ----------

    static Question sequence(Random r) {
        long[] s = new long[5];
        int kind = r.nextInt(3);
        if (kind == 0) {
            int start = between(r, 1, 30), step = between(r, 2, 15);
            for (int i = 0; i < 5; i++) s[i] = start + (long) i * step;
        } else if (kind == 1) {
            int start = between(r, 1, 5), ratio = between(r, 2, 3);
            s[0] = start;
            for (int i = 1; i < 5; i++) s[i] = s[i - 1] * ratio;
        } else {
            int start = between(r, 1, 8);
            for (int i = 0; i < 5; i++) s[i] = (long) (start + i) * (start + i);
        }
        int hole = between(r, 1, 4);
        StringBuilder t = new StringBuilder("Koji broj nedostaje: ");
        for (int i = 0; i < 5; i++) {
            if (i > 0) t.append(", ");
            t.append(i == hole ? "?" : String.valueOf(s[i]));
        }
        return new Question("Niz brojeva", t.toString(), null, String.valueOf(s[hole]));
    }

    static Question roman(Random r) {
        int n = r.nextInt(3) == 0 ? between(r, 1000, 2030) : between(r, 4, 399);
        return new Question("Rimski brojevi", "Koji broj je zapisan rimskim brojem " + toRoman(n) + "?",
                null, String.valueOf(n));
    }

    static String toRoman(int n) {
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] s = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) {
                b.append(s[i]);
                n -= v[i];
            }
        }
        return b.toString();
    }

    static Question units(Random r) {
        // jedinica, u šta se pretvara, koliko puta, najveći broj
        String[][] u = {
                {"m", "centimetrima", "100", "30"},
                {"km", "metrima", "1000", "15"},
                {"kg", "gramima", "1000", "12"},
                {"t", "kilogramima", "1000", "9"},
                {"h", "minutima", "60", "12"},
                {"min", "sekundama", "60", "15"},
                {"dana", "satima", "24", "10"},
                {"cm", "milimetrima", "10", "90"},
        };
        String[] k = u[r.nextInt(u.length)];
        int n = between(r, 2, Integer.parseInt(k[3]));
        return new Question("Merne jedinice", "Koliko je " + n + " " + k[0] + " u " + k[1] + "?", null,
                String.valueOf((long) n * Integer.parseInt(k[2])));
    }

    static Question days(Random r) {
        int today = r.nextInt(7);
        int n = between(r, 2, 20);
        boolean back = r.nextBoolean();
        int day = Math.floorMod(today + (back ? -n : n), 7);
        List<String> choices = new ArrayList<>();
        choices.add(DAYS[day]);
        List<String> others = new ArrayList<>();
        for (int i = 0; i < 7; i++) if (i != day) others.add(DAYS[i]);
        Collections.shuffle(others, r);
        choices.addAll(others.subList(0, 3));
        Collections.shuffle(choices, r);
        String text = "Danas je " + DAYS[today] + ". Koji dan " + (back ? "je bio pre " + n + " dana?" : "će biti za " + n + " dana?");
        return new Question("Kalendar", text, choices, DAYS[day]);
    }

    static Question clock(Random r) {
        int now = between(r, 0, 23) * 60 + 5 * between(r, 0, 11);
        int add = 5 * between(r, 3, 40);
        int right = (now + add) % 1440;
        List<String> choices = new ArrayList<>();
        choices.add(time(right));
        List<Integer> shifts = new ArrayList<>();
        for (int d : new int[]{10, -10, 60, -60, 20, -20, 5, -5}) shifts.add(d);
        Collections.shuffle(shifts, r);
        for (int d : shifts) {
            String t = time(Math.floorMod(right + d, 1440));
            if (choices.size() < 4 && !choices.contains(t)) choices.add(t);
        }
        Collections.shuffle(choices, r);
        return new Question("Sat", "Sada je " + time(now) + ". Koliko je sati posle " + add + " minuta?",
                choices, time(right));
    }

    static String time(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static int between(Random r, int lo, int hi) {
        return lo + r.nextInt(hi - lo + 1);
    }
}
