package com.cuvar.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Red sa ikonicom, nazivom i okruglom kvačicom; zamenjuje obični sistemski CheckBox. */
final class CheckRow extends LinearLayout {

    interface Listener {
        void onChanged(boolean checked);
    }

    private final TextView mark;
    private boolean checked;
    private Listener listener;

    CheckRow(Context c, Drawable icon, CharSequence title, CharSequence sub) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 12);
        setPadding(p, p, p, p);
        setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(c, 16)));

        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            int size = Ui.dp(c, 36);
            LayoutParams ilp = new LayoutParams(size, size);
            ilp.rightMargin = Ui.dp(c, 12);
            addView(iv, ilp);
        }

        LinearLayout texts = Ui.column(c);
        TextView t = Ui.text(c, title, 16, Ui.INK, false);
        t.setSingleLine(true);
        texts.addView(t);
        if (sub != null) {
            texts.addView(Ui.text(c, sub, 13, Ui.MUTED, false));
        }
        addView(texts, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        mark = Ui.text(c, "", 15, 0xFFFFFFFF, true);
        mark.setGravity(Gravity.CENTER);
        int m = Ui.dp(c, 26);
        LayoutParams mlp = new LayoutParams(m, m);
        mlp.leftMargin = Ui.dp(c, 10);
        addView(mark, mlp);

        setOnClickListener(v -> {
            setChecked(!checked);
            if (listener != null) {
                listener.onChanged(checked);
            }
        });
        render();
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean on) {
        checked = on;
        render();
    }

    private void render() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (checked) {
            g.setColor(Ui.ACCENT);
            mark.setText("✓");
        } else {
            g.setColor(Ui.CARD);
            g.setStroke(Ui.dp(getContext(), 2), Ui.SOFT_DOWN);
            mark.setText("");
        }
        mark.setBackground(g);
    }
}
