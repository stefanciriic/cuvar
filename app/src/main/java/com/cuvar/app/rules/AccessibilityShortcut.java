package com.cuvar.app.rules;

/** Da li je Čuvar dodeljen prečici Pristupačnosti (vrednost Settings.Secure ključa, komponente odvojene sa ':'). */
public final class AccessibilityShortcut {
    private AccessibilityShortcut() {}

    /** Tačno poređenje komponenti: `com.cuvar.app` se ne poklapa sa `com.cuvar.app.debug` i obrnuto. */
    public static boolean contains(String value, String pkg, String serviceClass) {
        if (value == null || pkg == null || serviceClass == null) return false;
        for (String part : value.split(":")) {
            String c = part.trim();
            int slash = c.indexOf('/');
            if (slash <= 0 || slash == c.length() - 1) continue;
            String p = c.substring(0, slash);
            String cls = c.substring(slash + 1);
            if (cls.startsWith(".")) cls = p + cls; // kratka forma: com.cuvar.app/.GuardService
            if (p.equalsIgnoreCase(pkg) && cls.equalsIgnoreCase(serviceClass)) return true;
        }
        return false;
    }
}
