package com.cuvar.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Red sa ikonicom, nazivom i okruglom kvačicom; zamenjuje obični sistemski CheckBox. */
final class CheckRow extends LinearLayout implements Checkable {

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
        setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(title + (sub == null ? "" : ". " + sub));
        int p = Ui.dp(c, 12);
        setPadding(p, p, p, p);
        setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(c, 16)));

        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = Ui.dp(c, 36);
            LayoutParams ilp = new LayoutParams(size, size);
            ilp.rightMargin = Ui.dp(c, 12);
            addView(iv, ilp);
        }

        LinearLayout texts = Ui.column(c);
        texts.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        TextView t = Ui.text(c, title, 16, Ui.INK, false);
        t.setSingleLine(true);
        texts.addView(t);
        if (sub != null) {
            texts.addView(Ui.text(c, sub, 13, Ui.MUTED, false));
        }
        addView(texts, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        mark = Ui.text(c, "", 15, 0xFFFFFFFF, true);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
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

    @Override public boolean isChecked() {
        return checked;
    }

    @Override public void setChecked(boolean on) {
        boolean changed = checked != on;
        checked = on;
        render();
        if (changed) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override public void toggle() {
        setChecked(!checked);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.CheckBox.class.getName());
        info.setCheckable(true);
        info.setChecked(checked);
    }

    @Override public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(android.widget.CheckBox.class.getName());
        event.setChecked(checked);
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
