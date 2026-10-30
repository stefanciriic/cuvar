package com.cuvar.app;

import java.util.Locale;

/** Prepoznavanje sistemskih ekrana po imenu paketa, aktivnosti ili tekstu (bez čitanja sadržaja aplikacija). */
final class SystemScreens {
    /** Reči sa ekrana za brisanje aplikacije (u instalaciji i Play prodavnici se Čuvar sme samo ažurirati). */
    static final String[] UNINSTALL = {"uninstall", "deinstal", "деинстал", "ukloni", "уклони", "obriši", "обриши",
            "izbriši", "избриши", "delete", "remove"};

    private SystemScreens() {
    }

    /** Ekran poziva po imenu (npr. VoipActivityV2, PhoneFragmentActivity, RtcCallActivity), a ne istorija poziva. */
    static boolean callScreen(String cls) {
        String n = cls.substring(cls.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (n.contains("log") || n.contains("history") || n.contains("link") || n.contains("setting")
                || n.contains("info") || n.contains("contact") || n.contains("picker") || n.contains("permission")) {
            return false;
        }
        return n.contains("call") || n.contains("voip") || n.startsWith("phone");
    }

    /** Podešavanja telefona, instalacija i brisanje aplikacija, Play prodavnica i slični sistemski ekrani. */
    static boolean settingsLike(String p) {
        return p.contains("settings") || p.contains("packageinstaller") || p.contains("permissioncontroller")
                || p.contains("securitycenter") || p.contains("safecenter") || p.contains("accessibility")
                || p.equals("com.android.vending") || p.equals("com.samsung.android.lool");
    }

    /** Ekran za instalaciju ili Play prodavnica: tu se Čuvar sme ažurirati, samo se brisanje zatvara. */
    static boolean installer(String p) {
        return p.contains("packageinstaller") || p.equals("com.android.vending");
    }

    /** Da li tekst sa ekrana pominje Čuvara (ali ne „čuvar ekrana“, koji je podešavanje ekrana). */
    static boolean namesGuard(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("(čuvar|чувар)[a-zа-я]* (ekrana|екрана)", "");
        return s.contains("čuvar") || s.contains("чувар");
    }
}
