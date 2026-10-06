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

/**
 * Vremenski režimi. Bez "id" u intent-u prikazuje listu režima; sa "id" uređuje jedan režim
 * sa sopstvenim periodom, aplikacijama i domenima.
 */
public class ScheduleActivity extends SubActivity {
    private static final String EXTRA_ID = "id";

    private LinearLayout content;
    private String id;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getStringExtra(EXTRA_ID);
    }

    @Override protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        render();
    }

    private void render() {
        if (id == null) renderList(); else renderRule();
    }

    private LinearLayout page(String title, String sub) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        root.addView(header(title, sub));
        content = Ui.column(this);
        int p = Ui.dp(this, 20);
        content.setPadding(p, 0, p, p);
        root.addView(content);
        scroll.addView(root);
        setContentView(scroll);
        return content;
    }

    private void renderList() {
        page("Vremenski režimi", "Svaki režim ima svoj period i svoje aplikacije i sajtove koji će tada biti blokirani svakog dana.");
        action("Dodaj režim", () -> open(store.addSchedule().id));
        List<DailySchedule.Rule> rules = store.schedules();
        if (rules.isEmpty()) empty("Još nema režima.");
        for (DailySchedule.Rule r : rules) {
            String state = r.enabled ? "uključen" : "isključen";
            action(r.name + " · " + r.label() + " · " + state + "\n"
                    + "Aplikacije: " + r.apps.size() + " · Sajtovi: " + r.sites.size(), () -> open(r.id));
        }
        empty("Po lokalnom vremenu telefona. Period može da prelazi ponoć. Ako je aplikacija ili sajt u više uključenih režima, blokada važi kad god je bilo koji od njih aktivan.");
    }

    private void open(String ruleId) {
        startActivity(new Intent(this, ScheduleActivity.class).putExtra(EXTRA_ID, ruleId));
    }

    private void renderRule() {
        DailySchedule.Rule rule = store.schedule(id);
        if (rule == null) {
            finish();
            return;
        }
        page(rule.name, "Izaberi period i dodaj aplikacije i sajtove koji će tada biti blokirani svakog dana.");
        Switch enabled = new Switch(this);
        enabled.setText("Uključi režim");
        enabled.setChecked(rule.enabled);
        enabled.setOnCheckedChangeListener((button, checked) ->
                store.setSchedule(id, rule.name, checked, rule.start, rule.end));
        content.addView(enabled, Ui.fill(this, 12));
        action("Naziv: " + rule.name, () -> editName(rule));
        action("Period: " + rule.label(), () -> editTime(rule));
        content.addView(Ui.text(this, "Po lokalnom vremenu telefona. Period može da prelazi ponoć. Blokada traje do kraja perioda.", 14, Ui.MUTED, false), Ui.fill(this, 8));
        content.addView(Ui.text(this, "Aplikacije", 20, Ui.INK, true), Ui.fill(this, 24));
        action("Dodaj / izaberi aplikacije", () -> chooseApps(rule));
        if (rule.apps.isEmpty()) empty("Još nema aplikacija u režimu.");
        for (String pkg : rule.apps) {
            String label = pkg;
            try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
            catch (Exception ignored) { }
            action(label + " · ukloni", () -> { store.setScheduleApp(id, pkg, false); render(); });
        }
        content.addView(Ui.text(this, "Web sajtovi", 20, Ui.INK, true), Ui.fill(this, 24));
        action("Dodaj sajt", this::addSite);
        if (rule.sites.isEmpty()) empty("Još nema sajtova u režimu.");
        List<String> sites = new ArrayList<>(rule.sites);
        sites.sort(String::compareTo);
        for (String domain : sites) action(domain + " · ukloni", () -> {
            store.setScheduleSite(id, domain, false); render();
        });
        empty("Blokada sajta obuhvata i poddomene. Radi u podržanim pregledačima; za ostale dodaj ceo pregledač u režim.");
        content.addView(Ui.text(this, "Brisanje", 20, Ui.INK, true), Ui.fill(this, 24));
        action("Obriši režim", () -> new AlertDialog.Builder(this).setTitle("Obriši režim „" + rule.name + "“?")
                .setMessage("Period i izbor aplikacija i sajtova ovog režima biće obrisani.")
                .setPositiveButton("Obriši", (d, w) -> { store.removeSchedule(id); finish(); })
                .setNegativeButton("Otkaži", null).show());
    }

    private void action(String label, Runnable run) {
        TextView button = Ui.button(this, label, false);
        button.setOnClickListener(v -> run.run());
        content.addView(button, Ui.fill(this, 10));
    }

    private void empty(String text) {
        content.addView(Ui.text(this, text, 14, Ui.MUTED, false), Ui.fill(this, 10));
    }

    private void editName(DailySchedule.Rule rule) {
        EditText name = new EditText(this);
        name.setText(rule.name);
        name.setSingleLine(true);
        name.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Naziv režima").setView(name)
                .setPositiveButton("Sačuvaj", null).setNegativeButton("Otkaži", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) {
                name.setError("Unesi naziv, npr. Spavanje");
                return;
            }
            DailySchedule.Rule now = store.schedule(id);
            if (now != null) store.setSchedule(id, value, now.enabled, now.start, now.end);
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }

    private void editTime(DailySchedule.Rule rule) {
        int[] values = {rule.start, rule.end};
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
            DailySchedule.Rule now = store.schedule(id);
            if (now != null) store.setSchedule(id, now.name, now.enabled, values[0], values[1]);
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }

    private void chooseApps(DailySchedule.Rule rule) {
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
                    selected[i] = rule.apps.contains(items.get(i)[0]);
                }
                new AlertDialog.Builder(this).setTitle("Aplikacije u režimu „" + rule.name + "“")
                        .setMultiChoiceItems(labels, selected, (d, which, checked) -> selected[which] = checked)
                        .setPositiveButton("Sačuvaj", (d, w) -> {
                            for (int i = 0; i < items.size(); i++) store.setScheduleApp(id, items.get(i)[0], selected[i]);
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
            store.setScheduleSite(id, host, true);
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }
}
