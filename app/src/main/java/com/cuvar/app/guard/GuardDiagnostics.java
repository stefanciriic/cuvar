package com.cuvar.app.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Lokalna dijagnostika: greška se upiše u logcat (najviše jednom u minutu po mestu) i u dnevnik na telefonu.
 * Nikad poruka greške, adrese, paketi ni sadržaj ekrana (vidi ErrorLog).
 */
public final class GuardDiagnostics {
    private static final String FILE = "cuvar_errors";
    private static final String KEY = "log";
    private static final long SAVE_EVERY_MS = 10000L;
    private static final Map<String, Long> LAST = new HashMap<>();
    private static SharedPreferences prefs;
    private static ErrorLog log;
    private static long savedAt;
    private static boolean dirty;
    /** Sat za prikaz u dnevniku; servis ga zameni pouzdanim satom (store.now()) čim se pokrene. */
    private static volatile LongSupplier clock = System::currentTimeMillis;

    private GuardDiagnostics() { }

    /** Poziva se jednom, na pokretanju procesa. Poseban fajl, da oštećena podešavanja ne pokvare dnevnik. */
    public static synchronized void init(Context c) {
        if (prefs != null) return;
        try {
            prefs = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
            log = ErrorLog.parse(prefs.getString(KEY, ""));
        } catch (Throwable error) {
            log = new ErrorLog();
            Log.e("CuvarGuard", "diagnosticsInit:" + error.getClass().getSimpleName());
        }
    }

    public static void useClock(LongSupplier now) {
        if (now != null) clock = now;
    }

    public static synchronized void report(String operation, Throwable error) {
        long el = SystemClock.elapsedRealtime();
        record(operation, error);
        if (dirty && el - savedAt >= SAVE_EVERY_MS) save(false);
        Long last = LAST.get(operation);
        if (last != null && el - last < 60000L) return;
        LAST.put(operation, el);
        Log.e("CuvarGuard", operation + ": " + (error == null ? "?" : error.getClass().getSimpleName())
                + " @" + ErrorLog.frame(error));
    }

    /** Neuhvaćen pad: upisuje se odmah (commit), jer proces posle toga nestaje. */
    public static synchronized void crash(Throwable error) {
        record("crash", error);
        save(true);
    }

    /** Upisuje ono što nije sačuvano (npr. kad se servis gasi). */
    public static synchronized void flush() {
        if (dirty) save(false);
    }

    public static synchronized List<ErrorLog.Entry> entries() {
        return log == null ? new ErrorLog().entries() : log.entries();
    }

    public static synchronized void clear() {
        if (log == null) return;
        log.clear();
        save(false);
    }

    private static void record(String operation, Throwable error) {
        if (log == null) return;
        long now;
        try {
            now = clock.getAsLong();
        } catch (Throwable ignored) {
            now = System.currentTimeMillis(); // samo za prikaz u dnevniku, ne za pravila
        }
        log.record(operation, error, now);
        dirty = true;
    }

    private static void save(boolean now) {
        if (prefs == null || log == null) return;
        try {
            SharedPreferences.Editor e = prefs.edit().putString(KEY, log.serialize());
            if (now) e.commit(); else e.apply();
            dirty = false;
            savedAt = SystemClock.elapsedRealtime();
        } catch (Throwable error) {
            Log.e("CuvarGuard", "diagnosticsSave:" + error.getClass().getSimpleName());
        }
    }
}
