package com.cuvar.app;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Builds the short pause screen shown before opening a guarded app. */
final class PauseOverlayContent {
    interface Actions {
        boolean isVisible(View view);
        String appLabel(String packageName);
        void close();
        void continueToApp();
    }

    private final Context context;
    private final Handler handler;
    private final Store store;
    private final Actions actions;

    PauseOverlayContent(Context context, Handler handler, Store store, Actions actions) {
        this.context = context;
        this.handler = handler;
        this.store = store;
        this.actions = actions;
    }

    View build(final String key) {
        String pkg = key.substring(4);
        String name = actions.appLabel(pkg);
        final long shownAt = SystemClock.elapsedRealtime();

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Ui.NIGHT);
        scroll.setFillViewport(true);
        scroll.setClickable(true);
        LinearLayout box = Ui.column(context);
        box.setGravity(Gravity.CENTER);
        int padding = Ui.dp(context, 24);
        box.setPadding(padding, padding, padding, padding);

        TextView eyebrow = Ui.text(context, "ČUVAR", 12, Ui.NIGHT_ACCENT, true);
        eyebrow.setLetterSpacing(0.25f);
        eyebrow.setGravity(Gravity.CENTER);
        box.addView(eyebrow, Ui.fill(context, 0));

        TextView title = Ui.text(context, "Zastani na trenutak", 25, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title, Ui.fill(context, 10));

        StringBuilder message = new StringBuilder("Otvaraš ").append(name).append(". Danas si ga koristio ")
                .append(Ui.fmt(store.usedShared(pkg)));
        int limit = store.appLimitNow(pkg);
        if (limit > 0) message.append(" od ").append(limit).append(" min");
        int opens = store.appOpensNow(pkg);
        if (opens > 0) {
            message.append(", a otvorio ").append(Ui.count(store.opensToday(key), "put", "puta", "puta"))
                    .append(" od ").append(opens);
        }
        message.append(". Da li ti zaista treba sada?");
        TextView detail = Ui.text(context, message.toString(), 15, Ui.NIGHT_MUTED, false);
        detail.setGravity(Gravity.CENTER);
        box.addView(detail, Ui.fill(context, 8));

        TextView close = button("Zatvori");
        close.setTextSize(17);
        close.setBackground(Ui.pressable(Ui.ACCENT, Ui.ACCENT_DOWN, Ui.dp(context, 14)));
        close.setOnClickListener(view -> actions.close());
        box.addView(close, Ui.fill(context, 24));

        final TextView go = button("");
        go.setTextColor(Ui.NIGHT_MUTED);
        go.setBackground(null);
        go.setEnabled(false);
        box.addView(go, Ui.fill(context, 16));
        Runnable countdown = new Runnable() {
            @Override public void run() {
                if (!actions.isVisible(scroll)) return;
                long left = GuardService.PAUSE_SECONDS * 1000L - (SystemClock.elapsedRealtime() - shownAt);
                if (left > 0) {
                    go.setText("Nastavi za " + ((left + 999L) / 1000L) + " s");
                    handler.postDelayed(this, 250L);
                } else {
                    go.setText("Nastavi u " + name);
                    go.setEnabled(true);
                }
            }
        };
        go.setText("Nastavi za " + GuardService.PAUSE_SECONDS + " s");
        handler.postDelayed(countdown, 250L);
        go.setOnClickListener(view -> {
            if (SystemClock.elapsedRealtime() - shownAt < GuardService.PAUSE_SECONDS * 1000L) return;
            actions.continueToApp();
        });

        scroll.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private TextView button(String label) {
        TextView button = Ui.text(context, label, 15, 0xFFFFFFFF, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.pressable(Ui.NIGHT_KEY, Ui.NIGHT_KEY_DOWN, Ui.dp(context, 14)));
        int horizontal = Ui.dp(context, 22);
        int vertical = Ui.dp(context, 13);
        button.setPadding(horizontal, vertical, horizontal, vertical);
        return button;
    }
}
