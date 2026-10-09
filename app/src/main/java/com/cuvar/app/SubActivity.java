package com.cuvar.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Zajednička osnova za ekrane sa listama. */
abstract class SubActivity extends Activity {

    protected Store store;
    private boolean dark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.theme(this);
        dark = Ui.dark;
        super.onCreate(savedInstanceState);
        store = Store.get(this);
        Ui.styleWindow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.wantsDark(this) != dark) {
            recreate();
            return;
        }
    }

    /** Zaglavlje sa strelicom nazad, naslovom i kratkim objašnjenjem. */
    protected LinearLayout header(String title, String sub) {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 10));
        TextView back = Ui.text(this, "‹ Nazad", 15, Ui.ACCENT, true);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        box.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(Ui.text(this, title, 30, Ui.INK, true), Ui.fill(this, 4));
        box.addView(Ui.text(this, sub, 14, Ui.MUTED, false), Ui.fill(this, 4));
        return box;
    }
}
