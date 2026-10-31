package com.cuvar.app;

import com.cuvar.app.rules.ItemKey;

public final class ItemKeyTest {
    private static int checks;

    public static void main(String[] args) {
        check(ItemKey.app("com.game").equals("app:com.game"), "Ključ aplikacije");
        check(ItemKey.site("youtube.com").equals("site:youtube.com"), "Ključ sajta");
        check(ItemKey.isApp("app:com.game") && !ItemKey.isSite("app:com.game"), "Aplikacija nije sajt");
        check(ItemKey.isSite("site:youtube.com") && !ItemKey.isApp("site:youtube.com"), "Sajt nije aplikacija");
        check(!ItemKey.isApp(null) && !ItemKey.isSite(null), "Null nije ništa");
        check(!ItemKey.isSite("web:youtube.com") && !ItemKey.isApp("com.game"), "Drugi ključevi");
        check(ItemKey.name("app:com.game").equals("com.game"), "Paket iz ključa");
        check(ItemKey.name("site:m.youtube.com").equals("m.youtube.com"), "Domen iz ključa");
        check(ItemKey.name("web:x.com").equals("web:x.com"), "Nepoznat ključ ostaje");
        check(ItemKey.name(ItemKey.app("site:čudno")).equals("site:čudno"), "Samo prvi početak se skida");
        System.out.println("ItemKeyTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
    }
}
