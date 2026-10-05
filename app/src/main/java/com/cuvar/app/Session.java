package com.cuvar.app;

import android.os.SystemClock;

/** Pamti da je PIN unet, da ga ne tražimo na svakom ekranu. Ističe 30 s posle izlaska. */
final class Session {
    static boolean authed;
    static long lastSeen;

    private Session() {
    }

    static boolean valid() {
        return authed && SystemClock.elapsedRealtime() - lastSeen < 30000L;
    }

    static void seen() {
        lastSeen = SystemClock.elapsedRealtime();
    }
}
