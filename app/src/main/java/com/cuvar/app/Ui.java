package com.cuvar.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Boje i mali pomoćnici za pravljenje ekrana bez XML-a. */
final class Ui {
    static final int BG = 0xFFF3EFE7;
    static final int CARD = 0xFFFFFFFF;
    static final int INK = 0xFF1D2433;
    static final int MUTED = 0xFF6B7280;
    static final int ACCENT = 0xFFD9480F;
    static final int ACCENT_DOWN = 0xFFB23A0A;
    static final int SOFT = 0xFFE9E3D6;
    static final int SOFT_DOWN = 0xFFD9D1C0;
    static final int LINE = 0xFFE5E0D5;
    static final int NIGHT = 0xFF12161F;
    static final int NIGHT_KEY = 0xFF232A38;
    static final int NIGHT_KEY_DOWN = 0xFF38425A;
    static final int NIGHT_MUTED = 0xFFB6BDCB;
    static final int NIGHT_ACCENT = 0xFFFF8A4C;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    static Drawable pressable(int normal, int pressed, float radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(pressed, radius));
        s.addState(new int[]{}, round(normal, radius));
        return s;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return t;
    }

    static TextView button(Context c, String label, boolean primary) {
        TextView t = text(c, label, 16, primary ? 0xFFFFFFFF : INK, true);
        t.setGravity(Gravity.CENTER);
        float r = dp(c, 14);
        t.setBackground(primary ? pressable(ACCENT, ACCENT_DOWN, r) : pressable(SOFT, SOFT_DOWN, r));
        int p = dp(c, 15);
        t.setPadding(p, p, p, p);
        return t;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(round(CARD, dp(c, 18)));
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        return l;
    }

    /** Puna širina, visina po sadržaju, sa razmakom odozgo. */
    static LinearLayout.LayoutParams fill(Context c, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static void styleWindow(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    static String fmt(long ms) {
        long min = ms / 60000L;
        if (min >= 60) {
            return (min / 60) + " h " + (min % 60) + " min";
        }
        if (min >= 1) {
            return min + " min";
        }
        return (ms / 1000L) + " s";
    }

    static int parseInt(String s) {
        try {
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (Exception e) {
            return 0;
        }
    }
}
