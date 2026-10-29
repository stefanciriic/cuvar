package com.cuvar.app;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Engleski prevod: assets/en.tsv se učitava i sklopljene rečenice se prevode deo po deo. */
public final class LangTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (InputStreamReader r = new InputStreamReader(
                Files.newInputStream(Paths.get("app/src/main/assets/en.tsv")), StandardCharsets.UTF_8)) {
            Lang.load(r);
        }
        eq("Sačuvaj", "Save");
        eq("Danas si na youtube.com proveo 1 h 5 min. Dnevni limit je potrošen i youtube.com je zaključan do ponoći, ni šifrom se ne otvara.",
                "Today on youtube.com you spent 1 h 5 min. The daily limit is used up and youtube.com is locked until midnight; the code won't open it.");
        eq("Koliko je 5 km u metrima?", "What is 5 km in meters?");
        eq("Danas je utorak. Koji dan je bio pre 3 dana?", "Today is Tuesday. What day was it 3 days ago?");
        eq("pon, sre, pet", "Mon, Wed, Fri");
        eq("Najduže u komadu, u minutima, pa pauza od 15 min (0 = bez)",
                "Longest session in minutes, then a break of 15 min (0 = none)");
        eq("WhatsApp", "WhatsApp");

        // Tekst u kodu bez prevoda: samo upozorenje, da build ne stane.
        Pattern lit = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
        int missing = 0;
        for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) Files.list(Paths.get("app/src/main/java/com/cuvar/app"))::iterator) {
            String name = p.getFileName().toString();
            if (name.equals("QuizBank.java") || name.equals("Translit.java") || name.equals("Lang.java")) continue;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("//") || t.startsWith("*") || t.contains("GuardDiagnostics") || t.contains("Pattern.")) continue;
                Matcher m = lit.matcher(line);
                while (m.find()) {
                    String s = Lang.unescape(m.group(1));
                    if (!s.matches(".*[čćšžđČĆŠŽĐ].*") && !(s.contains(" ") && s.matches(".*[a-z]{3}.*"))) continue;
                    if (s.matches(".*\\b(EEE|HH|yyyy)\\b.*")) continue;
                    if (Lang.translate(s).equals(s) && !s.equals("Čuvar")) {
                        missing++;
                        System.out.println("Bez engleskog prevoda (" + name + "): " + s);
                    }
                }
            }
        }
        System.out.println("LangTest: " + checks + " provera prošlo, bez prevoda: " + missing);
    }

    private static void eq(String sr, String en) {
        checks++;
        String got = Lang.translate(sr);
        if (!en.equals(got)) throw new AssertionError(sr + "\n -> " + got + "\n očekivano " + en);
    }
}
