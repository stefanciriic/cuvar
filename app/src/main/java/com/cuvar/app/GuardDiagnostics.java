package com.cuvar.app;

import android.os.SystemClock;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/** Local, rate-limited diagnostics. Never log exception messages, URLs, packages or screen content. */
final class GuardDiagnostics {
    private static final Map<String, Long> LAST = new HashMap<>();

    private GuardDiagnostics() { }

    static synchronized void report(String operation, Throwable error) {
        long now = SystemClock.elapsedRealtime();
        Long last = LAST.get(operation);
        if (last != null && now - last < 60000L) return;
        LAST.put(operation, now);
        Log.e("CuvarGuard", operation + ": " + error.getClass().getSimpleName());
    }
}
