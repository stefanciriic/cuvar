package com.cuvar.app;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tastatura za PIN i za brojčane odgovore. Radi i u prozoru za blokadu, gde obična tastatura nije dostupna. */
final class PinPad extends LinearLayout {

    interface Listener {
        void onSubmit(String pin);
    }

    private static final String BACK = "←";
    private static final String OK = "OK";

    private final StringBuilder buf = new StringBuilder();
    private final TextView dots;
    private final TextView msg;
    private Listener listener;
    private boolean showDigits;

    PinPad(Context c, boolean dark) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        int fg = dark ? 0xFFFFFFFF : Ui.INK;
        int key = dark ? Ui.NIGHT_KEY : Ui.SOFT;
        int keyDown = dark ? Ui.NIGHT_KEY_DOWN : Ui.SOFT_DOWN;

        dots = Ui.text(c, "", 26, fg, true);
        dots.setGravity(Gravity.CENTER);
        dots.setLetterSpacing(0.35f);
        addView(dots, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        msg = Ui.text(c, " ", 14, dark ? Ui.NIGHT_ACCENT : Ui.ACCENT, false);
        msg.setGravity(Gravity.CENTER);
        LayoutParams mlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(c, 6);
        mlp.bottomMargin = Ui.dp(c, 6);
        addView(msg, mlp);

        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", BACK, "0", OK};
        int size = Ui.dp(c, 66);
        int gap = Ui.dp(c, 7);
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int k = 0; k < 3; k++) {
                final String label = keys[r * 3 + k];
                boolean small = OK.equals(label);
                TextView b = Ui.text(c, label, small ? 17 : 24, small ? (dark ? Ui.NIGHT_ACCENT : Ui.ACCENT) : fg, true);
                b.setGravity(Gravity.CENTER);
                b.setBackground(Ui.pressable(key, keyDown, size / 2f));
                b.setOnClickListener(v -> press(label));
                LayoutParams lp = new LayoutParams(size, size);
                lp.setMargins(gap, gap, gap, gap);
                row.addView(b, lp);
            }
            addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        render();
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** Prikazuje otkucane cifre umesto tačkica (za odgovor na pitanje). */
    void showDigits() {
        showDigits = true;
        render();
    }

    void setMessage(String s) {
        msg.setText(s == null || s.isEmpty() ? " " : s);
    }

    void clear() {
        buf.setLength(0);
        render();
    }

    private void press(String label) {
        if (OK.equals(label)) {
            if (listener != null && buf.length() > 0) {
                listener.onSubmit(buf.toString());
            }
            return;
        }
        if (BACK.equals(label)) {
            if (buf.length() > 0) {
                buf.setLength(buf.length() - 1);
            }
        } else if (buf.length() < 8) {
            buf.append(label);
        }
        setMessage(null);
        render();
    }

    private void render() {
        if (buf.length() == 0) {
            dots.setText("––––");
            dots.setAlpha(0.35f);
            return;
        }
        if (showDigits) {
            dots.setText(buf.toString());
            dots.setAlpha(1f);
            return;
        }
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < buf.length(); i++) {
            s.append('●');
        }
        dots.setText(s);
        dots.setAlpha(1f);
    }
}
