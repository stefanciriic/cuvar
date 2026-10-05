package com.cuvar.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Jedan svakodnevni režim sa sopstvenim izborom aplikacija i domena. */
public class ScheduleActivity extends SubActivity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        root.addView(header("Vremenski režim", "Izaberi period i dodaj aplikacije i sajtove koji će tada biti blokirani svakog dana."));
        content = Ui.column(this);
        int p = Ui.dp(this, 20);
        content.setPadding(p, 0, p, p);
        Switch enabled = new Switch(this);
        enabled.setText("Uključi režim");
        enabled.setChecked(store.scheduleEnabled());
        enabled.setOnCheckedChangeListener((button, checked) ->
                store.setSchedule(checked, store.scheduleStart(), store.scheduleEnd()));
        content.addView(enabled, Ui.fill(this, 12));
        action("Period: " + store.scheduleLabel(), this::editTime);
        content.addView(Ui.text(this, "Po lokalnom vremenu telefona. Period može da prelazi ponoć. Blokada traje do kraja perioda.", 14, Ui.MUTED, false), Ui.fill(this, 8));
        content.addView(Ui.text(this, "Aplikacije", 20, Ui.INK, true), Ui.fill(this, 24));
        action("Dodaj / izaberi aplikacije", this::chooseApps);
        List<String> apps = store.scheduledApps();
        if (apps.isEmpty()) empty("Još nema aplikacija u režimu.");
        for (String pkg : apps) {
            String label = pkg;
            try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
            catch (Exception ignored) { }
            action(label + " · ukloni", () -> { store.setAppScheduled(pkg, false); render(); });
        }
        content.addView(Ui.text(this, "Web sajtovi", 20, Ui.INK, true), Ui.fill(this, 24));
        action("Dodaj sajt", this::addSite);
        List<String> sites = store.scheduledSites();
        if (sites.isEmpty()) empty("Još nema sajtova u režimu.");
        for (String domain : sites) action(domain + " · ukloni", () -> {
            store.setSiteScheduled(domain, false); render();
        });
        empty("Blokada sajta obuhvata i poddomene. Radi u podržanim pregledačima; za ostale dodaj ceo pregledač u režim.");
        root.addView(content);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void action(String label, Runnable run) {
        TextView button = Ui.button(this, label, false);
        button.setOnClickListener(v -> run.run());
        content.addView(button, Ui.fill(this, 10));
    }

    private void empty(String text) {
        content.addView(Ui.text(this, text, 14, Ui.MUTED, false), Ui.fill(this, 10));
    }

    private void editTime() {
        int[] values = {store.scheduleStart(), store.scheduleEnd()};
        LinearLayout box = Ui.column(this);
        int p = Ui.dp(this, 22);
        box.setPadding(p, p, p, p);
        for (int index = 0; index < 2; index++) {
            final int i = index;
            String prefix = i == 0 ? "Od " : "Do ";
            TextView button = Ui.button(this, prefix + DailySchedule.label(values[i]), false);
            button.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
                values[i] = hour * 60 + minute;
                button.setText(prefix + DailySchedule.label(values[i]));
            }, values[i] / 60, values[i] % 60, true).show());
            box.addView(button, Ui.fill(this, 12));
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Period režima").setView(box)
                .setPositiveButton("Sačuvaj", null).setNegativeButton("Otkaži", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (values[0] == values[1]) {
                Toast.makeText(this, "Početak i kraj moraju biti različiti", Toast.LENGTH_LONG).show();
                return;
            }
            store.setSchedule(store.scheduleEnabled(), values[0], values[1]);
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }

    private void chooseApps() {
        Toast.makeText(this, "Učitavam aplikacije…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<String[]> items = new ArrayList<>();
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                Set<String> seen = new HashSet<>();
                for (ResolveInfo info : getPackageManager().queryIntentActivities(intent, 0)) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    items.add(new String[]{pkg, info.loadLabel(getPackageManager()).toString()});
                }
                items.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
            } catch (Exception ignored) { }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (items.isEmpty()) {
                    Toast.makeText(this, "Nije moguće učitati aplikacije", Toast.LENGTH_LONG).show();
                    return;
                }
                String[] labels = new String[items.size()];
                boolean[] selected = new boolean[items.size()];
                for (int i = 0; i < items.size(); i++) {
                    labels[i] = items.get(i)[1];
                    selected[i] = store.appScheduled(items.get(i)[0]);
                }
                new AlertDialog.Builder(this).setTitle("Aplikacije u režimu")
                        .setMultiChoiceItems(labels, selected, (d, which, checked) -> selected[which] = checked)
                        .setPositiveButton("Sačuvaj", (d, w) -> {
                            for (int i = 0; i < items.size(); i++) store.setAppScheduled(items.get(i)[0], selected[i]);
                            render();
                        }).setNegativeButton("Otkaži", null).show();
            });
        }).start();
    }

    private void addSite() {
        EditText address = new EditText(this);
        address.setHint("npr. youtube.com");
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Dodaj sajt u režim").setView(address)
                .setPositiveButton("Dodaj", null).setNegativeButton("Otkaži", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String host = Store.hostOf(address.getText().toString());
            if (host == null || !host.contains(".") || !host.matches("[a-z0-9\\p{L}.-]+")) {
                address.setError("Unesi adresu sajta, npr. youtube.com");
                return;
            }
            store.setSiteScheduled(host, true);
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }
}
