package com.cuvar.app.unlock;

import com.cuvar.app.ui.Safe;
import com.cuvar.app.ui.Ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/** Ekran kratkih igrica (Strelice, Boje, Uljez, Pamćenje, Niz): na ekranu blokade i za probu iz Podešavanja. */
public final class QuickGameView {
    private static final Random RANDOM = new Random();

    private QuickGameView() {
    }

    /** Kratka igrica za izbor iz podešavanja (Ui.CHALLENGE_...); null za pitalice, brojeve i nasumično. */
    public static QuickGame.Kind kind(String challenge) {
        if (Ui.CHALLENGE_ARROWS.equals(challenge)) return QuickGame.Kind.ARROW;
        if (Ui.CHALLENGE_COLOR.equals(challenge)) return QuickGame.Kind.COLOR;
        if (Ui.CHALLENGE_ODD.equals(challenge)) return QuickGame.Kind.ODD;
        if (Ui.CHALLENGE_MEMORY.equals(challenge)) return QuickGame.Kind.MEMORY;
        if (Ui.CHALLENGE_SEQUENCE.equals(challenge)) return QuickGame.Kind.SEQUENCE;
        return null;
    }

    /** Naslov igrice (za podešavanja). */
    public static String name(QuickGame.Kind kind) {
        if (kind == QuickGame.Kind.ARROW) return "Igrica: strelice";
        if (kind == QuickGame.Kind.COLOR) return "Igrica: boje";
        if (kind == QuickGame.Kind.MEMORY) return "Igrica: pamćenje";
        if (kind == QuickGame.Kind.SEQUENCE) return "Igrica: niz";
        return "Igrica: uljez";
    }

    public static void practice(Activity a, final QuickGame.Kind kind) {
        NumberGameView.practice(a, (c, h, area, onPass) -> show(c, h, area, onPass, null, kind, 0));
    }

    /** Nova partija; greška ili isteklo vreme daje novu partiju istog nivoa. */
    public static void show(final Context c, final Handler h, final LinearLayout area, final Runnable onPass,
                            String notice, final QuickGame.Kind kind, final int level) {
        show(c, h, area, onPass, notice, kind, level, null);
    }

    /** Kao gore; kad lost nije null, poraz zove lost umesto nove partije iste igrice. */
    public static void show(final Context c, final Handler h, final LinearLayout area, final Runnable onPass,
                            String notice, final QuickGame.Kind kind, final int level, final NumberGameView.Lost lost) {
        final QuickGame game = new QuickGame(kind, level, RANDOM, SystemClock.elapsedRealtime());
        final TextView status = Ui.text(c, "", 15, Ui.NIGHT_MUTED, true);
        status.setGravity(Gravity.CENTER);
        final LinearLayout board = Ui.column(c);
        area.removeAllViews();

        String title = kind == QuickGame.Kind.ARROW ? "Na koju stranu pokazuje srednja strelica?"
                : kind == QuickGame.Kind.COLOR ? "Tapni boju kojom je reč napisana"
                : kind == QuickGame.Kind.MEMORY ? "Zapamti osvetljena polja, pa ih tapni"
                : kind == QuickGame.Kind.SEQUENCE ? "Zapamti redosled, pa tapni polja istim redom"
                : "Tapni znak koji se razlikuje";
        TextView head = Ui.text(c, title, 20, 0xFFFFFFFF, true);
        head.setGravity(Gravity.CENTER);
        area.addView(head, Ui.fill(c, 0));
        if (notice != null) {
            TextView n = Ui.text(c, notice, 14, Ui.NIGHT_ACCENT, false);
            n.setGravity(Gravity.CENTER);
            area.addView(n, Ui.fill(c, 8));
        }
        area.addView(status, Ui.fill(c, 10));
        area.addView(board, Ui.fill(c, 12));

        final Runnable[] draw = new Runnable[1];
        // Dok se polja pokazuju (Pamćenje, Niz), tapovi se ne broje.
        final boolean[] showing = {false};
        draw[0] = () -> {
            final QuickGame.Round round = game.round();
            final View[][] cells = new View[1][];
            cells[0] = drawRound(c, board, round, option -> {
                if (showing[0]) return;
                NumberGame.Tap t = game.tap(option, SystemClock.elapsedRealtime());
                if (t == NumberGame.Tap.OK && game.round() == round) {
                    light(c, cells[0][option], true);
                } else if (t == NumberGame.Tap.OK) {
                    draw[0].run();
                } else if (t == NumberGame.Tap.DONE) {
                    onPass.run();
                } else if (t == NumberGame.Tap.WRONG) {
                    if (lost != null) lost.lost(false);
                    else show(c, h, area, onPass, "Pogrešno. Kreni ispočetka.", kind, level, null);
                } else if (t == NumberGame.Tap.LATE) {
                    if (lost != null) lost.lost(true);
                    else show(c, h, area, onPass, "Vreme je isteklo. Kreni ispočetka.", kind, level, null);
                }
            });
            status.setText(status(game));
            if (round.targets != null) reveal(c, h, round, cells[0], showing, () -> game.round() == round && !game.over());
        };
        draw[0].run();

        // Odbrojavanje dok je igrica na ekranu; posle isteka nova partija (ili lost).
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                Safe.run("quickTimer", this::tick);
            }

            private void tick() {
                if (game.over() || board.getParent() != area) return;
                if (game.leftMs(SystemClock.elapsedRealtime()) == 0) {
                    if (!area.isAttachedToWindow()) return;
                    if (lost != null) lost.lost(true);
                    else show(c, h, area, onPass, "Vreme je isteklo. Kreni ispočetka.", kind, level, null);
                    return;
                }
                status.setText(status(game));
                h.postDelayed(this, 250);
            }
        }, 250);
    }

    private interface Pick {
        void pick(int option);
    }

    private interface Still {
        boolean still();
    }

    /** Pokaže polja koja treba zapamtiti (sva odjednom ili jedno po jedno), pa ih sakrije. */
    private static void reveal(final Context c, final Handler h, final QuickGame.Round r, final View[] cells,
                               final boolean[] showing, final Still still) {
        showing[0] = true;
        final int n = r.targets.length;
        if (!r.ordered) {
            for (int t : r.targets) light(c, cells[t], true);
            h.postDelayed(() -> Safe.run("quickHide", () -> {
                if (!still.still()) return;
                for (int t : r.targets) light(c, cells[t], false);
                showing[0] = false;
            }), 800L + 150L * n);
            return;
        }
        final long on = 450L, gap = 150L;
        for (int i = 0; i < n; i++) {
            final int idx = r.targets[i];
            final boolean last = i == n - 1;
            h.postDelayed(() -> Safe.run("quickFlash", () -> {
                if (still.still()) light(c, cells[idx], true);
            }), 400L + i * (on + gap));
            h.postDelayed(() -> Safe.run("quickFlash", () -> {
                if (!still.still()) return;
                light(c, cells[idx], false);
                if (last) showing[0] = false;
            }), 400L + i * (on + gap) + on);
        }
    }

    private static void light(Context c, View cell, boolean on) {
        cell.setBackground(on ? Ui.pressable(Ui.NIGHT_ACCENT, Ui.NIGHT_ACCENT, Ui.dp(c, 12))
                : Ui.pressable(Ui.NIGHT_KEY, Ui.NIGHT_KEY_DOWN, Ui.dp(c, 12)));
    }

    private static View[] drawRound(Context c, LinearLayout board, QuickGame.Round r, Pick pick) {
        board.removeAllViews();
        View[] cells = new View[r.count()];
        int gap = Ui.dp(c, 4);
        int perRow;
        int height;
        if (r.prompt != null) {
            TextView p = Ui.text(c, r.prompt, r.options != null ? 34 : 34, r.options != null ? 0xFFFFFFFF : r.ink, true);
            p.setGravity(Gravity.CENTER);
            board.addView(p, Ui.fill(c, 8));
        }
        if (r.options == null) {
            perRow = 3;
            height = 64;
        } else if (r.side > 0) {
            perRow = r.side;
            height = r.side > 4 ? 52 : r.targets != null ? 72 : 60;
        } else {
            perRow = 2;
            height = 64;
        }
        LinearLayout row = null;
        for (int i = 0; i < r.count(); i++) {
            if (i % perRow == 0) {
                row = Ui.row(c);
                board.addView(row, Ui.fill(c, i == 0 ? 16 : 0));
            }
            final int option = i;
            View cell;
            if (r.options != null) {
                TextView t = Ui.text(c, r.options[i], r.side > 0 ? 24 : 26, 0xFFFFFFFF, true);
                t.setGravity(Gravity.CENTER);
                t.setBackground(Ui.pressable(Ui.NIGHT_KEY, Ui.NIGHT_KEY_DOWN, Ui.dp(c, 12)));
                cell = t;
            } else {
                cell = new View(c);
                GradientDrawable d = new GradientDrawable();
                d.setColor(r.colors[i]);
                d.setCornerRadius(Ui.dp(c, 14));
                cell.setBackground(d);
            }
            cell.setOnClickListener(Safe.click("quickCell", v -> pick.pick(option)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, height), 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(cell, lp);
            cells[i] = cell;
        }
        return cells;
    }

    private static String status(QuickGame game) {
        long s = (game.leftMs(SystemClock.elapsedRealtime()) + 999) / 1000;
        return "Zadatak " + (game.done() + 1) + " od " + game.rounds + "   ·   " + s + " s";
    }
}
