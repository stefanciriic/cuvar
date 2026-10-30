package com.cuvar.app;

/** Ekran blokade ne zaustavlja muziku koja je krenula iz druge aplikacije (Spotify u pozadini). */
public final class MusicOwnerTest {
    private static int checks;

    public static void main(String[] args) {
        String spotify = "com.spotify.music", home = "launcher", ig = "com.instagram.android";

        // Spotify pušten sa početnog ekrana, pa otvoren Instagram pod ključem.
        String owner = MusicOwner.next(false, false, true, null, home);
        check(home.equals(owner), "Muzika je krenula dok je napred bio početni ekran");
        check(MusicOwner.belongsToOther(true, owner, ig), "Blokada Instagrama ne zaustavlja Spotify");

        // Instagram se koristi uz Spotify i limit istekne: muzika i dalje nije Instagramova.
        owner = MusicOwner.next(false, true, true, owner, ig);
        check(home.equals(owner), "Vlasnik ostaje dok muzika svira");
        check(MusicOwner.belongsToOther(true, owner, ig), "Istekao limit uz Spotify: muzika svira dalje");

        // Muzika stane, pa krene dok je Instagram napred: to je zvuk Instagrama.
        owner = MusicOwner.next(false, true, false, owner, ig);
        check(owner == null, "Muzika je stala");
        owner = MusicOwner.next(false, false, true, owner, ig);
        check(ig.equals(owner), "Zvuk je krenuo u Instagramu");
        check(!MusicOwner.belongsToOther(true, owner, ig), "Zvuk zaključane aplikacije se zaustavlja");

        // Čuvar pokrenut dok muzika već svira: ne zna se čija je, ne dira se.
        owner = MusicOwner.next(true, false, true, null, ig);
        check(MusicOwner.UNKNOWN.equals(owner), "Nepoznat vlasnik");
        check(MusicOwner.belongsToOther(true, owner, ig), "Nepoznata muzika se ne zaustavlja");

        check(!MusicOwner.belongsToOther(false, spotify, ig), "Ništa ne svira: blokada sme da utiša");
        System.out.println("MusicOwnerTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
