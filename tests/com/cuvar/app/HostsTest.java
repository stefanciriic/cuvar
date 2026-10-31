package com.cuvar.app;

import com.cuvar.app.web.Hosts;

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
        System.out.println("HostsTest: " + checks + " provera prošlo.");
    }

    private static void eq(String got, String want, String what) {
        checks++;
        if (want == null ? got != null : !want.equals(got)) throw new AssertionError(what + ": " + got);
    }
}
