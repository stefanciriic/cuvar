package com.cuvar.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Random;

/** Ekran igrice „Brojevi redom“ (tamni, kao blokada): na ekranu blokade i za probu iz Podešavanja. */
final class NumberGameView {
    private static final Random RANDOM = new Random();

    private NumberGameView() {
    }

    /** Proba iz Podešavanja: ista igrica preko celog ekrana, bez blokade i bez šifre. */
    static void practice(Activity a) {
        final Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(Ui.NIGHT);
        scroll.setFillViewport(true);
        LinearLayout box = Ui.column(a);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(a, 24);
        box.setPadding(p, p * 2, p, p);
        final LinearLayout area = Ui.column(a);
        box.addView(area, Ui.fill(a, 0));
        TextView close = button(a, "Zatvori");
        close.setOnClickListener(v -> d.dismiss());
        box.addView(close, Ui.fill(a, 24));
        scroll.addView(box);
        d.setContentView(scroll);
        final Handler h = new Handler(Looper.getMainLooper());
        final long[] started = {SystemClock.elapsedRealtime()};
        final Runnable[] won = new Runnable[1];
        won[0] = () -> {
            long s = (SystemClock.elapsedRealtime() - started[0] + 500) / 1000;
            area.removeAllViews();
            TextView t = Ui.text(a, "Uspelo je za " + s + " s!", 22, 0xFFFFFFFF, true);
            t.setGravity(Gravity.CENTER);
            area.addView(t, Ui.fill(a, 0));
            TextView n = Ui.text(a, "Na ekranu blokade bi sada išao unos dnevne šifre.", 14, Ui.NIGHT_MUTED, false);
            n.setGravity(Gravity.CENTER);
            area.addView(n, Ui.fill(a, 8));
            TextView again = button(a, "Igraj ponovo");
            again.setOnClickListener(v -> {
                started[0] = SystemClock.elapsedRealtime();
                show(a, h, area, won[0], null);
            });
            area.addView(again, Ui.fill(a, 16));
        };
        show(a, h, area, won[0], null);
        d.show();
    }

    private static TextView button(Context c, String label) {
        TextView b = Ui.text(c, label, 15, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.pressable(Ui.NIGHT_KEY, Ui.NIGHT_KEY_DOWN, Ui.dp(c, 14)));
        int ph = Ui.dp(c, 22), pv = Ui.dp(c, 13);
        b.setPadding(ph, pv, ph, pv);
        return b;
    }

    /** Igrica „Brojevi redom“ umesto pitanja: pobeda vodi dalje, greška ili isteklo vreme daje novu partiju. */
    static void show(final Context c, final Handler h, final LinearLayout area, final Runnable onPass, String notice) {
        show(c, h, area, onPass, notice, NumberGame.SIDE, NumberGame.TIME_MS);
    }

    /** Igrica sa zadatom veličinom mreže i vremenom (teža za svako sledeće otključavanje u danu). */
    static void show(final Context c, final Handler h, final LinearLayout area, final Runnable onPass, String notice,
                     final int side, final long timeMs) {
        area.removeAllViews();
        final NumberGame game = new NumberGame(RANDOM, SystemClock.elapsedRealtime(), side, timeMs);

        TextView head = Ui.text(c, "Tapni brojeve od 1 do " + game.size + " redom", 20, 0xFFFFFFFF, true);
        head.setGravity(Gravity.CENTER);
        area.addView(head, Ui.fill(c, 0));
        if (notice != null) {
            TextView n = Ui.text(c, notice, 14, Ui.NIGHT_ACCENT, false);
            n.setGravity(Gravity.CENTER);
            area.addView(n, Ui.fill(c, 8));
        }
        final TextView status = Ui.text(c, "", 15, Ui.NIGHT_MUTED, true);
        status.setGravity(Gravity.CENTER);
        area.addView(status, Ui.fill(c, 10));

        LinearLayout grid = Ui.column(c);
        int gap = Ui.dp(c, 3);
        for (int r = 0; r < side; r++) {
            LinearLayout row = Ui.row(c);
            for (int col = 0; col < side; col++) {
                final int number = game.cells[r * side + col];
                final TextView cell = Ui.text(c, String.valueOf(number), 18, 0xFFFFFFFF, true);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(Ui.pressable(Ui.NIGHT_KEY, Ui.NIGHT_KEY_DOWN, Ui.dp(c, 10)));
                cell.setOnClickListener(v -> {
                    NumberGame.Tap t = game.tap(number, SystemClock.elapsedRealtime());
                    if (t == NumberGame.Tap.OK) {
                        cell.setBackground(null);
                        cell.setTextColor(Ui.NIGHT_MUTED);
                        status.setText(numbersStatus(game));
                    } else if (t == NumberGame.Tap.DONE) {
                        onPass.run();
                    } else if (t == NumberGame.Tap.WRONG) {
                        show(c, h, area, onPass, "Pogrešan broj. Kreni ispočetka.", side, timeMs);
                    } else if (t == NumberGame.Tap.LATE) {
                        show(c, h, area, onPass, "Vreme je isteklo. Kreni ispočetka.", side, timeMs);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, side > 5 ? 46 : 54), 1f);
                lp.setMargins(gap, gap, gap, gap);
                row.addView(cell, lp);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        area.addView(grid, Ui.fill(c, 12));
        status.setText(numbersStatus(game));

        // Odbrojavanje dok je igrica na ekranu; posle isteka nova partija.
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (game.over() || grid.getParent() != area) return;
                if (game.leftMs(SystemClock.elapsedRealtime()) == 0) {
                    // Zatvoren ekran blokade: samo stani.
                    if (area.isAttachedToWindow()) show(c, h, area, onPass, "Vreme je isteklo. Kreni ispočetka.", side, timeMs);
                    return;
                }
                status.setText(numbersStatus(game));
                h.postDelayed(this, 250);
            }
        }, 250);
    }

    private static String numbersStatus(NumberGame game) {
        long s = (game.leftMs(SystemClock.elapsedRealtime()) + 999) / 1000;
        return "Sledeći broj: " + game.next() + "   ·   " + s + " s";
    }
}
