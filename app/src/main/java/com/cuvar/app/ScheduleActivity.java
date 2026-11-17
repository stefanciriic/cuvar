package com.cuvar.app;

import com.cuvar.app.data.AppLabels;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.ui.Charts;
import com.cuvar.app.ui.AppPicker;
import com.cuvar.app.ui.CheckRow;
import com.cuvar.app.ui.Sheet;
import com.cuvar.app.ui.Safe;
import com.cuvar.app.ui.Ui;
import com.cuvar.app.web.Hosts;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
    private ScrollView scroll;
    private String id;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getStringExtra(EXTRA_ID);
    }

    @Override protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        Safe.screen(this, "schedule", this::render);
    }

    private void render() {
        if (id == null) renderList(); else renderRule();
    }

    private LinearLayout page(String title, String sub) {
        // Posle izmene (npr. "Ukloni") ekran se crta ponovo: ostaje na istom mestu, ne skače na vrh.
        int y = scroll == null ? 0 : scroll.getScrollY();
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        root.addView(header(title, sub));
        content = Ui.column(this);
        int p = Ui.dp(this, 20);
        content.setPadding(p, 0, p, p);
        root.addView(content);
        scroll.addView(root);
        setContentView(scroll);
        if (y > 0) {
            ScrollView view = scroll;
            view.post(Safe.task("scheduleScroll", () -> view.scrollTo(0, y)));
        }
        return content;
    }

    private void renderList() {
        page("Vremenski režimi", "Svaki režim ima svoj period, dane i svoje aplikacije i sajtove koji će tada biti blokirani.");
        TextView add = Ui.button(this, "+  Dodaj režim", true);
        add.setOnClickListener(v -> open(store.modes.addSchedule().id));
        content.addView(add, Ui.fill(this, 10));

        View pending = MainActivity.pendingCard(this, store, this::render);
        if (pending != null) content.addView(pending, Ui.fill(this, 14));
        List<DailySchedule.Rule> rules = new ArrayList<>(store.modes.schedules());
        Set<String> listed = new HashSet<>();
        for (DailySchedule.Rule r : rules) listed.add(r.id);
        // Zakazano brisanje ne sme sakriti režim koji još blokira.
        for (DailySchedule.Rule r : store.modes.schedulesNow()) if (listed.add(r.id)) rules.add(r);
        content.addView(Ui.section(this, "Tvoji režimi"), Ui.fill(this, 26));
        if (rules.isEmpty()) empty("Još nema režima.");
        for (DailySchedule.Rule r : rules) content.addView(ruleCard(r), Ui.fill(this, 10));

        boolean work = !store.modes.hasWorkSchedule(), night = !store.modes.hasNightSchedule();
        if (work || night) {
            content.addView(Ui.section(this, "Gotovi režimi"), Ui.fill(this, 26));
            if (work) {
                content.addView(presetCard("Radno vreme", "09:00–17:00 · svakog dana",
                        () -> open(store.modes.addWorkSchedule().id)), Ui.fill(this, 10));
            }
            if (night) {
                content.addView(presetCard("Noćno zaključavanje", "22:00–06:00 · svakog dana",
                        () -> open(store.modes.addNightSchedule().id)), Ui.fill(this, 10));
            }
        }
        content.addView(Ui.text(this, "Po vremenu telefona; pomeranje sata ne skraćuje režim. Period može da prelazi ponoć. Ako je aplikacija ili sajt u više uključenih režima, blokada važi kad god je bilo koji od njih aktivan.",
                13, Ui.MUTED, false), Ui.fill(this, 24));
    }

    /** Kartica režima: naziv i stanje u prvom redu, period i dani ispod, pa broj aplikacija i sajtova. */
    private View ruleCard(DailySchedule.Rule r) {
        List<DailySchedule.Rule> effective = effectiveRules(r.id);
        DailySchedule.Rule desired = store.modes.schedule(r.id);
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
        if (store.modes.scheduleActive(r.id)) top.addView(Ui.badge(this, "Traje sada", Ui.ACCENT), Ui.wrap(this, 8));
        top.addView(enabledNow ? Ui.badge(this, "Uključen", Charts.good()) : Ui.badge(this, "Isključen", Ui.MUTED),
                Ui.wrap(this, 8));
        card.addView(top);

        card.addView(Ui.text(this, effectiveDescription(effective), 15, enabledNow ? Ui.INK : Ui.MUTED, false),
                Ui.fill(this, 6));
        if (pending) card.addView(Ui.text(this, "Posle čekanja, od 06:00: " + (desired == null
                ? "režim se briše" : description(desired)), 13, Ui.ACCENT, false), Ui.fill(this, 6));
        card.setOnClickListener(v -> {
            if (desired != null) open(r.id);
            else new Sheet(this, "Brisanje režima je zakazano")
                    .message("Ovo još važi do kraja čekanja (06:00):\n" + effectiveDescription(effective)
                            + "\n\nZa odustajanje koristi karticu „Zakazane promene“ na listi režima.")
                    .secondary("Zatvori", null).show();
        });
        return card;
    }

    private List<DailySchedule.Rule> effectiveRules(String ruleId) {
        List<DailySchedule.Rule> out = new ArrayList<>();
        for (DailySchedule.Rule r : store.modes.schedulesNow()) if (r.id.equals(ruleId)) out.add(r);
        return out;
    }

    private boolean differs(List<DailySchedule.Rule> effective, DailySchedule.Rule desired) {
        if (desired == null || effective.size() != 1) return true;
        DailySchedule.Rule current = effective.get(0);
        return current.enabled != desired.enabled || current.start != desired.start || current.end != desired.end
                || current.days != desired.days
                || !current.apps.equals(desired.apps) || !current.sites.equals(desired.sites);
    }

    private String description(DailySchedule.Rule r) {
        return (r.enabled ? "" : "Isključen · ") + r.label() + " · " + r.daysLabel()
                + " · " + Ui.count(r.apps.size(), "aplikacija", "aplikacije", "aplikacija")
                + " · " + Ui.count(r.sites.size(), "sajt", "sajta", "sajtova");
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
        DailySchedule.Rule rule = store.modes.schedule(id);
        if (rule == null) {
            finish();
            return;
        }
        page(rule.name, "Izaberi period i dane i dodaj aplikacije i sajtove koji će tada biti blokirani. Dodavanje važi odmah. Izlazak iz režima se traži samo 06:00–10:00 i važi sutra u 06:00.");
        List<DailySchedule.Rule> effective = effectiveRules(id);
        if (differs(effective, rule)) {
            LinearLayout current = Ui.card(this);
            current.addView(Ui.text(this, "Važi sada", 16, Ui.INK, true));
            current.addView(Ui.text(this, effectiveDescription(effective), 14, Ui.MUTED, false), Ui.fill(this, 6));
            current.addView(Ui.text(this, "Podešavanja ispod važe u potpunosti kad istekne čekanje (u 06:00). "
                    + "Pooštravanje važi odmah.", 14, Ui.ACCENT, false), Ui.fill(this, 6));
            content.addView(current, Ui.fill(this, 12));
        }
        boolean active = store.modes.scheduleActive(id);
        if (active) {
            content.addView(Ui.text(this, "Režim je sada aktivan.", 14, Ui.ACCENT, true), Ui.fill(this, 12));
        }
        CheckRow enabled = new CheckRow(this, null, "Režim je uključen", "Blokira u izabranom periodu i danima");
        enabled.setChecked(rule.enabled);
        enabled.setListener(checked -> {
            DailySchedule.Rule now = store.modes.schedule(id);
            if (now != null && !store.modes.setSchedule(id, now.name, checked, now.start, now.end)) refused();
            else render();
        });
        content.addView(enabled, Ui.fill(this, 12));
        LinearLayout settings = Ui.group(this);
        Ui.groupRow(this, settings, "Naziv", rule.name, Safe.click("scheduleName", v -> editName(rule)));
        Ui.groupRow(this, settings, "Period", rule.label(), Safe.click("scheduleTime", v -> editTime(rule)));
        Ui.groupRow(this, settings, "Dani", rule.daysLabel(), Safe.click("scheduleDays", v -> editDays(rule)));
        content.addView(settings, Ui.fill(this, 10));
        content.addView(Ui.text(this, "Po vremenu telefona; pomeranje sata ne skraćuje režim. Period može da prelazi ponoć i tada pripada danu u kome počinje. Blokada traje do kraja perioda.", 13, Ui.MUTED, false), Ui.fill(this, 10));
        content.addView(Ui.text(this, "Režim blokira samo u svom periodu. Da aplikacija bude zaključana i ostatak dana, "
                + "uključi joj „Zaključaj“ na ekranu Pravila.", 13, Ui.MUTED, false), Ui.fill(this, 8));

        // Stavke uklonjene iz podešavanja koje važe do 06:00 ostaju na listi, sa oznakom.
        Set<String> appsNow = new HashSet<>(), sitesNow = new HashSet<>();
        for (DailySchedule.Rule r : effective) {
            appsNow.addAll(r.apps);
            sitesNow.addAll(r.sites);
        }
        android.content.pm.PackageManager pm = getPackageManager();
        Set<String> allApps = new HashSet<>(rule.apps);
        allApps.addAll(appsNow);
        List<String[]> apps = new ArrayList<>();
        for (String pkg : allApps) apps.add(new String[]{pkg, AppLabels.of(pm, pkg)});
        apps.sort((x, y) -> x[1].compareToIgnoreCase(y[1]));
        heading("Aplikacije", rule.apps.size());
        content.addView(Ui.text(this, "Dodavanje važi odmah. Izlazak iz režima se traži samo 06:00–10:00 i važi sutra u 06:00.",
                13, Ui.MUTED, false), Ui.fill(this, 4));
        LinearLayout appBox = Ui.group(this);
        if (apps.isEmpty()) note(appBox, "Još nema aplikacija u režimu.");
        for (String[] x : apps) {
            String pkg = x[0];
            itemRow(appBox, appIcon(pm, pkg, x[1]), x[1], !rule.apps.contains(pkg),
                    () -> { if (!store.modes.setScheduleApp(id, pkg, false)) refused(); else render(); },
                    () -> { store.modes.setScheduleApp(id, pkg, true); render(); });
        }
        addRow(appBox, "+  Dodaj aplikacije", () -> chooseApps(rule));
        content.addView(appBox, Ui.fill(this, 10));

        Set<String> allSites = new HashSet<>(rule.sites);
        allSites.addAll(sitesNow);
        List<String> sites = new ArrayList<>(allSites);
        sites.sort(String::compareTo);
        heading("Web sajtovi", rule.sites.size());
        LinearLayout siteBox = Ui.group(this);
        if (sites.isEmpty()) note(siteBox, "Još nema sajtova u režimu.");
        for (String domain : sites) {
            itemRow(siteBox, letterIcon(domain), domain, !rule.sites.contains(domain),
                    () -> { if (!store.modes.setScheduleSite(id, domain, false)) refused(); else render(); },
                    () -> { store.modes.setScheduleSite(id, domain, true); render(); });
        }
        addRow(siteBox, "+  Dodaj sajt", this::addSite);
        content.addView(siteBox, Ui.fill(this, 10));
        empty("Blokada sajta obuhvata i poddomene. Radi u podržanim pregledačima; za ostale dodaj ceo pregledač u režim.");

        TextView delete = Ui.text(this, "Obriši režim", 16, Ui.ACCENT, true);
        delete.setGravity(android.view.Gravity.CENTER);
        delete.setMinHeight(Ui.dp(this, 52));
        delete.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(this, 18)));
        delete.setOnClickListener(Safe.click("scheduleDelete", v -> new Sheet(this, "Obriši režim „" + rule.name + "“?")
                .message("Period i izbor aplikacija i sajtova ovog režima biće obrisani.")
                .secondary("Otkaži", null)
                .primary("Obriši", () -> {
                    if (store.modes.removeSchedule(id)) finish(); else refused();
                    return true;
                })
                .show()));
        content.addView(delete, Ui.fill(this, 28));
    }

    /** Naslov liste sa brojem stavki. */
    private void heading(String title, int n) {
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, title, 18, Ui.INK, true));
        if (n > 0) row.addView(Ui.badge(this, String.valueOf(n), Ui.ACCENT), Ui.wrap(this, 10));
        content.addView(row, Ui.fill(this, 26));
    }

    private void divider(LinearLayout group) {
        if (group.getChildCount() == 0) return;
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = Ui.dp(this, 66);
        group.addView(line, lp);
    }

    /**
     * Red stavke: ikonica, naziv i malo dugme. Ceo red se ne tapka, da se stavka ne ukloni slučajno.
     * Stavka koja je uklonjena a još važi nudi "Vrati" (pooštravanje, važi odmah).
     */
    private void itemRow(LinearLayout group, View icon, String name, boolean leaving, Runnable remove, Runnable restore) {
        divider(group);
        LinearLayout row = Ui.row(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        row.setPadding(Ui.dp(this, 16), p, Ui.dp(this, 12), p);
        int size = Ui.dp(this, 38);
        row.addView(icon, new LinearLayout.LayoutParams(size, size));
        LinearLayout texts = Ui.column(this);
        TextView title = Ui.text(this, name, 16, leaving ? Ui.MUTED : Ui.INK, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        if (leaving) texts.addView(Ui.text(this, "Uklonjeno, važi još do kraja čekanja", 13, Ui.ACCENT, false), Ui.fill(this, 3));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = Ui.dp(this, 12);
        row.addView(texts, tp);
        TextView action = Ui.text(this, leaving ? "Vrati" : "Ukloni", 13, leaving ? Ui.ACCENT : Ui.MUTED, true);
        action.setGravity(android.view.Gravity.CENTER);
        action.setMinHeight(Ui.dp(this, 36));
        int h = Ui.dp(this, 14);
        action.setPadding(h, 0, h, 0);
        int tint = leaving ? (Ui.ACCENT & 0x00FFFFFF) | 0x2E000000 : Ui.SOFT;
        action.setBackground(Ui.pressable(tint, Ui.SOFT_DOWN, Ui.dp(this, 18)));
        action.setOnClickListener(Safe.click("scheduleItem", v -> (leaving ? restore : remove).run()));
        row.addView(action, Ui.wrap(this, 10));
        group.addView(row, Ui.fill(this, 0));
    }

    /** Red za dodavanje na dnu liste, u boji akcenta. */
    private void addRow(LinearLayout group, String label, Runnable run) {
        divider(group);
        TextView add = Ui.text(this, label, 16, Ui.ACCENT, true);
        add.setGravity(android.view.Gravity.CENTER_VERTICAL);
        add.setMinHeight(Ui.dp(this, 54));
        add.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 16), 0);
        add.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, 0));
        add.setOnClickListener(Safe.click("scheduleAdd", v -> run.run()));
        group.addView(add, Ui.fill(this, 0));
    }

    private void note(LinearLayout group, String text) {
        TextView t = Ui.text(this, text, 14, Ui.MUTED, false);
        int p = Ui.dp(this, 16);
        t.setPadding(Ui.dp(this, 18), p, p, p);
        group.addView(t, Ui.fill(this, 0));
    }

    private View appIcon(android.content.pm.PackageManager pm, String pkg, String label) {
        try {
            android.widget.ImageView image = new android.widget.ImageView(this);
            image.setImageDrawable(pm.getApplicationIcon(pkg));
            return image;
        } catch (Exception ignored) {
            // aplikacija nije instalirana: prvo slovo umesto ikonice
            return letterIcon(label);
        }
    }

    /** Krug sa prvim slovom (sajt ili aplikacija bez ikonice). */
    private View letterIcon(String name) {
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        TextView t = Ui.text(this, "", 16, Ui.ACCENT, true);
        ((Ui.Label) t).setRaw(letter);
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackground(Ui.round((Ui.ACCENT & 0x00FFFFFF) | 0x2E000000, Ui.dp(this, 19)));
        return t;
    }

    /** Izlazak iz režima van 06:00–10:00 je odbijen: ekran se vraća na sačuvano stanje. */
    private void refused() {
        Ui.toast(this, "Izlazak iz režima (uklanjanje, isključivanje, kraći period, manje dana) može da se zatraži samo od 06:00 do 10:00, a važi sutra u 06:00.");
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
                        Ui.toast(this, "Izaberi bar jedan dan");
                        return false;
                    }
                    if (!store.modes.setScheduleDays(id, days[0])) refused(); else render();
                    return true;
                }).show();
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
                        name.setError(Ui.cyr("Unesi naziv, npr. Spavanje"));
                        return false;
                    }
                    DailySchedule.Rule now = store.modes.schedule(id);
                    if (now != null) store.modes.setSchedule(id, value, now.enabled, now.start, now.end);
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
                        Ui.toast(this, "Početak i kraj moraju biti različiti");
                        return false;
                    }
                    DailySchedule.Rule now = store.modes.schedule(id);
                    if (now != null && !store.modes.setSchedule(id, now.name, now.enabled, values[0], values[1])) refused();
                    else render();
                    return true;
                }).show();
    }

    private void chooseApps(DailySchedule.Rule rule) {
        Ui.toastShort(this, "Učitavam aplikacije…");
        AppPicker.load(this, items -> {
            if (items.isEmpty()) {
                Ui.toast(this, "Nije moguće učitati aplikacije");
                return;
            }
            // Već izabrane na vrh, ostale po abecedi.
            List<AppPicker.App> sorted = new ArrayList<>(items);
            sorted.sort((a, b) -> Boolean.compare(!rule.apps.contains(a.pkg), !rule.apps.contains(b.pkg)));
            showAppPicker(rule, sorted);
        });
    }

    private void showAppPicker(DailySchedule.Rule rule, List<AppPicker.App> items) {
        Set<String> selected = new HashSet<>(rule.apps);
        LinearLayout box = Ui.column(this);
        TextView count = Ui.text(this, "", 14, Ui.ACCENT, true);
        Runnable updateCount = () -> count.setText(selected.isEmpty()
                ? "Ništa nije izabrano" : "Izabrano: " + selected.size());
        updateCount.run();

        List<CheckRow> rows = AppPicker.addList(this, box, items, count);
        for (int i = 0; i < items.size(); i++) {
            String pkg = items.get(i).pkg;
            CheckRow row = rows.get(i);
            row.setChecked(selected.contains(pkg));
            row.setListener(checked -> {
                if (checked) selected.add(pkg); else selected.remove(pkg);
                updateCount.run();
            });
        }

        new Sheet(this, "Aplikacije u režimu „" + rule.name + "“")
                .message("Izabrane aplikacije biće blokirane " + rule.daysLabel() + " u periodu " + rule.label() + ".")
                .view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    boolean ok = true;
                    DailySchedule.Rule current = store.modes.schedule(id);
                    if (current != null) {
                        for (AppPicker.App it : items) {
                            boolean want = selected.contains(it.pkg);
                            if (want != current.apps.contains(it.pkg)) ok &= store.modes.setScheduleApp(id, it.pkg, want);
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
                    String host = Hosts.hostOf(address.getText().toString());
                    if (host == null || !host.contains(".") || !host.matches("[a-z0-9\\p{L}.-]+")) {
                        address.setError(Ui.cyr("Unesi adresu sajta, npr. youtube.com"));
                        return false;
                    }
                    store.modes.setScheduleSite(id, host, true);
                    render();
                    return true;
                }).show();
    }
}
