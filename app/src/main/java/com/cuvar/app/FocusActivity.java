package com.cuvar.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ručno pokrenut režim u kome su dozvoljene samo izabrane aplikacije i sajtovi. */
public class FocusActivity extends SubActivity {

    private static final class Item {
        String pkg;
        String label;
        Drawable icon;
    }

    private final List<Item> apps = new ArrayList<>();
    private final Set<String> selectedApps = new HashSet<>();
    private final Set<String> selectedSites = new HashSet<>();
    private int durationMin = 25;
    private TextView appButton;
    private TextView status;
    private EditText sitesInput;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        selectedApps.addAll(store.focusApps());
        selectedSites.addAll(store.focusSites());
        render();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null && store.focusActive()) render();
    }

    private void render() {
        captureSites();
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        if (store.focusActive()) {
            root.addView(header("Fokus režim", "Dozvoli samo izabrano · ručno pokrenuto"));
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, "Fokus je aktivan", 22, Ui.INK, true));
            card.addView(Ui.text(this, "Još " + Ui.fmt(store.focusLeft()) + ". Dozvoljeno: "
                    + store.focusApps().size() + " aplikacija i " + store.focusSites().size() + " sajtova.",
                    14, Ui.MUTED, false), Ui.fill(this, 8));
            TextView stop = Ui.button(this, "Zaustavi fokus", true);
            stop.setOnClickListener(v -> {
                store.stopFocus();
                finish();
            });
            card.addView(stop, Ui.fill(this, 14));
            root.addView(card, Ui.fill(this, 20));
            setContentView(root);
            return;
        }

        root.addView(header("Fokus režim", "Dozvoli samo izabrano · ne menja trajna pravila"));
        root.addView(Ui.text(this, "Dok traje fokus, sve ostalo je zaključano. Pozivi, poruke, početni ekran i Čuvar ostaju dostupni. Postojeći limiti i noćna blokada i dalje važe.",
                14, Ui.MUTED, false), Ui.fill(this, 20));

        root.addView(Sheet.label(this, "Trajanje"), Ui.fill(this, 12));
        LinearLayout durations = Ui.row(this);
        for (int min : new int[]{15, 25, 50, 90}) {
            TextView b = Ui.button(this, min + " min", min == durationMin);
            final int chosen = min;
            b.setOnClickListener(v -> {
                durationMin = chosen;
                render();
            });
            durations.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f));
        }
        root.addView(durations, Ui.fill(this, 8));

        appButton = Ui.button(this, appSummary(), false);
        appButton.setOnClickListener(v -> showAppPicker());
        root.addView(appButton, Ui.fill(this, 18));

        root.addView(Sheet.label(this, "Dozvoljeni sajtovi (opciono, razdvoj zarezima)"), Ui.fill(this, 12));
        sitesInput = Sheet.input(this, "npr. docs.google.com, github.com", false);
        sitesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        sitesInput.setText(joinSites());
        root.addView(sitesInput, Ui.fill(this, 6));

        TextView start = Ui.button(this, "Pokreni fokus", true);
        start.setOnClickListener(v -> startFocus());
        root.addView(start, Ui.fill(this, 20));

        status = Ui.text(this, "Izaberi bar jednu aplikaciju ili sajt.", 13, Ui.MUTED, false);
        root.addView(status, Ui.fill(this, 8));
        setContentView(root);
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
            String host = Store.hostOf(raw.trim());
            if (host != null && host.contains(".")) parsed.add(host);
        }
        selectedSites.clear();
        selectedSites.addAll(parsed);
    }

    private void loadApps() {
        new Thread(() -> {
            List<Item> out = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                Set<String> seen = new HashSet<>();
                for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                    if (ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg == null || pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    Item item = new Item();
                    item.pkg = pkg;
                    CharSequence label = ri.loadLabel(pm);
                    item.label = label == null ? pkg : label.toString();
                    try { item.icon = ri.loadIcon(pm); } catch (Throwable ignored) { }
                    out.add(item);
                }
            } catch (Throwable ignored) { }
            Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(out);
                if (appButton != null) appButton.setText(appSummary());
            });
        }).start();
    }

    private void showAppPicker() {
        if (apps.isEmpty()) {
            Toast.makeText(this, "Aplikacije se još učitavaju.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = Ui.column(this);
        EditText search = Sheet.input(this, "Pretraži aplikacije", false);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(search);
        TextView count = Ui.text(this, "Izabrano: " + selectedApps.size(), 14, Ui.ACCENT, true);
        box.addView(count, Ui.fill(this, 10));
        List<CheckRow> rows = new ArrayList<>();
        for (Item item : apps) {
            CheckRow row = new CheckRow(this, item.icon, item.label, null);
            row.setChecked(selectedApps.contains(item.pkg));
            row.setListener(checked -> {
                if (checked) selectedApps.add(item.pkg); else selectedApps.remove(item.pkg);
                count.setText("Izabrano: " + selectedApps.size());
            });
            rows.add(row);
            box.addView(row, Ui.fill(this, 6));
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < rows.size(); i++) {
                    rows.get(i).setVisibility(q.isEmpty() || apps.get(i).label.toLowerCase(Locale.ROOT).contains(q)
                            ? View.VISIBLE : View.GONE);
                }
            }
        });
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
                String host = Store.hostOf(raw.trim());
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
        store.startFocus(durationMin * 60000L, selectedApps, sites);
        Toast.makeText(this, "Fokus je pokrenut na " + durationMin + " min.", Toast.LENGTH_LONG).show();
        finish();
    }
}
