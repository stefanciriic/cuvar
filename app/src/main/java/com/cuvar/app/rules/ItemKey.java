package com.cuvar.app.rules;

/** Ključ stavke pod pravilom: "app:paket" ili "site:domen" (blokada, otvaranja, vreme sajta). */
public final class ItemKey {
    public static final String APP = "app:";
    public static final String SITE = "site:";

    private ItemKey() {
    }

    public static String app(String pkg) {
        return APP + pkg;
    }

    public static String site(String domain) {
        return SITE + domain;
    }

    public static boolean isApp(String key) {
        return key != null && key.startsWith(APP);
    }

    public static boolean isSite(String key) {
        return key != null && key.startsWith(SITE);
    }

    /** Paket ili domen iz ključa; ključ bez poznatog početka vraća se kakav jeste. */
    public static String name(String key) {
        if (isApp(key)) return key.substring(APP.length());
        if (isSite(key)) return key.substring(SITE.length());
        return key;
    }
}
