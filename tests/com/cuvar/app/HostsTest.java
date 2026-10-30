package com.cuvar.app;

/** Čist domen iz adrese i glavni domen hosta. */
public final class HostsTest {
    private static int checks;

    public static void main(String[] args) {
        eq(Hosts.hostOf("https://www.Facebook.com/x?y=1#z"), "facebook.com", "Adresa sa putanjom");
        eq(Hosts.hostOf("m.youtube.com"), "m.youtube.com", "Poddomen ostaje");
        eq(Hosts.hostOf("user:pass@reddit.com:443/r"), "reddit.com", "Korisnik i port");
        eq(Hosts.hostOf("linkedin.com."), "linkedin.com", "Tačka na kraju");
        eq(Hosts.hostOf("линкедин.цом"), "linkedin.com", "Ćirilica postaje latinica");
        eq(Hosts.hostOf("you​tube.com"), "youtube.com", "Nevidljivi znak se briše");
        eq(Hosts.hostOf("ovo nije adresa"), null, "Tekst sa razmakom");
        eq(Hosts.hostOf(""), null, "Prazno");
        eq(Hosts.hostOf(null), null, "Ništa");

        eq(Hosts.mainDomain("m.youtube.com"), "youtube.com", "Poddomen se sabira");
        eq(Hosts.mainDomain("news.bbc.co.uk"), "bbc.co.uk", "Državni domen drugog nivoa");
        eq(Hosts.mainDomain("nesto.org.rs"), "nesto.org.rs", "org.rs");
        eq(Hosts.mainDomain("a.b.example.rs"), "example.rs", "Običan domen");
        eq(Hosts.mainDomain("192.168.0.1"), "192.168.0.1", "IP adresa ostaje");
        eq(Hosts.mainDomain("localhost"), null, "Bez tačke nije domen");
        eq(Hosts.mainDomain("a b.com"), null, "Razmak nije domen");
        System.out.println("HostsTest: " + checks + " provera prošlo.");
    }

    private static void eq(String got, String want, String what) {
        checks++;
        if (want == null ? got != null : !want.equals(got)) throw new AssertionError(what + ": " + got);
    }
}
