package com.cuvar.app;

import android.app.Application;
import android.os.StrictMode;

import com.cuvar.app.guard.GuardDiagnostics;

/** Pokretanje procesa: dnevnik grešaka i beleženje padova pre nego što ih Android obradi. */
public final class CuvarApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GuardDiagnostics.init(this);
        if (BuildConfig.DEBUG) {
            // Samo u probnoj verziji: u logcat-u se vidi spor rad na glavnoj niti i zaboravljeni resursi.
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().detectCustomSlowCalls().penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects().detectActivityLeaks().detectLeakedRegistrationObjects()
                    .penaltyLog().build());
        }
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
