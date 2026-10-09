package com.cuvar.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.EditText;
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
        t.setIncludeFontPadding(false);
        if (bold) {
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return t;
    }

    static TextView button(Context c, String label, boolean primary) {
        TextView t = text(c, label, 16, primary ? 0xFFFFFFFF : INK, true);
        t.setGravity(Gravity.CENTER);
        t.setAllCaps(false);
        t.setMinHeight(dp(c, 52));
        t.setMinimumWidth(dp(c, 52));
        float r = dp(c, 14);
        t.setBackground(primary ? pressable(ACCENT, ACCENT_DOWN, r) : pressable(SOFT, SOFT_DOWN, r));
        int p = dp(c, 15);
        t.setPadding(p, p, p, p);
        return t;
    }

    /** Mala oznaka stanja: obojen tekst na blagoj podlozi iste boje. */
    static TextView badge(Context c, String label, int color) {
        TextView t = text(c, label, 12, color, true);
        t.setBackground(round((color & 0x00FFFFFF) | 0x2E000000, dp(c, 10)));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        return t;
    }

    /** Naslov odeljka: mala slova razmaknuta, prigušena boja. */
    static TextView section(Context c, String label) {
        TextView t = text(c, label.toUpperCase(java.util.Locale.ROOT), 12, MUTED, true);
        t.setLetterSpacing(0.15f);
        t.setPadding(dp(c, 4), 0, 0, 0);
        return t;
    }

    static LinearLayout.LayoutParams wrap(Context c, int leftDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, leftDp);
        return lp;
    }

    /** "1 aplikacija", "3 aplikacije", "5 aplikacija" (srpska množina). */
    static String count(int n, String one, String few, String many) {
        int d = n % 10, dd = n % 100;
        if (d == 1 && dd != 11) return n + " " + one;
        if (d >= 2 && d <= 4 && (dd < 12 || dd > 14)) return n + " " + few;
        return n + " " + many;
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

    static final String[] TABS = {"Danas", "Pravila", "Statistika"};

    /** Donja traka sa karticama glavnog ekrana; onTab dobija redni broj tapnute kartice. */
    static LinearLayout bottomBar(Context c, int active, java.util.function.IntConsumer onTab) {
        LinearLayout bar = column(c);
        View line = new View(c);
        line.setBackgroundColor(LINE);
        bar.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout row = row(c);
        row.setBackgroundColor(CARD);
        row.setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4));
        row.setElevation(dp(c, 3));
        for (int i = 0; i < TABS.length; i++) {
            final int tab = i;
            LinearLayout cell = column(c);
            cell.setGravity(android.view.Gravity.CENTER);
            cell.setMinimumHeight(dp(c, 64));
            cell.setContentDescription(TABS[i] + (i == active ? ", izabrano" : ""));
            cell.setFocusable(true);
            int p = dp(c, 10);
            cell.setPadding(0, p, 0, p);
            View mark = new View(c);
            mark.setBackground(round(i == active ? ACCENT : 0x00000000, dp(c, 2)));
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(c, 28), dp(c, 4));
            mlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            cell.addView(mark, mlp);
            TextView t = text(c, TABS[i], 14, i == active ? ACCENT : MUTED, true);
            t.setGravity(android.view.Gravity.CENTER);
            cell.addView(t, fill(c, 6));
            int selectedBg = (ACCENT & 0x00FFFFFF) | 0x16000000;
            cell.setBackground(pressable(i == active ? selectedBg : CARD, SOFT, dp(c, 14)));
            cell.setSelected(i == active);
            if (i != active) cell.setOnClickListener(v -> onTab.accept(tab));
            row.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        bar.addView(row);
        return bar;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(round(CARD, dp(c, 18)));
        l.setElevation(dp(c, 2));
        l.setClipToOutline(true);
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

    /**
     * Boje sistemskih traka i razmak oko njih. Od Androida 15 ekran ide ispod statusne i navigacione trake
     * (ceo ekran), pa sadržaj dobija razmak tačno koliki su trake i tastatura.
     */
    @SuppressWarnings("deprecation")
    static void styleWindow(Activity a) {
        Window w = a.getWindow();
        w.getDecorView().setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT < 35) {
            w.setStatusBarColor(BG);
            w.setNavigationBarColor(BG);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ic = w.getInsetsController();
            if (ic != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ic.setSystemBarsAppearance(dark ? 0 : light, light);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(dark ? 0
                    : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        if (Build.VERSION.SDK_INT >= 35) {
            View content = a.findViewById(android.R.id.content);
            content.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(b.left, b.top, b.right, b.bottom);
                return WindowInsets.CONSUMED;
            });
        }
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

    /** Ne pretvara grešku u nulu: nula menja značenje pravila. */
    static Integer nonNegativeNumber(EditText field) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            if (value < 0) throw new NumberFormatException();
            field.setError(null);
            return value;
        } catch (Exception e) {
            field.setError("Unesi ceo broj od 0 do 2147483647");
            field.requestFocus();
            return null;
        }
    }
}
