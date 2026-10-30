package com.cuvar.app;

/** Spajanje današnjeg i novog pravila: uvek ostaje strožije. */
public final class RuleMergeTest {
    private static int checks;

    public static void main(String[] args) {
        check(RuleMerge.stricterLimit(0, 0) == 0, "Oba bez limita");
        check(RuleMerge.stricterLimit(0, 30) == 30, "Novi limit važi odmah");
        check(RuleMerge.stricterLimit(30, 0) == 30, "Brisanje limita čeka sutra");
        check(RuleMerge.stricterLimit(30, 60) == 30, "Povećanje čeka sutra");
        check(RuleMerge.stricterLimit(60, 30) == 30, "Smanjenje važi odmah");
        check(RuleMerge.stricterLimit(-5, 10) == 10, "Negativno je isto što i bez limita");
        check(RuleMerge.stricterLimit(10, -5) == 10, "Negativno je isto što i bez limita");
        check(RuleMerge.stricterLimit(-5, -5) == 0, "Negativno oba: bez limita");

        int none = RuleMerge.NO_SITE;
        check(RuleMerge.stricterSite(none, none) == none, "Sajt bez pravila");
        check(RuleMerge.stricterSite(none, 0) == 0, "Novo blokiranje važi odmah");
        check(RuleMerge.stricterSite(0, none) == 0, "Brisanje blokade čeka sutra");
        check(RuleMerge.stricterSite(0, 30) == 0, "Blokada je strožija od limita");
        check(RuleMerge.stricterSite(30, 0) == 0, "Blokada je strožija od limita");
        check(RuleMerge.stricterSite(30, 60) == 30, "Manji limit sajta");
        check(RuleMerge.stricterSite(none, 45) == 45, "Novi limit sajta");

        check(!RuleMerge.anyAppRule(false, 0, 0, 0), "Prazno pravilo se ne čuva");
        check(RuleMerge.anyAppRule(true, 0, 0, 0), "Zaključavanje");
        check(RuleMerge.anyAppRule(false, 0, 3, 0), "Broj otvaranja");
        check(RuleMerge.anyAppRule(false, 0, 0, 20), "Najduže u komadu");
        System.out.println("RuleMergeTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
