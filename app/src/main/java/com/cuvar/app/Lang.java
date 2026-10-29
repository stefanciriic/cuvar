package com.cuvar.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Engleski prevod pri prikazu. Tekst Čuvara se u kodu piše na srpskom, a prevodi su u assets/en.tsv:
 * ceo tekst ili deo rečenice (kad se rečenica sklapa od delova i vrednosti) i njegov prevod.
 * Prikaz prvo traži ceo tekst, pa menja poznate delove, od najdužeg ka najkraćem.
 */
final class Lang {

    /** Izabran engleski. Postavlja se iz podešavanja (Ui.loadScript). */
    static volatile boolean english;

    private static volatile Map<String, String> exact = Collections.emptyMap();
    private static volatile List<String> parts = Collections.emptyList();

    private Lang() {
    }

    static boolean loaded() {
        return !exact.isEmpty();
    }

    /** Učitava „srpski<TAB>engleski“ redove; # na početku reda je komentar. */
    static void load(Reader in) throws IOException {
        Map<String, String> map = new HashMap<>();
        BufferedReader r = new BufferedReader(in);
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String sr = unescape(line.substring(0, tab));
            String en = unescape(line.substring(tab + 1));
            if (!sr.equals(en)) map.put(sr, en);
        }
        List<String> keys = new ArrayList<>();
        for (String k : map.keySet()) if (hasLetter(k)) keys.add(k);
        keys.sort((a, b) -> b.length() - a.length());
        exact = map;
        parts = keys;
    }

    static String get(String sr) {
        String en = exact.get(sr);
        return en == null ? sr : en;
    }

    /** Ceo tekst ili tekst sklopljen od delova i vrednosti (brojeva, naziva aplikacija). */
    static String translate(String s) {
        if (s == null || s.isEmpty()) return s;
        String whole = exact.get(s);
        if (whole != null) return whole;
        List<Edit> edits = edits(s);
        if (edits.isEmpty()) return s;
        StringBuilder out = new StringBuilder();
        int at = 0;
        for (Edit e : edits) {
            out.append(s, at, e.start).append(e.repl);
            at = e.end;
        }
        return out.append(s, at, s.length()).toString();
    }

    static final class Edit {
        final int start, end;
        final String repl;

        Edit(int start, int end, String repl) {
            this.start = start;
            this.end = end;
            this.repl = repl;
        }
    }

    /** Izmene redom od početka; delovi se ne preklapaju, a reč se menja samo cela. */
    static List<Edit> edits(String s) {
        List<Edit> out = new ArrayList<>();
        String whole = exact.get(s);
        if (whole != null) {
            out.add(new Edit(0, s.length(), whole));
            return out;
        }
        boolean[] used = new boolean[s.length()];
        for (String k : parts) {
            if (k.length() > s.length()) continue;
            int from = 0;
            while (true) {
                int at = s.indexOf(k, from);
                if (at < 0) break;
                int end = at + k.length();
                from = at + 1;
                if (!wordEdge(s, at, k, end)) continue;
                boolean free = true;
                for (int i = at; i < end; i++) {
                    if (used[i]) {
                        free = false;
                        break;
                    }
                }
                if (!free) continue;
                for (int i = at; i < end; i++) used[i] = true;
                out.add(new Edit(at, end, exact.get(k)));
                from = end;
            }
        }
        out.sort((a, b) -> a.start - b.start);
        return out;
    }

    private static boolean wordEdge(String s, int at, String k, int end) {
        if (Character.isLetter(k.charAt(0)) && at > 0 && Character.isLetter(s.charAt(at - 1))) return false;
        if (Character.isLetter(k.charAt(k.length() - 1)) && end < s.length() && Character.isLetter(s.charAt(end))) return false;
        return true;
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }

    /** Isti zapis kao u Java kodu: \n, \t, \", \\ i \\uXXXX. */
    static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                b.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'u':
                    if (i + 4 < s.length()) {
                        b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    break;
                default: b.append(n);
            }
        }
        return b.toString();
    }
}
