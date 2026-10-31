package com.cuvar.app;

import android.app.Application;
import android.os.StrictMode;

import com.cuvar.app.data.Store;
import com.cuvar.app.guard.GuardDiagnostics;
import com.cuvar.app.ui.Safe;
import com.cuvar.app.ui.Ui;

/** Pokretanje procesa: dnevnik grešaka i beleženje padova pre nego što ih Android obradi. */
public final class CuvarApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GuardDiagnostics.init(this);
        Ui.warmUp(this);
        // Pravila se čitaju sa diska odmah, na pozadinskoj niti: ekran i zaštita ih onda ne čekaju na glavnoj niti.
        // Store.get je synchronized, pa ko stigne pre kraja učitavanja samo sačeka isti objekat.
        new Thread(() -> Safe.run("storePreload", () -> Store.get(this)), "cuvar-store").start();
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
