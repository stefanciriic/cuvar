package com.cuvar.app;

import com.cuvar.app.rules.LightMode;

/** Kada servis radi kao foreground "laki" servis. */
public final class LightModeTest {
    private static int checks;

    public static void main(String[] args) {
        check(LightMode.foreground(true, false, true, true), "Laki, bez Pristupačnosti: radi");
        check(LightMode.foreground(true, true, true, true), "Laki i povezan: radi (nastavlja kad se Pristupačnost isključi)");
        check(LightMode.foreground(false, false, true, true), "Pun, Pristupačnost još nije povezana: radi");
        check(!LightMode.foreground(false, true, true, true), "Pun i povezan: ne treba");
        check(!LightMode.foreground(true, false, false, true), "Bez Pristupa korišćenju: ne može");
        check(!LightMode.foreground(true, false, true, false), "Bez prikaza preko aplikacija: ne može");
        System.out.println("LightModeTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
