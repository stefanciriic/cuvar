package com.cuvar.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Prozor u stilu aplikacije umesto belog sistemskog dijaloga: zaobljena kartica pri dnu ekrana,
 * naslov, sadržaj i dugmad iste boje kao ostatak Čuvara.
 */
final class Sheet {

    /** Akcija dugmeta; vraća true ako prozor treba da se zatvori (false npr. kad unos nije ispravan). */
    interface Action {
        boolean run();
    }

    private final Context c;
    private final Dialog dialog;
    private final LinearLayout box;
    private final LinearLayout buttons;
    private final LinearLayout body;
    private TextView danger;

    Sheet(Context c, String title) {
        this.c = c;
        dialog = new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        box = Ui.column(c);
        box.setBackground(Ui.round(Ui.BG, Ui.dp(c, 26)));
        int p = Ui.dp(c, 22);
        box.setPadding(p, Ui.dp(c, 12), p, p);

        View grip = new View(c);
        grip.setBackground(Ui.round(Ui.SOFT_DOWN, Ui.dp(c, 3)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 5));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(grip, glp);

        box.addView(Ui.text(c, title, 22, Ui.INK, true), Ui.fill(c, 14));

        body = Ui.column(c);
        // Dugačak sadržaj (npr. lista aplikacija) se skroluje, a dugmad ostaju vidljiva.
        final int maxHeight = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.62f);
        ScrollView scroll = new ScrollView(c) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int available = maxHeight;
                if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
                    // Roditelj je već odbio naslov i padding. Dugmad dolaze posle sadržaja,
                    // zato njihov prostor rezervišemo pre merenja skrolujućeg dela.
                    available = Math.min(available, Math.max(0,
                            MeasureSpec.getSize(heightSpec) - footerHeight(widthSpec)));
                }
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(body);
        box.addView(scroll, Ui.fill(c, 0));

        buttons = Ui.row(c);
        box.addView(buttons, Ui.fill(c, 18));

        FrameLayout frame = new FrameLayout(c);
        frame.addView(box, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        dialog.setContentView(frame);
    }

    private int footerHeight(int widthSpec) {
        int total = measuredHeightWithMargins(buttons, widthSpec);
        if (danger != null) total += measuredHeightWithMargins(danger, widthSpec);
        return total;
    }

    private int measuredHeightWithMargins(View view, int widthSpec) {
        view.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) view.getLayoutParams();
        return view.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
    }

    /** Kratko objašnjenje ispod naslova. */
    Sheet message(CharSequence text) {
        body.addView(Ui.text(c, text, 15, Ui.MUTED, false), Ui.fill(c, 6));
        return this;
    }

    Sheet view(View v) {
        body.addView(v, Ui.fill(c, 12));
        return this;
    }

    /** Glavno dugme (narandžasto). */
    Sheet primary(String label, Action a) {
        return addButton(label, true, a);
    }

    /** Sporedno dugme, npr. Otkaži. Bez akcije samo zatvara prozor. */
    Sheet secondary(String label, Action a) {
        return addButton(label, false, a);
    }

    /** Opasna radnja (Obriši, Ukloni) kao tekst iznad dugmadi. */
    Sheet danger(String label, Action a) {
        danger = Ui.text(c, label, 15, Ui.ACCENT, true);
        danger.setGravity(Gravity.CENTER);
        int p = Ui.dp(c, 10);
        danger.setPadding(p, p, p, p);
        danger.setOnClickListener(v -> {
            if (a == null || a.run()) {
                dialog.dismiss();
            }
        });
        box.addView(danger, box.indexOfChild(buttons), Ui.fill(c, 10));
        return this;
    }

    private Sheet addButton(String label, boolean primary, Action a) {
        TextView b = Ui.button(c, label, primary);
        b.setOnClickListener(v -> {
            if (a == null || a.run()) {
                dialog.dismiss();
            }
        });
        // Sporedno dugme ide levo, glavno desno.
        buttons.addView(b, primary ? buttons.getChildCount() : 0,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        for (int i = 0; i < buttons.getChildCount(); i++) {
            ((LinearLayout.LayoutParams) buttons.getChildAt(i).getLayoutParams()).leftMargin =
                    i == 0 ? 0 : Ui.dp(c, 10);
        }
        return this;
    }

    Dialog show() {
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            int m = Ui.dp(c, 10);
            w.getDecorView().setPadding(m, m, m, m);
            w.setDimAmount(0.45f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        return dialog;
    }

    void dismiss() {
        dialog.dismiss();
    }

    // ---------- Polja u stilu aplikacije ----------

    /** Polje za unos: bela zaobljena kartica, bez sive linije ispod. */
    static EditText input(Context c, String hint, boolean number) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(Ui.INK);
        e.setHintTextColor(Ui.MUTED);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        GradientDrawable bg = Ui.round(Ui.CARD, Ui.dp(c, 14));
        bg.setStroke(Ui.dp(c, 1), Ui.LINE);
        e.setBackground(bg);
        int p = Ui.dp(c, 14);
        e.setPadding(p, p, p, p);
        return e;
    }

    /** Natpis iznad polja. */
    static TextView label(Context c, String text) {
        return Ui.text(c, text, 14, Ui.MUTED, true);
    }
}
