package com.cuvar.app;

import java.util.Locale;

/** Kopije aplikacija i aplikacije koje ih prave, da se pravila ne zaobiđu kloniranjem. */
final class Clones {
    /** Aplikacije koje pokreću kopije drugih aplikacija pod svojim imenom, pa ih Čuvar ne bi prepoznao. */
    static final String[] CLONERS = {"com.lbe.parallel", "com.parallel.space", "com.excelliance.multiaccount",
            "com.excelliance.dualaid", "com.ludashi.dualspace", "com.ludashi.superclone", "com.polestar.multiaccount",
            "com.polestar.domultiple", "com.jumobile.multiapp", "com.applisto.appcloner", "com.dualspace", "com.multiple.account",
            "com.trendmicro.dualapps", "do.multiple.cloner", "com.cloneapp", "com.waxmoon.ma.gp", "com.oasisfeng.island",
            "com.lody.virtual", "io.va.exposed", "com.vmos"};

    private Clones() {
    }

    /** Poznata aplikacija za kloniranje (po početku naziva paketa). */
    static boolean isCloner(String pkg) {
        for (String c : CLONERS) if (pkg.startsWith(c)) return true;
        return false;
    }

    /** Kopija po paketu: paket originala produžen (com.instagram.android2). */
    static boolean isPackageCopy(String pkg, String originalPkg) {
        return pkg.startsWith(originalPkg) && pkg.length() > originalPkg.length();
    }

    /**
     * Kopija po nazivu: naziv originala produžen brojem ili zagradom („Instagram 2“, „Instagram (1)“).
     * Kratki nazivi (manje od 4 slova) i nazivi jednaki paketu se ne porede, da ne bi bilo lažnih poklapanja.
     */
    static boolean isLabelCopy(String label, String originalPkg, String originalLabel) {
        String l = label.toLowerCase(Locale.ROOT).trim();
        String gl = originalLabel.toLowerCase(Locale.ROOT).trim();
        return gl.length() >= 4 && !gl.equals(originalPkg) && l.length() > gl.length() && l.startsWith(gl)
                && l.substring(gl.length()).matches("[\\s\\d()+.-]+");
    }
}
