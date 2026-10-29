package com.cuvar.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Srpska latinica u ćirilicu i nazad. Tekst Čuvara se piše latinicom, a kad je izabrana ćirilica,
 * svaki natpis se pretvara pri prikazu. Nazivi aplikacija, adrese sajtova i strane reči
 * (WhatsApp, LinkedIn, youtube.com) ostaju latinicom.
 */
final class Translit {

    /** Izabrano pismo: true = ćirilica. Postavlja se iz podešavanja (Ui.loadScript). */
    static volatile boolean cyrillic;

    private static final String LAT = "abcčćdđefghijklmnoprsštuvzž";
    private static final String CYR = "абцчћдђефгхијклмнопрсштувзж";

    /** Nazivi koji se ne prevode: aplikacije na telefonu i poznate marke. */
    private static final Set<String> KEEP = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> BRANDS = new HashSet<>(Arrays.asList(
            "Chrome", "Google", "Brave", "Opera", "Samsung", "Android", "Facebook", "Instagram", "Viber",
            "Telegram", "Signal", "Messenger", "Edge", "Gmail", "Maps", "Reddit", "Play", "Store", "Vivaldi",
            "Mozilla", "Safari", "Snapchat", "Pinterest", "Spotify", "Discord", "Threads", "Kiwi", "Ecosia",
            "Huawei", "Xiaomi", "Accessibility", "Usage", "Access", "Settings", "GitHub"));

    private Translit() {
    }

    /** Zapamti naziv aplikacije da ostane kako ga telefon piše. Vraća isti naziv. */
    static String keep(String label) {
        if (label != null && label.length() > 1 && !hasSerbianLetter(label)) KEEP.add(label);
        return label;
    }

    /** Jedna izmena: deo [start, end) se menja tekstom repl. */
    static final class Edit {
        final int start, end;
        final String repl;

        Edit(int start, int end, String repl) {
            this.start = start;
            this.end = end;
            this.repl = repl;
        }
    }

    static String toCyrillic(String s) {
        if (s == null) return null;
        List<Edit> edits = edits(s);
        if (edits.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        int at = 0;
        for (Edit e : edits) {
            out.append(s, at, e.start).append(e.repl);
            at = e.end;
        }
        return out.append(s, at, s.length()).toString();
    }

    /** Izmene redom od početka teksta; koristi se i za tekst sa stilovima (Ui.cyr). */
    static List<Edit> edits(String s) {
        List<Edit> out = new ArrayList<>();
        int n = s.length();
        if (n == 0) return out;
        boolean[] keep = protectedChars(s);
        for (int i = 0; i < n; i++) {
            if (keep[i]) continue;
            char c = s.charAt(i);
            char next = i + 1 < n && !keep[i + 1] ? s.charAt(i + 1) : 0;
            String digraph = digraph(c, next);
            if (digraph != null) {
                out.add(new Edit(i, i + 2, digraph));
                i++;
                continue;
            }
            char lower = Character.toLowerCase(c);
            int k = LAT.indexOf(lower);
            if (k < 0) continue;
            char cy = CYR.charAt(k);
            out.add(new Edit(i, i + 1, String.valueOf(c == lower ? cy : Character.toUpperCase(cy))));
        }
        return out;
    }

    private static String digraph(char a, char b) {
        if (b == 0) return null;
        char la = Character.toLowerCase(a), lb = Character.toLowerCase(b);
        String low;
        if (la == 'l' && lb == 'j') low = "љ";
        else if (la == 'n' && lb == 'j') low = "њ";
        else if (la == 'd' && lb == 'ž') low = "џ";
        else return null;
        return Character.isUpperCase(a) ? low.toUpperCase(Locale.ROOT) : low;
    }

    /** Koji znakovi ostaju latinicom: nazivi aplikacija, marke, adrese i strane reči. */
    private static boolean[] protectedChars(String s) {
        int n = s.length();
        boolean[] keep = new boolean[n];
        for (String name : KEEP) mark(s, name, keep);
        for (String name : BRANDS) mark(s, name, keep);
        int i = 0;
        while (i < n) {
            if (Character.isWhitespace(s.charAt(i))) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && !Character.isWhitespace(s.charAt(j))) j++;
            if (foreign(s.substring(i, j))) Arrays.fill(keep, i, j, true);
            i = j;
        }
        return keep;
    }

    private static void mark(String s, String name, boolean[] keep) {
        int from = 0;
        while (true) {
            int at = s.indexOf(name, from);
            if (at < 0) return;
            int end = at + name.length();
            boolean startOk = at == 0 || !Character.isLetterOrDigit(s.charAt(at - 1));
            boolean endOk = end == s.length() || !Character.isLetterOrDigit(s.charAt(end));
            if (startOk && endOk) Arrays.fill(keep, at, end, true);
            from = at + 1;
        }
    }

    /** Reč koja nije srpska: adresa, slova q/w/x/y, VelikoSlovo usred reči, slova pomešana sa ciframa. */
    static boolean foreign(String token) {
        boolean letter = false, digit = false, lowerSeen = false;
        for (int k = 0; k < token.length(); k++) {
            char c = token.charAt(k);
            char l = Character.toLowerCase(c);
            if (l == 'q' || l == 'w' || l == 'x' || l == 'y') return true;
            if (c == '@' || c == '_' || c == '#') return true;
            if (c == '.' && k > 0 && k + 1 < token.length()
                    && Character.isLetterOrDigit(token.charAt(k - 1)) && Character.isLetter(token.charAt(k + 1))) {
                return true; // youtube.com, m.facebook.com
            }
            if (c == ':' && token.startsWith("//", k + 1)) return true;
            if (Character.isLetter(c)) {
                if (Character.isUpperCase(c) && lowerSeen) return true; // LinkedIn, YouTube
                if (Character.isLowerCase(c)) lowerSeen = true;
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        if (letter && digit) {
            // „1h” i „15min” su srpski; „v183” i „4G” nisu.
            String t = token.replaceAll("[^\\p{L}\\p{N}]", "");
            return !t.matches("\\d+(h|min|s|d|x)?");
        }
        return false;
    }

    private static boolean hasSerbianLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ("čćšžđ".indexOf(c) >= 0 || (c >= 'а' && c <= 'я') || "ђјљњћџ".indexOf(c) >= 0) return true;
        }
        return false;
    }

    /** Ćirilica u latinicu, za ono što korisnik ukuca (adresa sajta, pretraga). */
    static String toLatin(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char lower = Character.toLowerCase(c);
            String r;
            switch (lower) {
                case 'љ': r = "lj"; break;
                case 'њ': r = "nj"; break;
                case 'џ': r = "dž"; break;
                default:
                    int k = CYR.indexOf(lower);
                    r = k < 0 ? null : String.valueOf(LAT.charAt(k));
            }
            if (r == null) {
                out.append(c);
            } else if (c != lower) {
                out.append(Character.toUpperCase(r.charAt(0))).append(r.substring(1));
            } else {
                out.append(r);
            }
        }
        return out.toString();
    }
}
