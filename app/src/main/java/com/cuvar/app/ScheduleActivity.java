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
        page("Vremenski režimi", "Svaki režim ima svoj period, dane i svoje aplikacije i sajtove koji će tada biti blokirani.");
        TextView add = Ui.button(this, "+  Dodaj režim", true);
        add.setOnClickListener(v -> open(store.addSchedule().id));
        content.addView(add, Ui.fill(this, 10));

        View pending = MainActivity.pendingCard(this, store, this::render);
        if (pending != null) content.addView(pending, Ui.fill(this, 14));
        List<DailySchedule.Rule> rules = new ArrayList<>(store.schedules());
        Set<String> listed = new HashSet<>();
        for (DailySchedule.Rule r : rules) listed.add(r.id);
        // Zakazano brisanje ne sme sakriti režim koji još blokira.
        for (DailySchedule.Rule r : store.schedulesNow()) if (listed.add(r.id)) rules.add(r);
        content.addView(Ui.section(this, "Tvoji režimi"), Ui.fill(this, 26));
        if (rules.isEmpty()) empty("Još nema režima.");
        for (DailySchedule.Rule r : rules) content.addView(ruleCard(r), Ui.fill(this, 10));

        boolean work = !store.hasWorkSchedule(), night = !store.hasNightSchedule();
        if (work || night) {
            content.addView(Ui.section(this, "Gotovi režimi"), Ui.fill(this, 26));
            if (work) {
                content.addView(presetCard("Radno vreme", "09:00–17:00 · svakog dana · posle toga dnevnom šifrom",
                        () -> open(store.addWorkSchedule().id)), Ui.fill(this, 10));
            }
            if (night) {
                content.addView(presetCard("Noćno zaključavanje", "22:30–06:00 · svakog dana",
                        () -> open(store.addNightSchedule().id)), Ui.fill(this, 10));
            }
        }
        content.addView(Ui.text(this, "Po vremenu telefona; pomeranje sata ne skraćuje režim. Period može da prelazi ponoć. Ako je aplikacija ili sajt u više uključenih režima, blokada važi kad god je bilo koji od njih aktivan.",
                13, Ui.MUTED, false), Ui.fill(this, 24));
    }

    /** Kartica režima: naziv i stanje u prvom redu, period i dani ispod, pa broj aplikacija i sajtova. */
    private View ruleCard(DailySchedule.Rule r) {
        List<DailySchedule.Rule> effective = effectiveRules(r.id);
        DailySchedule.Rule desired = store.schedule(r.id);
        boolean pending = differs(effective, desired);
        boolean enabledNow = false;
        for (DailySchedule.Rule current : effective) enabledNow |= current.enabled;
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(this, 18)));
        int p = Ui.dp(this, 16);
        card.setPadding(Ui.dp(this, 18), p, p, p);

        LinearLayout top = Ui.row(this);
        TextView name = Ui.text(this, r.name, 18, enabledNow ? Ui.INK : Ui.MUTED, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (store.scheduleActive(r.id)) top.addView(Ui.badge(this, "Traje sada", Ui.ACCENT), Ui.wrap(this, 8));
        top.addView(enabledNow ? Ui.badge(this, "Uključen", Charts.good()) : Ui.badge(this, "Isključen", Ui.MUTED),
                Ui.wrap(this, 8));
        card.addView(top);

        card.addView(Ui.text(this, effectiveDescription(effective), 15, enabledNow ? Ui.INK : Ui.MUTED, false),
                Ui.fill(this, 6));
        if (pending) card.addView(Ui.text(this, "Od sledećih 06:00: " + (desired == null
                ? "režim se briše" : description(desired)), 13, Ui.ACCENT, false), Ui.fill(this, 6));
        card.setOnClickListener(v -> {
            if (desired != null) open(r.id);
            else new Sheet(this, "Brisanje režima je zakazano")
                    .message("Ovo još važi do sledećih 06:00:\n" + effectiveDescription(effective)
                            + "\n\nZa odustajanje koristi karticu „Od sutra“ na listi režima.")
                    .secondary("Zatvori", null).show();
        });
        return card;
    }

    private List<DailySchedule.Rule> effectiveRules(String ruleId) {
        List<DailySchedule.Rule> out = new ArrayList<>();
        for (DailySchedule.Rule r : store.schedulesNow()) if (r.id.equals(ruleId)) out.add(r);
        return out;
    }

    private boolean differs(List<DailySchedule.Rule> effective, DailySchedule.Rule desired) {
        if (desired == null || effective.size() != 1) return true;
        DailySchedule.Rule current = effective.get(0);
        return current.enabled != desired.enabled || current.start != desired.start || current.end != desired.end
                || current.days != desired.days || current.code != desired.code
                || !current.apps.equals(desired.apps) || !current.sites.equals(desired.sites);
    }

    private String description(DailySchedule.Rule r) {
        return (r.enabled ? "" : "Isključen · ") + r.label() + " · " + r.daysLabel()
                + " · " + Ui.count(r.apps.size(), "aplikacija", "aplikacije", "aplikacija")
                + " · " + Ui.count(r.sites.size(), "sajt", "sajta", "sajtova")
                + (r.code ? " · dnevna šifra" : "");
    }

    private String effectiveDescription(List<DailySchedule.Rule> rules) {
        List<String> lines = new ArrayList<>();
        for (DailySchedule.Rule r : rules) lines.add(description(r));
        return lines.isEmpty() ? "Bez aktivnih pravila" : android.text.TextUtils.join("\n", lines);
    }

    private View presetCard(String title, String sub, Runnable run) {
        LinearLayout tile = Ui.row(this);
        tile.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(this, 18)));
        int p = Ui.dp(this, 16);
        tile.setPadding(Ui.dp(this, 18), p, p, p);
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 16, Ui.INK, true));
        texts.addView(Ui.text(this, sub, 13, Ui.MUTED, false), Ui.fill(this, 2));
        tile.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tile.addView(Ui.text(this, "Dodaj", 14, Ui.ACCENT, true), Ui.wrap(this, 12));
        tile.setOnClickListener(v -> run.run());
        return tile;
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
        page(rule.name, "Izaberi period i dane i dodaj aplikacije i sajtove koji će tada biti blokirani. Strože izmene važe odmah, a blaže (isključivanje, kraći period, manje dana, uklanjanje) tek sutra od 06:00.");
        List<DailySchedule.Rule> effective = effectiveRules(id);
        if (differs(effective, rule)) {
            LinearLayout current = Ui.card(this);
            current.addView(Ui.text(this, "Važi sada", 16, Ui.INK, true));
            current.addView(Ui.text(this, effectiveDescription(effective), 14, Ui.MUTED, false), Ui.fill(this, 6));
            current.addView(Ui.text(this, "Podešavanja ispod važe u potpunosti od sledećih 06:00. "
                    + "Pooštravanje važi odmah.", 14, Ui.ACCENT, false), Ui.fill(this, 6));
            content.addView(current, Ui.fill(this, 12));
        }
        boolean active = store.scheduleActive(id);
        if (active) {
            content.addView(Ui.text(this, "Režim je sada aktivan. Dok traje ne može da se isključi, obriše, skrati ni da mu se promene dani, a aplikacije i sajtovi"
                    + " mogu samo da se dodaju.", 14, Ui.ACCENT, true), Ui.fill(this, 12));
        }
        CheckRow enabled = new CheckRow(this, null, "Režim je uključen", "Blokira u izabranom periodu i danima");
        enabled.setChecked(rule.enabled);
        enabled.setListener(checked -> {
            DailySchedule.Rule now = store.schedule(id);
            if (now != null && !store.setSchedule(id, now.name, checked, now.start, now.end)) refused();
            else render();
        });
        content.addView(enabled, Ui.fill(this, 12));
        action("Naziv: " + rule.name, () -> editName(rule));
        action("Period: " + rule.label(), () -> editTime(rule));
        action("Dani: " + rule.daysLabel(), () -> editDays(rule));
        content.addView(Ui.text(this, "Po vremenu telefona; pomeranje sata ne skraćuje režim. Period može da prelazi ponoć i tada pripada danu u kome počinje. Blokada traje do kraja perioda.", 14, Ui.MUTED, false), Ui.fill(this, 8));
        CheckRow code = new CheckRow(this, null, "Van perioda traži dnevnu šifru",
                "Aplikacije i sajtovi ovog režima su i van perioda zaključani i otvaraju se samo dnevnom šifrom, od 17:00 do 22:00. Šifra se vidi na početnom ekranu Čuvara.");
        code.setChecked(rule.code);
        code.setListener(checked -> {
            if (!store.setScheduleCode(id, checked)) refused();
            else render();
        });
        content.addView(code, Ui.fill(this, 12));
        content.addView(Ui.text(this, "Aplikacije", 20, Ui.INK, true), Ui.fill(this, 24));
        action("Dodaj / izaberi aplikacije", () -> chooseApps(rule));
        if (rule.apps.isEmpty()) empty("Još nema aplikacija u režimu.");
        for (String pkg : rule.apps) {
            String label = pkg;
            try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
            catch (Exception ignored) { }
            action(label + " · ukloni", () -> {
                if (!store.setScheduleApp(id, pkg, false)) refused();
                render();
            });
        }
        content.addView(Ui.text(this, "Web sajtovi", 20, Ui.INK, true), Ui.fill(this, 24));
        action("Dodaj sajt", this::addSite);
        if (rule.sites.isEmpty()) empty("Još nema sajtova u režimu.");
        List<String> sites = new ArrayList<>(rule.sites);
        sites.sort(String::compareTo);
        for (String domain : sites) action(domain + " · ukloni", () -> {
            if (!store.setScheduleSite(id, domain, false)) refused();
            render();
        });
        empty("Blokada sajta obuhvata i poddomene. Radi u podržanim pregledačima; za ostale dodaj ceo pregledač u režim.");
        content.addView(Ui.text(this, "Brisanje", 20, Ui.INK, true), Ui.fill(this, 24));
        action("Obriši režim", () -> new Sheet(this, "Obriši režim „" + rule.name + "“?")
                .message("Period i izbor aplikacija i sajtova ovog režima biće obrisani.")
                .secondary("Otkaži", null)
                .primary("Obriši", () -> {
                    if (store.removeSchedule(id)) finish(); else refused();
                    return true;
                })
                .show());
    }

    /** Izmena bi oslabila aktivan režim: ekran se vraća na sačuvano stanje. */
    private void refused() {
        Toast.makeText(this, "Režim je sada aktivan i to ne može da se promeni do kraja perioda", Toast.LENGTH_LONG).show();
        render();
    }

    private void editDays(DailySchedule.Rule rule) {
        int[] days = {rule.days};
        LinearLayout box = Ui.column(this);
        String[] names = {"Ponedeljak", "Utorak", "Sreda", "Četvrtak", "Petak", "Subota", "Nedelja"};
        for (int d = 0; d < 7; d++) {
            final int bit = 1 << d;
            CheckRow row = new CheckRow(this, null, names[d], null);
            row.setChecked((days[0] & bit) != 0);
            row.setListener(checked -> days[0] = checked ? days[0] | bit : days[0] & ~bit);
            box.addView(row, Ui.fill(this, d == 0 ? 0 : 6));
        }
        new Sheet(this, "Dani režima").view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    if (days[0] == 0) {
                        Toast.makeText(this, "Izaberi bar jedan dan", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    if (!store.setScheduleDays(id, days[0])) refused(); else render();
                    return true;
                }).show();
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
            button.setOnClickListener(v -> new TimePickerDialog(this, Ui.dialogStyle(), (picker, hour, minute) -> {
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
                    if (now != null && !store.setSchedule(id, now.name, now.enabled, values[0], values[1])) refused();
                    else render();
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
                .message("Izabrane aplikacije biće blokirane " + rule.daysLabel() + " u periodu " + rule.label() + ".")
                .view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    boolean ok = true;
                    DailySchedule.Rule current = store.schedule(id);
                    if (current != null) {
                        for (AppItem it : items) {
                            boolean want = selected.contains(it.pkg);
                            if (want != current.apps.contains(it.pkg)) ok &= store.setScheduleApp(id, it.pkg, want);
                        }
                    }
                    if (!ok) refused(); else render();
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
