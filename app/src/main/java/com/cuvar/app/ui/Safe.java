package com.cuvar.app.ui;

import android.view.View;

import com.cuvar.app.guard.GuardDiagnostics;

/**
 * Klik i zakazan posao koji ne smeju da obore proces: greška se prijavi u dnevnik, a Čuvar radi dalje.
 * Ekran blokade radi u procesu servisa, pa bi pad u pitalici ili PIN-u ugasio i zaštitu.
 */
public final class Safe {
    private Safe() {
    }

    public static View.OnClickListener click(String where, View.OnClickListener click) {
        return v -> {
            try {
                click.onClick(v);
            } catch (Throwable error) {
                GuardDiagnostics.report(where, error);
            }
        };
    }

    /** Klik sa rezervom: ako posao pukne, izvrši se fallback (npr. izlaz na početni ekran). */
    public static View.OnClickListener click(String where, View.OnClickListener click, Runnable fallback) {
        return v -> {
            try {
                click.onClick(v);
            } catch (Throwable error) {
                GuardDiagnostics.report(where, error);
                run(where + "Fallback", fallback);
            }
        };
    }

    /** Izvrši posao odmah; greška se prijavi. */
    public static void run(String where, Runnable work) {
        try {
            if (work != null) work.run();
        } catch (Throwable error) {
            GuardDiagnostics.report(where, error);
        }
    }

    /** Posao za Handler (postDelayed): isto kao run, ali kao Runnable. */
    public static Runnable task(String where, Runnable work) {
        return () -> run(where, work);
    }
}
