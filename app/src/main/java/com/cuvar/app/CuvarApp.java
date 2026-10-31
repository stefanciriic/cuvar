package com.cuvar.app;

import android.app.Application;

import com.cuvar.app.guard.GuardDiagnostics;

/** Pokretanje procesa: dnevnik grešaka i beleženje padova pre nego što ih Android obradi. */
public final class CuvarApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GuardDiagnostics.init(this);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                GuardDiagnostics.crash(error);
            } catch (Throwable ignored) {
                // beleženje ne sme da spreči normalnu obradu pada
            }
            // Pad se ne guta: Android i dalje gasi proces i ponovo pokreće servis.
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
