package com.cuvar.app;

import com.cuvar.app.rules.AccessibilityShortcut;

/** Prepoznavanje Čuvara u prečici Pristupačnosti. */
public final class AccessibilityShortcutTest {
    private static int checks;
    private static final String S = "com.cuvar.app.GuardService";

    public static void main(String[] args) {
        check(!AccessibilityShortcut.contains(null, "com.cuvar.app", S), "null");
        check(!AccessibilityShortcut.contains("", "com.cuvar.app", S), "prazno");
        check(AccessibilityShortcut.contains("com.cuvar.app/com.cuvar.app.GuardService", "com.cuvar.app", S), "puna forma");
        check(AccessibilityShortcut.contains("com.cuvar.app/.GuardService", "com.cuvar.app", S), "kratka forma");
        check(AccessibilityShortcut.contains("COM.cuvar.app/.guardservice", "com.cuvar.app", S), "velika slova");
        check(AccessibilityShortcut.contains("a.b/.X:com.cuvar.app/.GuardService", "com.cuvar.app", S), "lista");
        check(AccessibilityShortcut.contains(" a.b/.X : com.cuvar.app/.GuardService ", "com.cuvar.app", S), "razmaci");
        check(!AccessibilityShortcut.contains("a.b/.X:c.d/.Y", "com.cuvar.app", S), "tuđe");
        check(!AccessibilityShortcut.contains("com.cuvar.app.debug/com.cuvar.app.GuardService", "com.cuvar.app", S),
                "debug nije release");
        check(AccessibilityShortcut.contains("com.cuvar.app.debug/com.cuvar.app.GuardService", "com.cuvar.app.debug", S),
                "debug paket");
        check(!AccessibilityShortcut.contains("com.cuvar.app/com.cuvar.app.GuardService", "com.cuvar.app.debug", S),
                "release nije debug");
        check(!AccessibilityShortcut.contains("com.cuvar.app/.Other", "com.cuvar.app", S), "druga klasa");
        check(!AccessibilityShortcut.contains("com.cuvar.app", "com.cuvar.app", S), "bez klase");
        check(!AccessibilityShortcut.contains("com.cuvar.app/", "com.cuvar.app", S), "prazna klasa");
        System.out.println("AccessibilityShortcutTest: " + checks + " provera");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
