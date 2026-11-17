package com.cuvar.app.rules;

/**
 * Laki režim: Čuvar radi bez Pristupačnosti, kao foreground servis (Pristup korišćenju + prikaz preko
 * aplikacija). Bez Android klasa (tests/LightModeTest).
 */
public final class LightMode {
    private LightMode() { }

    /**
     * Da li servis treba da radi kao foreground "laki" servis: u lakom režimu, i u punom dok Pristupačnost
     * nije povezana (prelazak na pun nikad ne ostavlja telefon bez čuvara). Bez obe dozvole ne može da radi.
     */
    public static boolean foreground(boolean light, boolean connected, boolean usageOk, boolean overlayOk) {
        return usageOk && overlayOk && (light || !connected);
    }
}
