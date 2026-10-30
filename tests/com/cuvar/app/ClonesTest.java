package com.cuvar.app;

/** Kopije aplikacija potpadaju pod pravila originala. */
public final class ClonesTest {
    private static int checks;

    public static void main(String[] args) {
        check(Clones.isCloner("com.lbe.parallel.intl"), "Parallel Space");
        check(Clones.isCloner("com.applisto.appcloner"), "App Cloner");
        check(!Clones.isCloner("com.instagram.android"), "Instagram nije kloner");

        String ig = "com.instagram.android";
        check(Clones.isPackageCopy("com.instagram.android2", ig), "Produžen paket");
        check(!Clones.isPackageCopy(ig, ig), "Isti paket nije kopija");
        check(!Clones.isPackageCopy("com.instagram.lite", ig), "Drugi paket");

        check(Clones.isLabelCopy("Instagram 2", ig, "Instagram"), "Naziv sa brojem");
        check(Clones.isLabelCopy("Instagram (1)", ig, "Instagram"), "Naziv sa zagradom");
        check(Clones.isLabelCopy(" instagram+ ", ig, "Instagram"), "Mala slova i razmaci");
        check(!Clones.isLabelCopy("Instagram Lite", ig, "Instagram"), "Druga aplikacija sa sličnim nazivom");
        check(!Clones.isLabelCopy("Instagram", ig, "Instagram"), "Isti naziv nije kopija");
        check(!Clones.isLabelCopy("X 2", "com.twitter.android", "X"), "Kratak naziv se ne poredi");
        check(!Clones.isLabelCopy("com.foo.bar2", "com.foo.bar", "com.foo.bar"), "Naziv jednak paketu se ne poredi");
        System.out.println("ClonesTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
