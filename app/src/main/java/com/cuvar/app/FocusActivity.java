package com.cuvar.app;

import com.cuvar.app.ui.AppPicker;
import com.cuvar.app.ui.CheckRow;
import com.cuvar.app.ui.Sheet;
import com.cuvar.app.ui.Ui;
import com.cuvar.app.web.Hosts;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ručno pokrenut režim u kome su dozvoljene samo izabrane aplikacije i sajtovi. */
public class FocusActivity extends SubActivity {

    private final List<AppPicker.App> apps = new ArrayList<>();
    private final Set<String> selectedApps = new HashSet<>();
    private final Set<String> selectedSites = new HashSet<>();
    private int durationMin = 25;
    private TextView appButton;
    private TextView status;
    private EditText sitesInput;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        selectedApps.addAll(store.focus.focusApps());
        selectedSites.addAll(store.focus.focusSites());
        render();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null && store.focus.focusActive()) render();
    }

    private void render() {
        captureSites();
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        if (store.focus.focusActive()) {
            root.addView(header("Fokus režim", "Dozvoli samo izabrano · ručno pokrenuto"));
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, "Fokus je aktivan", 22, Ui.INK, true));
            card.addView(Ui.text(this, "Još " + Ui.fmt(store.focus.focusLeft()) + ". Dozvoljeno: "
                    + store.focus.focusApps().size() + " aplikacija i " + store.focus.focusSites().size() + " sajtova.",
                    14, Ui.MUTED, false), Ui.fill(this, 8));
            TextView stop = Ui.button(this, "Zaustavi fokus", true);
            stop.setOnClickListener(v -> {
                store.focus.stopFocus();
                finish();
            });
            card.addView(stop, Ui.fill(this, 14));
            LinearLayout.LayoutParams clp = Ui.fill(this, 20);
            clp.leftMargin = Ui.dp(this, 20);
            clp.rightMargin = Ui.dp(this, 20);
            root.addView(card, clp);
            setContentView(root);
            return;
        }

        root.addView(header("Fokus režim", "Dozvoli samo izabrano · ne menja trajna pravila"));
        // Sadržaj ima isti razmak od ivica kao zaglavlje, da tekst i dugmad ne budu uz ivicu ekrana.
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 24));
        root.addView(box);
        box.addView(Ui.text(this, "Dok traje fokus, sve ostalo je zaključano. Pozivi, poruke, početni ekran i Čuvar ostaju dostupni. Postojeći limiti i noćna blokada i dalje važe.",
                14, Ui.MUTED, false), Ui.fill(this, 20));

        box.addView(Sheet.label(this, "Trajanje"), Ui.fill(this, 12));
        LinearLayout durations = Ui.row(this);
        for (int min : new int[]{15, 25, 50, 90}) {
            TextView b = Ui.button(this, min + " min", min == durationMin);
            final int chosen = min;
            b.setOnClickListener(v -> {
                durationMin = chosen;
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f);
            lp.leftMargin = Ui.dp(this, 4);
            lp.rightMargin = Ui.dp(this, 4);
            durations.addView(b, lp);
        }
        box.addView(durations, Ui.fill(this, 8));

        appButton = Ui.button(this, appSummary(), false);
        appButton.setOnClickListener(v -> showAppPicker());
        box.addView(appButton, Ui.fill(this, 18));

        box.addView(Sheet.label(this, "Dozvoljeni sajtovi (opciono, razdvoj zarezima)"), Ui.fill(this, 12));
        sitesInput = Sheet.input(this, "npr. docs.google.com, github.com", false);
        sitesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        sitesInput.setText(joinSites());
        box.addView(sitesInput, Ui.fill(this, 6));

        TextView start = Ui.button(this, "Pokreni fokus", true);
        start.setOnClickListener(v -> startFocus());
        box.addView(start, Ui.fill(this, 20));

        status = Ui.text(this, "Izaberi bar jednu aplikaciju ili sajt.", 13, Ui.MUTED, false);
        box.addView(status, Ui.fill(this, 8));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    private String appSummary() {
        return selectedApps.isEmpty() ? "Izaberi dozvoljene aplikacije" :
                "Dozvoljene aplikacije: " + selectedApps.size();
    }

    private String joinSites() {
        StringBuilder out = new StringBuilder();
        for (String site : selectedSites) {
            if (out.length() > 0) out.append(", ");
            out.append(site);
        }
        return out.toString();
    }

    private void captureSites() {
        if (sitesInput == null) return;
        Set<String> parsed = new HashSet<>();
        for (String raw : sitesInput.getText().toString().split(",")) {
            String host = Hosts.hostOf(raw.trim());
            if (host != null && host.contains(".")) parsed.add(host);
        }
        selectedSites.clear();
        selectedSites.addAll(parsed);
    }

    private void loadApps() {
        AppPicker.load(this, out -> {
            apps.clear();
            apps.addAll(out);
            if (appButton != null) appButton.setText(appSummary());
        });
    }

    private void showAppPicker() {
        if (apps.isEmpty()) {
            Toast.makeText(this, Ui.cyr("Aplikacije se još učitavaju."), Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = Ui.column(this);
        TextView count = Ui.text(this, "Izabrano: " + selectedApps.size(), 14, Ui.ACCENT, true);
        List<CheckRow> rows = AppPicker.addList(this, box, apps, count);
        for (int i = 0; i < apps.size(); i++) {
            String pkg = apps.get(i).pkg;
            CheckRow row = rows.get(i);
            row.setChecked(selectedApps.contains(pkg));
            row.setListener(checked -> {
                if (checked) selectedApps.add(pkg); else selectedApps.remove(pkg);
                count.setText("Izabrano: " + selectedApps.size());
            });
        }
        new Sheet(this, "Dozvoljene aplikacije").message("Tokom fokusa ostale aplikacije se zaključavaju.")
                .view(box).secondary("Zatvori", null)
                .primary("Sačuvaj izbor", () -> {
                    if (appButton != null) appButton.setText(appSummary());
                    return true;
                }).show();
    }

    private void startFocus() {
        Set<String> sites = new HashSet<>();
        if (sitesInput != null) {
            for (String raw : sitesInput.getText().toString().split(",")) {
                String host = Hosts.hostOf(raw.trim());
                if (host == null || !host.contains(".")) {
                    if (!raw.trim().isEmpty()) {
                        sitesInput.setError("Unesi domen, npr. github.com");
                        return;
                    }
                } else {
                    sites.add(host);
                }
            }
        }
        if (selectedApps.isEmpty() && sites.isEmpty()) {
            status.setText("Izaberi bar jednu aplikaciju ili sajt.");
            status.setTextColor(Ui.ACCENT);
            return;
        }
        store.focus.startFocus(durationMin * 60000L, selectedApps, sites);
        Toast.makeText(this, Ui.cyr("Fokus je pokrenut na " + durationMin + " min."), Toast.LENGTH_LONG).show();
        finish();
    }
}
