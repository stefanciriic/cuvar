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
        return header(title, sub, true);
    }

    protected LinearLayout header(String title, String sub, boolean withBack) {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, withBack ? 20 : 28), Ui.dp(this, 20), Ui.dp(this, 10));
        if (!withBack) {
            box.addView(Ui.text(this, title, 34, Ui.INK, true));
            box.addView(Ui.text(this, sub, 14, Ui.MUTED, false), Ui.fill(this, 2));
            return box;
        }
        TextView back = Ui.text(this, "‹ Nazad", 15, Ui.ACCENT, true);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setMinHeight(Ui.dp(this, 48));
        back.setContentDescription("Nazad");
        back.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        box.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(Ui.text(this, title, 30, Ui.INK, true), Ui.fill(this, 4));
        box.addView(Ui.text(this, sub, 14, Ui.MUTED, false), Ui.fill(this, 4));
        return box;
    }

    /**
     * Kad je dnevni limit potrošen ili traje noćna blokada, aplikacija bez pravila se zaključava čim dobije pravilo,
     * a pravilo se skida tek sutra. Zato se pre toga pita, da se to ne desi slučajno.
     */
    protected void saveGuarding(String pkg, String label, boolean adding, Runnable save) {
        String why = adding && !store.appGuarded(pkg) ? store.lockedOnceGuarded() : null;
        if (why == null) {
            save.run();
            return;
        }
        new Sheet(this, "Zaključaće se odmah")
                .message(why + ". Čim " + label + " dobije pravilo, zaključava se do sutra ujutru, "
                        + "a pravilo može da se skine tek sutra od 06:00.")
                .secondary("Otkaži", null)
                .primary("Ipak dodaj", () -> {
                    save.run();
                    return true;
                })
                .show();
    }
}
