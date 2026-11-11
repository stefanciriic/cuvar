package com.cuvar.app.unlock;

import com.cuvar.app.ui.Safe;
import com.cuvar.app.ui.Ui;

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
public final class NumberGameView {
    private static final Random RANDOM = new Random();

    private NumberGameView() {
    }

    /** Prikaz igrice u zadatom prostoru; posle pobede se zove onPass. */
    public interface Game {
        void show(Context c, Handler h, LinearLayout area, Runnable onPass);
    }

    /** Proba iz Podešavanja: ista igrica preko celog ekrana, bez blokade i bez šifre. */
    public static void practice(Activity a) {
        practice(a, (c, h, area, onPass) -> show(c, h, area, onPass, null));
    }

    /** Proba bilo koje igrice (brojevi ili kratke igrice). */
    public static void practice(Activity a, final Game game) {
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
        close.setOnClickListener(Safe.click("numbersClose", v -> d.dismiss()));
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
            again.setOnClickListener(Safe.click("numbersAgain", v -> {
                started[0] = SystemClock.elapsedRealtime();
                game.show(a, h, area, won[0]);
            }));
            area.addView(again, Ui.fill(a, 16));
        };
        game.show(a, h, area, won[0]);
        d.show();
    }

    static TextView button(Context c, String label) {
        TextView b = Ui.text(c, label, 15, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.pressable(Ui.NIGHT_KEY, Ui.NIGHT_KEY_DOWN, Ui.dp(c, 14)));
        int ph = Ui.dp(c, 22), pv = Ui.dp(c, 13);
        b.setPadding(ph, pv, ph, pv);
        return b;
    }

    /** Igrica „Brojevi redom“ umesto pitanja: pobeda vodi dalje, greška ili isteklo vreme daje novu partiju. */
    public static void show(final Context c, final Handler h, final LinearLayout area, final Runnable onPass, String notice) {
        show(c, h, area, onPass, notice, NumberGame.SIDE, NumberGame.TIME_MS);
    }

    /** Poraz u igrici (pogrešan tap ili isteklo vreme); late je true kad je isteklo vreme. */
    public interface Lost {
        void lost(boolean late);
    }

    /** Igrica sa zadatom veličinom mreže i vremenom (teža za svako sledeće otključavanje u danu). */
    public static void show(final Context c, final Handler h, final LinearLayout area, final Runnable onPass, String notice,
                     final int side, final long timeMs) {
        show(c, h, area, onPass, notice, side, timeMs, null);
    }

    /** Kao gore; kad lost nije null, poraz zove lost umesto nove partije iste igrice. */
    public static void show(final Context c, final Handler h, final LinearLayout area, final Runnable onPass, String notice,
                     final int side, final long timeMs, final Lost lost) {
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
                cell.setOnClickListener(Safe.click("numbersCell", v -> {
                    NumberGame.Tap t = game.tap(number, SystemClock.elapsedRealtime());
                    if (t == NumberGame.Tap.OK) {
                        cell.setBackground(null);
                        cell.setTextColor(Ui.NIGHT_MUTED);
                        status.setText(numbersStatus(game));
                    } else if (t == NumberGame.Tap.DONE) {
                        onPass.run();
                    } else if (t == NumberGame.Tap.WRONG) {
                        if (lost != null) lost.lost(false);
                        else show(c, h, area, onPass, "Pogrešan broj. Kreni ispočetka.", side, timeMs, null);
                    } else if (t == NumberGame.Tap.LATE) {
                        if (lost != null) lost.lost(true);
                        else show(c, h, area, onPass, "Vreme je isteklo. Kreni ispočetka.", side, timeMs, null);
                    }
                }));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, side > 5 ? 46 : 54), 1f);
                lp.setMargins(gap, gap, gap, gap);
                row.addView(cell, lp);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        area.addView(grid, Ui.fill(c, 12));
        status.setText(numbersStatus(game));

        // Odbrojavanje dok je igrica na ekranu; posle isteka nova partija (ili lost).
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Safe.run("numbersTimer", this::tick);
            }

            private void tick() {
                if (game.over() || grid.getParent() != area) return;
                if (game.leftMs(SystemClock.elapsedRealtime()) == 0) {
                    // Zatvoren ekran blokade: samo stani.
                    if (!area.isAttachedToWindow()) return;
                    if (lost != null) lost.lost(true);
                    else show(c, h, area, onPass, "Vreme je isteklo. Kreni ispočetka.", side, timeMs, null);
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
