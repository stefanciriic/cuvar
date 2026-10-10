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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sva pravila na jednom mestu: za svaku aplikaciju i sajt piše u kojim režimima je blokirana
 * i šta važi ostatak dana (zaključavanje, dnevni limit). Tap otvara sva podešavanja te stavke.
 */
public class RulesActivity extends SubActivity {

    private LinearLayout content;

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        root.addView(header("Sva pravila", "Narandžasto: režim koji blokira. Ispod: šta važi ostatak dana."));
        content = Ui.column(this);
        int p = Ui.dp(this, 20);
        content.setPadding(p, 0, p, p);
        root.addView(content);
        scroll.addView(root);
        setContentView(scroll);

        TextView how = Ui.text(this, "Kako se pravila slažu  ›", 14, Ui.ACCENT, true);
        how.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, Ui.dp(this, 6));
        how.setOnClickListener(v -> new Sheet(this, "Kako se pravila slažu").message(
                "1. Režim je najjači: u svom periodu blokira i ne može da se otključa.\n"
                + "2. Ostatak dana: zaključano se otvara samo dnevnom šifrom (17:00 do 22:00), a potrošen limit se ne otvara do ponoći.\n"
                + "3. Ukupni dnevni limit, kad se potroši, zaključava sve ovo do ponoći.\n"
                + "4. Strože pravilo važi odmah, a blaže tek sutra od 06:00.\n"
                + "5. Od 22:00 do 06:00 sve ovo je zaključano (noćna blokada).\n"
                + "6. Ručni Fokus je zaseban: dok traje, dozvoljene su samo izabrane aplikacije i sajtovi.")
                .secondary("Zatvori", null).show());
        content.addView(how, Ui.fill(this, 4));

        LinearLayout adds = Ui.row(this);
        TextView addApp = Ui.button(this, "+ Aplikacija", true);
        addApp.setOnClickListener(v -> pickApp());
        TextView addSite = Ui.button(this, "+ Sajt", true);
        addSite.setOnClickListener(v -> addSite());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        adds.addView(addApp, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = Ui.dp(this, 10);
        adds.addView(addSite, half2);
        content.addView(adds, Ui.fill(this, 14));

        List<DailySchedule.Rule> rules = store.schedulesNow();
        List<DailySchedule.Rule> next = store.schedules();
        PackageManager pm = getPackageManager();

        // 1. Šta važi upravo sada.
        content.addView(Ui.section(this, "Sada"), Ui.fill(this, 22));
        LinearLayout now = Ui.card(this);
        boolean any = false;
        if (store.nightActive()) {
            now.addView(Ui.text(this, "⛔ Noćna blokada do 06:00: sve ispod je zaključano", 15, Ui.ACCENT, true));
            any = true;
        }
        for (DailySchedule.Rule r : store.activeSchedules()) {
            int n = items(r, pm, null).size();
            if (n == 0) continue;
            now.addView(Ui.text(this, "⛔ " + r.name + " do " + hm(r.end) + ": blokirano "
                    + Ui.count(n, "stavka", "stavke", "stavki"), 15, Ui.ACCENT, true), Ui.fill(this, any ? 6 : 0));
            any = true;
        }
        if (!any) now.addView(Ui.text(this, "Nijedan režim sada ne blokira", 15, Ui.INK, true));
        now.addView(Ui.text(this, store.dailyCodeVisible() ? "Zaključano se sada otvara dnevnom šifrom (do 22:00)"
                : "Zaključano se otvara dnevnom šifrom od 17:00 do 22:00", 13, Ui.MUTED, false), Ui.fill(this, 6));
        content.addView(now, Ui.fill(this, 8));

        // 2. Režimi, svaki jednom, sa onim što blokira.
        content.addView(Ui.section(this, "Režimi"), Ui.fill(this, 26));
        boolean anyRule = false;
        for (DailySchedule.Rule r : rules) {
            if (!r.enabled) continue;
            anyRule = true;
            DailySchedule.Rule d = null;
            for (DailySchedule.Rule x : next) if (x.id.equals(r.id)) d = x;
            content.addView(regimeCard(r, d, pm), Ui.fill(this, 10));
        }
        if (!anyRule) empty("Nema uključenih režima. Napravi ih u Vremenskim režimima.");

        // 3. Ostatak dana: zaključavanja i limiti van režima.
        content.addView(Ui.section(this, "Ostatak dana"), Ui.fill(this, 26));
        Set<String> apps = new LinkedHashSet<>(store.appListNow());
        apps.addAll(store.appList());
        Set<String> sites = new LinkedHashSet<>(store.siteListNow());
        sites.addAll(store.siteList());
        for (DailySchedule.Rule r : rules) {
            if (r.enabled && r.code) { apps.addAll(r.apps); sites.addAll(r.sites); }
        }
        // Sajt povezan sa instaliranom aplikacijom ide u isti red kao aplikacija.
        for (String dom : new ArrayList<>(sites)) {
            String pkg = installedApp(pm, dom);
            if (pkg != null) { apps.add(pkg); sites.remove(dom); }
        }
        for (String pkg : apps) {
            String dom = store.linkedSite(pkg);
            if (dom != null) sites.remove(dom);
        }
        if (apps.isEmpty() && sites.isEmpty()) empty("Van režima ništa nije zaključano ni ograničeno.");
        LinearLayout group = null;
        List<String[]> sorted = new ArrayList<>();
        for (String pkg : apps) sorted.add(new String[]{pkg, appLabel(pm, pkg)});
        sorted.sort((x, y) -> x[1].compareToIgnoreCase(y[1]));
        for (String[] x : sorted) {
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(x[0]); } catch (Exception ignored) { }
            String dom = store.linkedSite(x[0]);
            Has has = r -> r.apps.contains(x[0]) || (dom != null && r.sites.contains(dom));
            List<String> cur = appChips(x[0], true, hasCode(rules, has));
            List<String> nxt = appChips(x[0], false, hasCode(next, has));
            String title = dom == null ? x[1] : x[1] + "  +  " + dom;
            group = addRow(group, title, icon, cur, nxt, v -> editApp(x[0], x[1]));
        }
        List<String> siteSorted = new ArrayList<>(sites);
        siteSorted.sort(String::compareTo);
        for (String dom : siteSorted) {
            Has has = r -> r.sites.contains(dom);
            group = addRow(group, dom, null, siteChips(dom, true, hasCode(rules, has)),
                    siteChips(dom, false, hasCode(next, has)), v -> editSite(dom));
        }
        empty("Tapni režim da mu menjaš vreme i stavke, a aplikaciju ili sajt da mu menjaš zaključavanje i limit.");
    }

    /** Kartica režima: naziv i vreme, pa šta blokira; stavke koje otpadaju od sutra su označene. */
    private View regimeCard(DailySchedule.Rule r, DailySchedule.Rule d, PackageManager pm) {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(this, 18)));
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        LinearLayout top = Ui.row(this);
        TextView name = Ui.text(this, r.name, 17, Ui.INK, true);
        top.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(Ui.text(this, shortTime(r), 15, Ui.ACCENT, true), Ui.wrap(this, 8));
        card.addView(top);
        if (d == null || !d.enabled) {
            card.addView(Ui.text(this, "Isključuje se sutra u 06:00", 13, Ui.ACCENT, false), Ui.fill(this, 4));
        } else if (!shortTime(d).equals(shortTime(r))) {
            card.addView(Ui.text(this, "Od sutra: " + shortTime(d), 13, Ui.ACCENT, false), Ui.fill(this, 4));
        }
        List<String> keep = new ArrayList<>(), going = new ArrayList<>();
        Set<String> kept = items(d != null && d.enabled ? d : null, pm, null);
        for (String item : items(r, pm, null)) (kept.contains(item) ? keep : going).add(item);
        keep.sort(String::compareToIgnoreCase);
        String items = keep.isEmpty() ? "Još ništa ne blokira" : android.text.TextUtils.join(", ", keep);
        card.addView(Ui.text(this, items, 14, Ui.MUTED, false), Ui.fill(this, 6));
        if (d != null && d.enabled && !going.isEmpty()) {
            card.addView(Ui.text(this, "Ukida se sutra u 06:00: " + android.text.TextUtils.join(", ", going),
                    13, Ui.ACCENT, false), Ui.fill(this, 4));
        }
        if (r.code) {
            card.addView(Ui.text(this, "Posle perioda ostaje zaključano, otvara se šifrom", 13, Ui.MUTED, false),
                    Ui.fill(this, 4));
        }
        card.setOnClickListener(v -> startActivity(new Intent(this, ScheduleActivity.class).putExtra("id", r.id)));
        return card;
    }

    /** Red u grupi „Ostatak dana“: ikona, naziv, kratka pravila i, ako ih ima, popuštanje od sutra. */
    private LinearLayout addRow(LinearLayout group, String title, Drawable icon, List<String> cur, List<String> nxt,
                                View.OnClickListener onClick) {
        if (cur.isEmpty() && nxt.isEmpty()) return group;
        if (group == null) {
            group = Ui.column(this);
            group.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 18)));
            group.setClipToOutline(true);
            content.addView(group, Ui.fill(this, 8));
        } else {
            View line = new View(this);
            line.setBackgroundColor(Ui.LINE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
            lp.leftMargin = Ui.dp(this, 16);
            group.addView(line, lp);
        }
        List<Line> lines = new ArrayList<>();
        restLines(lines, cur, nxt);
        View row = itemCard(title, icon, lines, onClick);
        row.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, 0));
        group.addView(row);
        return group;
    }

    // ---------- Prikaz ----------

    /** Jedan red opisa: tekst i da li je naglašen (blokada) ili prigušen. */
    private static final class Line {
        final String text;
        final boolean strong;

        Line(String text, boolean strong) {
            this.text = text;
            this.strong = strong;
        }
    }

    private interface Has {
        boolean test(DailySchedule.Rule r);
    }

    private static boolean hasCode(List<DailySchedule.Rule> rules, Has has) {
        for (DailySchedule.Rule r : rules) if (r.enabled && r.code && has.test(r)) return true;
        return false;
    }

    /** Nazivi stavki režima; aplikacija i njen povezan sajt su jedna stavka. */
    private Set<String> items(DailySchedule.Rule r, PackageManager pm, Set<String> out) {
        if (out == null) out = new LinkedHashSet<>();
        if (r == null) return out;
        Set<String> covered = new HashSet<>();
        for (String pkg : r.apps) {
            out.add(appLabel(pm, pkg));
            String dom = store.linkedSite(pkg);
            if (dom != null) covered.add(dom);
        }
        for (String s : r.sites) {
            if (covered.contains(s)) continue;
            String pkg = installedApp(pm, s);
            out.add(pkg == null ? s : appLabel(pm, pkg));
        }
        return out;
    }

    /** Instalirana aplikacija povezana sa sajtom, ili null. */
    private String installedApp(PackageManager pm, String domain) {
        for (String pkg : store.linkedApps(domain)) {
            try {
                pm.getApplicationInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static int minLimit(int a, int b) {
        if (a <= 0) return Math.max(0, b);
        if (b <= 0) return a;
        return Math.min(a, b);
    }

    /** "09–17" ili "22:30–06" i dani samo kad nisu svi. */
    private static String shortTime(DailySchedule.Rule r) {
        String t = hm(r.start) + "–" + hm(r.end);
        return r.days == DailySchedule.ALL_DAYS ? t : t + " " + r.daysLabel();
    }

    private static String hm(int minute) {
        return minute % 60 == 0 ? String.format(Locale.ROOT, "%02d", minute / 60) : DailySchedule.label(minute);
    }

    /** Ostatak pravila kao kratki delovi; odloženo popuštanje kao jedan red „Od sutra“. */
    private void restLines(List<Line> out, List<String> now, List<String> next) {
        String joined = android.text.TextUtils.join("  ·  ", now);
        if (!now.isEmpty()) out.add(new Line(joined, false));
        else if (out.isEmpty()) out.add(new Line("Bez ograničenja", false));
        if (!now.equals(next)) {
            out.add(new Line("Od sutra 06:00: " + (next.isEmpty() ? "bez ovih ograničenja"
                    : android.text.TextUtils.join("  ·  ", next)), false));
        }
    }

    private List<String> appChips(String pkg, boolean effective, boolean code) {
        List<String> parts = new ArrayList<>();
        if (code || (effective ? store.appLockNow(pkg) : store.appLockLinked(pkg))) parts.add("🔒 šifrom");
        int limit = effective ? store.appLimitNow(pkg) : store.appLimitLinked(pkg);
        if (limit > 0) parts.add(limit + " min/dan" + used(store.usedShared(pkg), effective));
        int opens = effective ? store.appOpensNow(pkg) : store.appOpens(pkg);
        if (opens > 0) parts.add(opens + "× dnevno");
        int session = effective ? store.appSessionNow(pkg) : store.appSession(pkg);
        if (session > 0) parts.add("do " + session + " min u komadu");
        return parts;
    }

    private List<String> siteChips(String domain, boolean effective, boolean code) {
        List<String> parts = new ArrayList<>();
        int limit = effective ? store.siteLimitNow(domain) : store.siteLimitLinked(domain);
        if (limit == 0) parts.add("🔒 uvek, šifrom");
        else if (code) parts.add("🔒 šifrom");
        if (limit > 0) parts.add(limit + " min/dan" + used(store.usedShared("site:" + domain), effective));
        return parts;
    }

    /** Potrošnja danas, samo u važećem prikazu i samo kad je ima. */
    private static String used(long ms, boolean effective) {
        return effective && ms >= 60000L ? " (danas " + Ui.fmt(ms) + ")" : "";
    }

    private View itemCard(String title, Drawable icon, List<Line> lines, View.OnClickListener onClick) {
        LinearLayout card = Ui.row(this);
        card.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(this, 18)));
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        card.setGravity(android.view.Gravity.TOP);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            int s = Ui.dp(this, 36);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.rightMargin = Ui.dp(this, 14);
            card.addView(iv, lp);
        }
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 17, Ui.INK, true));
        for (Line l : lines) {
            texts.addView(Ui.text(this, l.text, 13, l.strong ? Ui.ACCENT : Ui.MUTED, l.strong), Ui.fill(this, 4));
        }
        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.setOnClickListener(onClick);
        return card;
    }

    private void empty(String text) {
        content.addView(Ui.text(this, text, 13, Ui.MUTED, false), Ui.fill(this, 10));
    }

    private static String appLabel(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    // ---------- Izmena ----------

    /** Štikle za režime u prozoru za izmenu; vraća ih da bi se pri čuvanju pročitale. */
    private List<CheckRow> ruleChecks(LinearLayout box, List<DailySchedule.Rule> rules, Set<String> selectedIds,
                                      Set<String> enforcedIds) {
        List<CheckRow> rows = new ArrayList<>();
        box.addView(Sheet.label(this, "Blokiraj u režimima"), Ui.fill(this, 18));
        if (rules.isEmpty()) {
            box.addView(Ui.text(this, "Još nema režima. Napravi ih u Vremenskim režimima.", 13, Ui.MUTED, false),
                    Ui.fill(this, 6));
        }
        for (DailySchedule.Rule r : rules) {
            String sub = r.label() + " · " + r.daysLabel() + (r.enabled ? "" : " · isključen");
            if (!selectedIds.contains(r.id) && enforcedIds.contains(r.id)) sub += "\nUklonjeno, važi još do sutra u 06:00";
            CheckRow row = new CheckRow(this, null, r.name, sub);
            row.setChecked(selectedIds.contains(r.id));
            rows.add(row);
            box.addView(row, Ui.fill(this, 6));
        }
        return rows;
    }

    private void pendingToast() {
        if (!store.pendingChanges(getPackageManager()).isEmpty()) {
            Toast.makeText(this, "Pooštravanje važi odmah, a popuštanje tek sutra od 06:00.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean saveRules(List<DailySchedule.Rule> rules, List<CheckRow> rows, String key, boolean site) {
        boolean ok = true;
        for (int i = 0; i < rules.size(); i++) {
            String id = rules.get(i).id;
            boolean on = rows.get(i).isChecked();
            ok &= site ? store.setScheduleSite(id, key, on) : store.setScheduleApp(id, key, on);
        }
        if (!ok) {
            Toast.makeText(this, "Režim koji sada traje ne može da izgubi stavku dok traje", Toast.LENGTH_LONG).show();
        }
        return ok;
    }

    private void editApp(String pkg, String label) {
        List<DailySchedule.Rule> rules = store.schedules();
        String dom = store.linkedSite(pkg);
        int site = dom == null ? -1 : store.siteLimit(dom);
        Set<String> in = new HashSet<>();
        for (DailySchedule.Rule r : rules) if (r.apps.contains(pkg) || (dom != null && r.sites.contains(dom))) in.add(r.id);
        Set<String> now = new HashSet<>();
        for (DailySchedule.Rule r : store.schedulesNow()) {
            if (r.enabled && (r.apps.contains(pkg) || (dom != null && r.sites.contains(dom)))) now.add(r.id);
        }

        LinearLayout box = Ui.column(this);
        box.addView(Sheet.label(this, "Sajt ove aplikacije (pravila i vreme su zajednički)"));
        EditText siteBox = Sheet.input(this, "npr. linkedin.com, prazno = bez sajta", false);
        if (dom != null) siteBox.setText(dom);
        box.addView(siteBox, Ui.fill(this, 6));
        List<CheckRow> rows = ruleChecks(box, rules, in, now);
        box.addView(Sheet.label(this, "Ostatak dana"), Ui.fill(this, 18));
        CheckRow lock = new CheckRow(this, null, "Zaključaj", "Otvara se samo dnevnom šifrom, od 17:00 do 22:00");
        lock.setChecked(store.appLock(pkg) || site == 0);
        box.addView(lock, Ui.fill(this, 6));
        box.addView(Sheet.label(this, "Dnevni limit u minutima (0 = bez limita)"), Ui.fill(this, 14));
        EditText limit = Sheet.input(this, "0", true);
        limit.setText(String.valueOf(minLimit(store.appLimit(pkg), Math.max(0, site))));
        box.addView(limit, Ui.fill(this, 6));
        box.addView(Sheet.label(this, "Najviše otvaranja dnevno (0 = bez ograničenja)"), Ui.fill(this, 14));
        EditText opens = Sheet.input(this, "0", true);
        opens.setText(String.valueOf(store.appOpens(pkg)));
        box.addView(opens, Ui.fill(this, 6));
        box.addView(Sheet.label(this, "Najduže u komadu, u minutima, pa pauza od "
                + Store.SESSION_BREAK_MS / 60000L + " min (0 = bez)"), Ui.fill(this, 14));
        EditText session = Sheet.input(this, "0", true);
        session.setText(String.valueOf(store.appSession(pkg)));
        box.addView(session, Ui.fill(this, 6));
        box.addView(Ui.text(this, "Danas korišćeno: " + Ui.fmt(store.usedShared(pkg)) + ", otvoreno "
                + Ui.count(store.opensToday("app:" + pkg), "put", "puta", "puta"), 13, Ui.MUTED, false),
                Ui.fill(this, 8));

        Sheet sheet = new Sheet(this, label).view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    boolean wantLock = lock.isChecked();
                    Integer wantLimit = Ui.nonNegativeNumber(limit);
                    if (wantLimit == null) return false;
                    Integer wantOpens = Ui.nonNegativeNumber(opens);
                    if (wantOpens == null) return false;
                    Integer wantSession = Ui.nonNegativeNumber(session);
                    if (wantSession == null) return false;
                    String typed = siteBox.getText().toString().trim();
                    String wantSite = typed.isEmpty() ? null : Store.hostOf(typed);
                    if (!typed.isEmpty() && (wantSite == null || !wantSite.contains("."))) {
                        siteBox.setError("Unesi adresu sajta, npr. linkedin.com");
                        return false;
                    }
                    boolean adding = wantLock || wantLimit > 0 || wantOpens > 0 || wantSession > 0;
                    for (CheckRow r : rows) adding |= r.isChecked();
                    saveGuarding(pkg, label, adding, () -> {
                        if (!java.util.Objects.equals(wantSite, dom)) store.setLink(pkg, wantSite);
                        saveRules(rules, rows, pkg, false);
                        // Odštiklan režim skida i sajt, ako je bio u njemu.
                        if (dom != null && dom.equals(wantSite)) {
                            for (int i = 0; i < rules.size(); i++) {
                                if (!rows.get(i).isChecked() && rules.get(i).sites.contains(dom)
                                        && !store.setScheduleSite(rules.get(i).id, dom, false)) busy();
                            }
                        }
                        if (wantLock != store.appLock(pkg) || wantLimit != store.appLimit(pkg) || wantOpens != store.appOpens(pkg)
                                || wantSession != store.appSession(pkg)) {
                            store.setApp(pkg, wantLock, wantLimit, wantOpens, wantSession);
                        }
                        // Sopstveno pravilo sajta prati aplikaciju, da i ekran Sajtovi pokazuje isto.
                        if (dom != null && dom.equals(wantSite) && site >= 0) {
                            if (wantLock) store.setSite(dom, 0);
                            else if (wantLimit > 0) store.setSite(dom, wantLimit);
                            else store.removeSite(dom);
                        }
                        pendingToast();
                        render();
                    });
                    return true;
                });
        if (store.hasAppRule(pkg) || !in.isEmpty() || site >= 0) {
            sheet.danger("Ukloni iz pravila", () -> {
                for (DailySchedule.Rule r : rules) {
                    if (r.apps.contains(pkg) && !store.setScheduleApp(r.id, pkg, false)) busy();
                    if (dom != null && r.sites.contains(dom) && !store.setScheduleSite(r.id, dom, false)) busy();
                }
                store.setApp(pkg, false, 0, 0, 0);
                if (site >= 0) store.removeSite(dom);
                pendingToast();
                render();
                return true;
            });
        }
        sheet.show();
    }

    private void busy() {
        Toast.makeText(this, "Režim koji sada traje ne može da izgubi stavku dok traje", Toast.LENGTH_LONG).show();
    }

    private void editSite(String domain) {
        String app = installedApp(getPackageManager(), domain);
        if (app != null) {
            editApp(app, appLabel(getPackageManager(), app));
            return;
        }
        List<DailySchedule.Rule> rules = store.schedules();
        Set<String> in = new HashSet<>();
        for (DailySchedule.Rule r : rules) if (r.sites.contains(domain)) in.add(r.id);
        Set<String> now = new HashSet<>();
        for (DailySchedule.Rule r : store.schedulesNow()) if (r.enabled && r.sites.contains(domain)) now.add(r.id);
        int current = store.siteLimit(domain);

        LinearLayout box = Ui.column(this);
        List<CheckRow> rows = ruleChecks(box, rules, in, now);
        box.addView(Sheet.label(this, "Ostatak dana"), Ui.fill(this, 18));
        CheckRow listed = new CheckRow(this, null, "Ograniči i van režima",
                "Dnevni limit ispod; 0 znači uvek blokiran (otvara se dnevnom šifrom)");
        listed.setChecked(current >= 0);
        box.addView(listed, Ui.fill(this, 6));
        box.addView(Sheet.label(this, "Dnevni limit u minutima"), Ui.fill(this, 14));
        EditText limit = Sheet.input(this, "0", true);
        limit.setText(String.valueOf(Math.max(0, current)));
        box.addView(limit, Ui.fill(this, 6));
        if (current > 0) {
            box.addView(Ui.text(this, "Danas: " + Ui.fmt(store.usedShared("site:" + domain)), 13, Ui.MUTED, false),
                    Ui.fill(this, 8));
        }

        Sheet sheet = new Sheet(this, domain).view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    Integer want = -1;
                    if (listed.isChecked()) want = Ui.nonNegativeNumber(limit);
                    if (want == null) return false;
                    saveRules(rules, rows, domain, true);
                    if (want != current) {
                        if (want < 0) store.removeSite(domain); else store.setSite(domain, want);
                    }
                    pendingToast();
                    render();
                    return true;
                });
        if (current >= 0 || !in.isEmpty()) {
            sheet.danger("Ukloni iz pravila", () -> {
                for (DailySchedule.Rule r : rules) if (in.contains(r.id) && !store.setScheduleSite(r.id, domain, false)) busy();
                if (current >= 0) store.removeSite(domain);
                pendingToast();
                render();
                return true;
            });
        }
        sheet.show();
    }

    private void addSite() {
        EditText address = Sheet.input(this, "npr. youtube.com", false);
        new Sheet(this, "Dodaj sajt")
                .message("Blokada obuhvata i poddomene. Zatim biraš režime i limit.")
                .view(address)
                .secondary("Otkaži", null)
                .primary("Dalje", () -> {
                    String host = Store.hostOf(address.getText().toString());
                    if (host == null || !host.contains(".") || !host.matches("[a-z0-9\\p{L}.-]+")) {
                        address.setError("Unesi adresu sajta, npr. youtube.com");
                        return false;
                    }
                    editSite(host);
                    return true;
                }).show();
    }

    private void pickApp() {
        Toast.makeText(this, "Učitavam aplikacije…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<String[]> items = new ArrayList<>();
            List<Drawable> icons = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                Set<String> seen = new HashSet<>();
                List<ResolveInfo> found = pm.queryIntentActivities(intent, 0);
                found.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
                for (ResolveInfo info : found) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    items.add(new String[]{pkg, info.loadLabel(pm).toString()});
                    Drawable d = null;
                    try { d = info.loadIcon(pm); } catch (Throwable ignored) { }
                    icons.add(d);
                }
            } catch (Exception ignored) { }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                showAppPicker(items, icons);
            });
        }).start();
    }

    private void showAppPicker(List<String[]> items, List<Drawable> icons) {
        LinearLayout box = Ui.column(this);
        EditText search = Sheet.input(this, "Pretraži aplikacije", false);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(search);
        Sheet sheet = new Sheet(this, "Izaberi aplikaciju").view(box).secondary("Otkaži", null);
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String[] it = items.get(i);
            CheckRow row = new CheckRow(this, icons.get(i), it[1], null);
            row.setListener(checked -> {
                sheet.dismiss();
                editApp(it[0], it[1]);
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
                    boolean show = q.isEmpty() || items.get(i)[1].toLowerCase(Locale.ROOT).contains(q);
                    rows.get(i).setVisibility(show ? View.VISIBLE : View.GONE);
                }
            }
        });
        sheet.show();
    }
}
