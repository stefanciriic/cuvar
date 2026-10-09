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
        root.addView(header("Sva pravila", "Za svaku aplikaciju i sajt: kada je blokiran i šta važi ostatak dana."));
        content = Ui.column(this);
        int p = Ui.dp(this, 20);
        content.setPadding(p, 0, p, p);
        root.addView(content);
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout how = Ui.card(this);
        how.addView(Ui.text(this, "Kako se slažu", 15, Ui.INK, true));
        how.addView(Ui.text(this, "1. Režim je najjači: u svom periodu blokira i ne može da se otključa.\n"
                + "2. Ostatak dana: zaključano se otvara samo dnevnom šifrom (17:00 do 22:00), a potrošen limit se ne otvara do ponoći.\n"
                + "3. Ukupni dnevni limit, kad se potroši, zaključava sve ovo do ponoći.\n"
                + "4. Strože pravilo važi odmah, a blaže tek sutra od 06:00.\n5. Od 22:00 do 06:00 sve ovo je zaključano (noćna blokada).",
                14, Ui.MUTED, false), Ui.fill(this, 6));
        content.addView(how, Ui.fill(this, 10));
        View pending = MainActivity.pendingCard(this, store, this::render);
        if (pending != null) content.addView(pending, Ui.fill(this, 10));

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

        List<DailySchedule.Rule> rules = store.schedules();

        Set<String> apps = new LinkedHashSet<>();
        for (DailySchedule.Rule r : rules) apps.addAll(r.apps);
        for (String pkg : store.appList()) apps.add(pkg);
        content.addView(Ui.section(this, "Aplikacije"), Ui.fill(this, 26));
        if (apps.isEmpty()) empty("Još nema pravila za aplikacije.");
        PackageManager pm = getPackageManager();
        List<String[]> sorted = new ArrayList<>();
        for (String pkg : apps) sorted.add(new String[]{pkg, appLabel(pm, pkg)});
        sorted.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        for (String[] a : sorted) {
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(a[0]); } catch (Exception ignored) { }
            content.addView(itemCard(a[1], icon, appLines(a[0], rules), v -> editApp(a[0], a[1])), Ui.fill(this, 10));
        }

        Set<String> sites = new LinkedHashSet<>(store.siteList());
        for (DailySchedule.Rule r : rules) sites.addAll(r.sites);
        content.addView(Ui.section(this, "Sajtovi"), Ui.fill(this, 26));
        if (sites.isEmpty()) empty("Još nema pravila za sajtove.");
        List<String> siteSorted = new ArrayList<>(sites);
        siteSorted.sort(String::compareTo);
        for (String d : siteSorted) {
            content.addView(itemCard(d, null, siteLines(d, rules), v -> editSite(d)), Ui.fill(this, 10));
        }
        empty("Sajtovi rade u podržanim pregledačima. Blokada sajta obuhvata i poddomene: youtube.com pokriva i m.youtube.com, a m.youtube.com ne pokriva www.youtube.com.");
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

    private List<Line> appLines(String pkg, List<DailySchedule.Rule> rules) {
        List<Line> out = new ArrayList<>();
        boolean code = false;
        for (DailySchedule.Rule r : rules) {
            if (!r.apps.contains(pkg)) continue;
            out.add(ruleLine(r));
            code |= r.enabled && r.code;
        }
        String rest = out.isEmpty() ? "Ceo dan: " : "Ostatak dana: ";
        List<String> parts = new ArrayList<>();
        if (code || store.appLock(pkg)) parts.add("zaključana, otvara se dnevnom šifrom");
        int limit = store.appLimit(pkg);
        if (limit > 0) parts.add("limit " + limit + " min (danas " + Ui.fmt(store.usedToday(pkg)) + ")");
        int opens = store.appOpens(pkg);
        if (opens > 0) parts.add("najviše " + Ui.count(opens, "otvaranje", "otvaranja", "otvaranja")
                + " (danas " + store.opensToday("app:" + pkg) + ")");
        int session = store.appSession(pkg);
        if (session > 0) parts.add("najviše " + session + " min u komadu");
        out.add(new Line(rest + (parts.isEmpty() ? "slobodno" : android.text.TextUtils.join(", ", parts)), false));
        return out;
    }

    private List<Line> siteLines(String domain, List<DailySchedule.Rule> rules) {
        List<Line> out = new ArrayList<>();
        boolean code = false;
        for (DailySchedule.Rule r : rules) {
            if (!r.sites.contains(domain)) continue;
            out.add(ruleLine(r));
            code |= r.enabled && r.code;
        }
        String rest = out.isEmpty() ? "Ceo dan: " : "Ostatak dana: ";
        int limit = store.siteLimit(domain);
        String s;
        if (limit == 0) s = "uvek blokiran, otvara se dnevnom šifrom";
        else if (limit > 0) s = "limit " + limit + " min (danas " + Ui.fmt(store.usedToday("site:" + domain)) + ")";
        else s = "slobodno";
        if (code && limit != 0) s = (limit < 0 ? "" : s + ", ") + "zaključan, otvara se dnevnom šifrom";
        out.add(new Line(rest + s, false));
        return out;
    }

    private Line ruleLine(DailySchedule.Rule r) {
        String t = r.label() + " " + r.daysLabel() + ": blokirano („" + r.name + "“)";
        return r.enabled ? new Line(t, true) : new Line(t + ", režim je isključen", false);
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
    private List<CheckRow> ruleChecks(LinearLayout box, List<DailySchedule.Rule> rules, Set<String> selectedIds) {
        List<CheckRow> rows = new ArrayList<>();
        box.addView(Sheet.label(this, "Blokiraj u režimima"), Ui.fill(this, 18));
        if (rules.isEmpty()) {
            box.addView(Ui.text(this, "Još nema režima. Napravi ih u Vremenskim režimima.", 13, Ui.MUTED, false),
                    Ui.fill(this, 6));
        }
        for (DailySchedule.Rule r : rules) {
            CheckRow row = new CheckRow(this, null, r.name, r.label() + " · " + r.daysLabel()
                    + (r.enabled ? "" : " · isključen"));
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
        Set<String> in = new HashSet<>();
        for (DailySchedule.Rule r : rules) if (r.apps.contains(pkg)) in.add(r.id);

        LinearLayout box = Ui.column(this);
        List<CheckRow> rows = ruleChecks(box, rules, in);
        box.addView(Sheet.label(this, "Ostatak dana"), Ui.fill(this, 18));
        CheckRow lock = new CheckRow(this, null, "Zaključaj", "Otvara se samo dnevnom šifrom, od 17:00 do 22:00");
        lock.setChecked(store.appLock(pkg));
        box.addView(lock, Ui.fill(this, 6));
        box.addView(Sheet.label(this, "Dnevni limit u minutima (0 = bez limita)"), Ui.fill(this, 14));
        EditText limit = Sheet.input(this, "0", true);
        limit.setText(String.valueOf(store.appLimit(pkg)));
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
        box.addView(Ui.text(this, "Danas korišćeno: " + Ui.fmt(store.usedToday(pkg)) + ", otvoreno "
                + Ui.count(store.opensToday("app:" + pkg), "put", "puta", "puta"), 13, Ui.MUTED, false),
                Ui.fill(this, 8));

        new Sheet(this, label).view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    saveRules(rules, rows, pkg, false);
                    boolean wantLock = lock.isChecked();
                    int wantLimit = Ui.parseInt(limit.getText().toString());
                    int wantOpens = Ui.parseInt(opens.getText().toString());
                    int wantSession = Ui.parseInt(session.getText().toString());
                    if (wantLock != store.appLock(pkg) || wantLimit != store.appLimit(pkg) || wantOpens != store.appOpens(pkg)
                            || wantSession != store.appSession(pkg)) {
                        store.setApp(pkg, wantLock, wantLimit, wantOpens, wantSession);
                    }
                    pendingToast();
                    render();
                    return true;
                }).show();
    }

    private void editSite(String domain) {
        List<DailySchedule.Rule> rules = store.schedules();
        Set<String> in = new HashSet<>();
        for (DailySchedule.Rule r : rules) if (r.sites.contains(domain)) in.add(r.id);
        int current = store.siteLimit(domain);

        LinearLayout box = Ui.column(this);
        List<CheckRow> rows = ruleChecks(box, rules, in);
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
            box.addView(Ui.text(this, "Danas: " + Ui.fmt(store.usedToday("site:" + domain)), 13, Ui.MUTED, false),
                    Ui.fill(this, 8));
        }

        new Sheet(this, domain).view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    saveRules(rules, rows, domain, true);
                    int want = listed.isChecked() ? Ui.parseInt(limit.getText().toString()) : -1;
                    if (want != current) {
                        if (want < 0) store.removeSite(domain); else store.setSite(domain, want);
                    }
                    pendingToast();
                    render();
                    return true;
                }).show();
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
