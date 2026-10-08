package com.cuvar.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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
    // Boje ekrana aplikacije; theme() ih postavlja na svetle ili tamne pre crtanja svakog ekrana.
    static int BG;
    static int CARD;
    static int INK;
    static int MUTED;
    static int ACCENT;
    static int ACCENT_DOWN;
    static int SOFT;
    static int SOFT_DOWN;
    static int LINE;
    static boolean dark;

    static {
        palette(false);
    }

    // Ekran blokade je uvek taman, bez obzira na izabranu temu.
    static final int NIGHT = 0xFF12161F;
    static final int NIGHT_KEY = 0xFF232A38;
    static final int NIGHT_KEY_DOWN = 0xFF38425A;
    static final int NIGHT_MUTED = 0xFFB6BDCB;
    static final int NIGHT_ACCENT = 0xFFFF8A4C;

    /** Izbor teme u podešavanjima. */
    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;
    static final String[] THEME_NAMES = {"Kao telefon", "Svetla", "Tamna"};

    private Ui() {
    }

    private static void palette(boolean night) {
        dark = night;
        if (night) {
            BG = 0xFF12151C;
            CARD = 0xFF1D222D;
            INK = 0xFFECEEF2;
            MUTED = 0xFF9AA3B2;
            ACCENT = 0xFFF26B2A;
            ACCENT_DOWN = 0xFFC9551E;
            SOFT = 0xFF2A303D;
            SOFT_DOWN = 0xFF3A4252;
            LINE = 0xFF2E3442;
        } else {
            BG = 0xFFF3EFE7;
            CARD = 0xFFFFFFFF;
            INK = 0xFF1D2433;
            MUTED = 0xFF6B7280;
            ACCENT = 0xFFD9480F;
            ACCENT_DOWN = 0xFFB23A0A;
            SOFT = 0xFFE9E3D6;
            SOFT_DOWN = 0xFFD9D1C0;
            LINE = 0xFFE5E0D5;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("ui", Context.MODE_PRIVATE);
    }

    static int themeChoice(Context c) {
        return prefs(c).getInt("theme", THEME_SYSTEM);
    }

    static void setThemeChoice(Context c, int choice) {
        prefs(c).edit().putInt("theme", choice).apply();
    }

    /** Da li ekran treba da bude taman: po izboru u podešavanjima ili po temi telefona. */
    static boolean wantsDark(Context c) {
        int choice = themeChoice(c);
        if (choice == THEME_SYSTEM) {
            int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        }
        return choice == THEME_DARK;
    }

    /** Poziva se na početku onCreate, pre super.onCreate: bira boje i sistemsku temu ekrana. */
    static void theme(Activity a) {
        palette(wantsDark(a));
        a.setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
    }

    /** Stil za sistemske dijaloge (npr. biranje vremena) u bojama trenutne teme. */
    static int dialogStyle() {
        return dark ? R.style.CuvarDialogDark : R.style.CuvarDialog;
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
        w.getDecorView().setSystemUiVisibility(dark ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
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
