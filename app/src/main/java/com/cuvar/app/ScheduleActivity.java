package com.cuvar.app;

import android.app.TimePickerDialog;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
        CheckRow enabled = new CheckRow(this, null, "Režim je uključen", "Blokira svakog dana u izabranom periodu");
        enabled.setChecked(rule.enabled);
        enabled.setListener(checked -> {
            DailySchedule.Rule now = store.schedule(id);
            if (now != null) store.setSchedule(id, now.name, checked, now.start, now.end);
        });
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
        action("Obriši režim", () -> new Sheet(this, "Obriši režim „" + rule.name + "“?")
                .message("Period i izbor aplikacija i sajtova ovog režima biće obrisani.")
                .secondary("Otkaži", null)
                .primary("Obriši", () -> { store.removeSchedule(id); finish(); return true; })
                .show());
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
        EditText name = Sheet.input(this, "npr. Spavanje", false);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(rule.name);
        name.setSelectAllOnFocus(true);
        new Sheet(this, "Naziv režima").view(name)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) {
                        name.setError("Unesi naziv, npr. Spavanje");
                        return false;
                    }
                    DailySchedule.Rule now = store.schedule(id);
                    if (now != null) store.setSchedule(id, value, now.enabled, now.start, now.end);
                    render();
                    return true;
                }).show();
    }

    private void editTime(DailySchedule.Rule rule) {
        int[] values = {rule.start, rule.end};
        LinearLayout box = Ui.column(this);
        for (int index = 0; index < 2; index++) {
            final int i = index;
            String prefix = i == 0 ? "Od " : "Do ";
            TextView button = Ui.button(this, prefix + DailySchedule.label(values[i]), false);
            button.setOnClickListener(v -> new TimePickerDialog(this, R.style.CuvarDialog, (picker, hour, minute) -> {
                values[i] = hour * 60 + minute;
                button.setText(prefix + DailySchedule.label(values[i]));
            }, values[i] / 60, values[i] % 60, true).show());
            box.addView(button, Ui.fill(this, i == 0 ? 0 : 10));
        }
        new Sheet(this, "Period režima").view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    if (values[0] == values[1]) {
                        Toast.makeText(this, "Početak i kraj moraju biti različiti", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    DailySchedule.Rule now = store.schedule(id);
                    if (now != null) store.setSchedule(id, now.name, now.enabled, values[0], values[1]);
                    render();
                    return true;
                }).show();
    }

    private static final class AppItem {
        String pkg;
        String label;
        Drawable icon;
    }

    private void chooseApps(DailySchedule.Rule rule) {
        Toast.makeText(this, "Učitavam aplikacije…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<AppItem> items = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                Set<String> seen = new HashSet<>();
                for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    AppItem it = new AppItem();
                    it.pkg = pkg;
                    it.label = info.loadLabel(pm).toString();
                    try { it.icon = info.loadIcon(pm); } catch (Throwable ignored) { }
                    items.add(it);
                }
                // Već izabrane na vrh, ostale po abecedi.
                items.sort((a, b) -> {
                    boolean sa = rule.apps.contains(a.pkg), sb = rule.apps.contains(b.pkg);
                    if (sa != sb) return sa ? -1 : 1;
                    return a.label.compareToIgnoreCase(b.label);
                });
            } catch (Exception ignored) { }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (items.isEmpty()) {
                    Toast.makeText(this, "Nije moguće učitati aplikacije", Toast.LENGTH_LONG).show();
                    return;
                }
                showAppPicker(rule, items);
            });
        }).start();
    }

    private void showAppPicker(DailySchedule.Rule rule, List<AppItem> items) {
        Set<String> selected = new HashSet<>(rule.apps);
        LinearLayout box = Ui.column(this);
        TextView count = Ui.text(this, "", 14, Ui.ACCENT, true);
        Runnable updateCount = () -> count.setText(selected.isEmpty()
                ? "Ništa nije izabrano" : "Izabrano: " + selected.size());
        updateCount.run();

        EditText search = Sheet.input(this, "Pretraži aplikacije", false);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(search);
        box.addView(count, Ui.fill(this, 10));

        List<CheckRow> rows = new ArrayList<>();
        for (AppItem it : items) {
            CheckRow row = new CheckRow(this, it.icon, it.label, null);
            row.setChecked(selected.contains(it.pkg));
            row.setListener(checked -> {
                if (checked) selected.add(it.pkg); else selected.remove(it.pkg);
                updateCount.run();
            });
            rows.add(row);
            box.addView(row, Ui.fill(this, 6));
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < rows.size(); i++) {
                    boolean show = q.isEmpty() || items.get(i).label.toLowerCase(Locale.ROOT).contains(q);
                    rows.get(i).setVisibility(show ? View.VISIBLE : View.GONE);
                }
            }
        });

        new Sheet(this, "Aplikacije u režimu „" + rule.name + "“")
                .message("Izabrane aplikacije biće blokirane svakog dana u periodu " + rule.label() + ".")
                .view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    for (AppItem it : items) store.setScheduleApp(id, it.pkg, selected.contains(it.pkg));
                    render();
                    return true;
                }).show();
    }

    private void addSite() {
        EditText address = Sheet.input(this, "npr. youtube.com", false);
        new Sheet(this, "Dodaj sajt u režim")
                .message("Blokada obuhvata i poddomene, npr. m.youtube.com.")
                .view(address)
                .secondary("Otkaži", null)
                .primary("Dodaj", () -> {
                    String host = Store.hostOf(address.getText().toString());
                    if (host == null || !host.contains(".") || !host.matches("[a-z0-9\\p{L}.-]+")) {
                        address.setError("Unesi adresu sajta, npr. youtube.com");
                        return false;
                    }
                    store.setScheduleSite(id, host, true);
                    render();
                    return true;
                }).show();
    }
}
