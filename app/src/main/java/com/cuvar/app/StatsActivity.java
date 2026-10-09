package com.cuvar.app;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Statistika za danas ili više dana: poređenje sa ranijim danima, krug po aplikacijama, kolone po danima, sajtovi i kategorije. */
public class StatsActivity extends SubActivity {

    private static final class Row {
        String label;
        long ms;
    }

    /** Otvoren kao kartica donje trake glavnog ekrana (bez strelice nazad). */
    static final String AS_TAB = "asTab";

    private int days = 7;
    private LinearLayout body;
    private TextView tab1;
    private TextView tab7;
    private TextView tab14;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        final boolean asTab = getIntent().getBooleanExtra(AS_TAB, false);
        root.addView(header("Statistika", "Pregled korišćenja telefona kroz vreme.", !asTab));

        LinearLayout tabs = Ui.row(this);
        tabs.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        tab1 = chip("Danas");
        tab7 = chip("7 dana");
        tab14 = chip("14 dana");
        tab1.setOnClickListener(v -> {
            days = 1;
            render();
        });
        tab7.setOnClickListener(v -> {
            days = 7;
            render();
        });
        tab14.setOnClickListener(v -> {
            days = 14;
            render();
        });
        tabs.addView(tab1, chipParams());
        tabs.addView(tab7, chipParams());
        tabs.addView(tab14, chipParams());
        root.addView(tabs, Ui.fill(this, 4));

        body = Ui.column(this);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 36));
        root.addView(body);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        if (asTab) {
            LinearLayout page = Ui.column(this);
            page.setBackgroundColor(Ui.BG);
            page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            page.addView(Ui.bottomBar(this, 2, t -> {
                MainActivity.tab = t;
                finish();
                overridePendingTransition(0, 0);
            }));
            setContentView(page);
        } else {
            setContentView(scroll);
        }

        render();
    }

    private TextView chip(String label) {
        TextView t = Ui.text(this, label, 14, Ui.INK, true);
        t.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 12);
        t.setPadding(p, p, p, p);
        return t;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        return lp;
    }

    private void setChipActive(TextView t, boolean active) {
        t.setBackground(Ui.round(active ? Ui.ACCENT : Ui.SOFT, Ui.dp(this, 14)));
        t.setTextColor(active ? 0xFFFFFFFF : Ui.INK);
    }

    private void render() {
        setChipActive(tab1, days == 1);
        setChipActive(tab7, days == 7);
        setChipActive(tab14, days == 14);
        body.removeAllViews();

        PackageManager pm = getPackageManager();
        skip.clear();
        skip.add(homePackage());
        skip.add(getPackageName());

        Map<String, Long> perApp = new HashMap<>();
        Map<String, Long> perSite = new HashMap<>();
        Map<Integer, Long> perCategory = new HashMap<>();
        List<Map<String, Long>> appsPerDay = new ArrayList<>();
        long grandTotal = 0;
        int counted = 0; // dani od prvog dana sa podacima, za prosek

        // Kalendarski dani zaključno sa danas; dani bez korišćenja se računaju kao nula.
        List<String> dayKeys = store.lastDays(days);
        for (String dk : dayKeys) {
            if (counted > 0 || store.hasDay(dk)) counted++;
            Map<String, Long> apps = new HashMap<>();
            Map<String, Long> day = store.dayMap(dk);
            // Dani pre merenja svih sajtova imaju samo sajtove sa liste; tada se prikazuju oni.
            boolean hasWeb = false;
            for (String key : day.keySet()) {
                if (key.startsWith("web:")) {
                    hasWeb = true;
                    break;
                }
            }
            for (Map.Entry<String, Long> e : day.entrySet()) {
                String key = e.getKey();
                long ms = e.getValue();
                if (key.startsWith("web:") || key.startsWith("site:")) {
                    if (key.startsWith("web:") == hasWeb) {
                        String domain = key.substring(key.indexOf(':') + 1);
                        perSite.put(domain, perSite.getOrDefault(domain, 0L) + ms);
                    }
                    continue; // vreme sajta je već deo vremena pregledača, ne broji se duplo u ukupno
                }
                if (skip.contains(key) || !GuardService.tracked(this, key)) {
                    continue;
                }
                grandTotal += ms;
                apps.put(key, ms);
                perApp.put(key, perApp.getOrDefault(key, 0L) + ms);
                int cat = categoryOf(pm, key);
                perCategory.put(cat, perCategory.getOrDefault(cat, 0L) + ms);
            }
            appsPerDay.add(apps);
        }

        // Aplikacije sa bojom: prvih 5 po vremenu u periodu, ostale idu u „Ostalo“ (isto u krugu i kolonama).
        List<Row> top = topRows(perApp, k -> labelOf(pm, k), Integer.MAX_VALUE);
        List<String> colored = new ArrayList<>();
        for (Map.Entry<String, Long> e : sortedEntries(perApp)) {
            if (colored.size() == TOP) break;
            if (labelOf(pm, e.getKey()) != null) colored.add(e.getKey());
        }

        long avg = counted > 0 ? grandTotal / counted : 0;
        body.addView(summaryCard(grandTotal, avg), Ui.fill(this, 14));
        body.addView(donutCard(pm, perApp, colored, grandTotal), Ui.fill(this, 16));
        if (days > 1) {
            body.addView(columnsCard(dayKeys, appsPerDay, colored, avg), Ui.fill(this, 16));
        }
        body.addView(barsCard("Top 3 sajta u pregledaču", topRows(perSite, k -> k, 3)), Ui.fill(this, 16));
        body.addView(barsCard("Po kategorijama", categoryRows(perCategory)), Ui.fill(this, 16));
        if (top.size() > TOP) {
            // Prvih 5 je već u legendi kruga; ovde su sledeće po redu.
            body.addView(barsCard("Ostale aplikacije", top.subList(TOP, Math.min(TOP + 8, top.size()))),
                    Ui.fill(this, 16));
        }
    }

    private static final int TOP = 5;
    private final Set<String> skip = new HashSet<>();

    /** Ukupno vreme aplikacija za dan (bez početnog ekrana i Čuvara), i da li dan uopšte ima podatke. */
    private long totalOf(String dayKey) {
        long t = 0;
        for (Map.Entry<String, Long> e : store.dayMap(dayKey).entrySet()) {
            String k = e.getKey();
            if (k.startsWith("web:") || k.startsWith("site:") || skip.contains(k) || !GuardService.tracked(this, k)) continue;
            t += e.getValue();
        }
        return t;
    }

    /** Prosek po danu za date dane, samo dani sa podacima; -1 ako podataka nema. */
    private long avgOf(List<String> keys) {
        long t = 0;
        int n = 0;
        for (String k : keys) {
            if (!store.hasDay(k)) continue;
            t += totalOf(k);
            n++;
        }
        return n == 0 ? -1 : t / n;
    }

    private View summaryCard(long total, long avg) {
        LinearLayout card = Ui.card(this);
        TextView eyebrow = Ui.text(this, days == 1 ? "UKUPNO DANAS" : "UKUPNO ZA " + days + " DANA", 12, Ui.MUTED, true);
        eyebrow.setLetterSpacing(0.15f);
        card.addView(eyebrow);
        card.addView(Ui.text(this, Ui.fmt(total), 34, Ui.INK, true), Ui.fill(this, 4));

        LinearLayout tiles = Ui.row(this);
        tiles.setGravity(Gravity.TOP);
        String change;
        int changeColor;
        if (days == 1) {
            List<String> week = store.lastDays(8);
            long yesterday = store.hasDay(week.get(6)) ? totalOf(week.get(6)) : -1;
            long weekAvg = avgOf(week.subList(0, 7));
            tiles.addView(tile("Juče", yesterday < 0 ? "–" : Ui.fmt(yesterday)), tileParams(false));
            tiles.addView(tile("Prosek 7 dana", weekAvg < 0 ? "–" : Ui.fmt(weekAvg)), tileParams(true));
            if (weekAvg > 0) {
                long pct = Math.round(100.0 * total / weekAvg);
                change = "Danas do sada: " + pct + "% tvog dnevnog proseka";
                changeColor = pct > 100 ? Ui.ACCENT : Ui.MUTED;
            } else {
                change = "Za poređenje treba još koji dan merenja.";
                changeColor = Ui.MUTED;
            }
        } else {
            List<String> two = store.lastDays(14);
            long now = avgOf(two.subList(7, 14));
            long before = avgOf(two.subList(0, 7));
            tiles.addView(tile("Prosek po danu", Ui.fmt(avg)), tileParams(false));
            tiles.addView(tile("Prethodnih 7 dana", before < 0 ? "–" : Ui.fmt(before) + " / dan"), tileParams(true));
            if (before > 0 && now >= 0) {
                long pct = Math.round(100.0 * (now - before) / before);
                if (pct > 0) {
                    change = "▲ " + pct + "% više nego prethodne nedelje";
                    changeColor = Ui.ACCENT;
                } else if (pct < 0) {
                    change = "▼ " + (-pct) + "% manje nego prethodne nedelje";
                    changeColor = Charts.good();
                } else {
                    change = "Isto kao prethodne nedelje";
                    changeColor = Ui.MUTED;
                }
            } else {
                change = "Za poređenje sa prethodnom nedeljom treba još podataka.";
                changeColor = Ui.MUTED;
            }
        }
        card.addView(tiles, Ui.fill(this, 14));
        card.addView(Ui.text(this, change, 14, changeColor, true), Ui.fill(this, 12));
        return card;
    }

    private View tile(String label, String value) {
        LinearLayout t = Ui.column(this);
        t.setBackground(Ui.round(Ui.SOFT, Ui.dp(this, 12)));
        int p = Ui.dp(this, 12);
        t.setPadding(p, p, p, p);
        t.addView(Ui.text(this, label, 12, Ui.MUTED, false));
        t.addView(Ui.text(this, value, 17, Ui.INK, true), Ui.fill(this, 2));
        return t;
    }

    private LinearLayout.LayoutParams tileParams(boolean second) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (second) lp.leftMargin = Ui.dp(this, 10);
        return lp;
    }

    private TextView eyebrow(String title) {
        TextView e = Ui.text(this, title.toUpperCase(Locale.ROOT), 12, Ui.MUTED, true);
        e.setLetterSpacing(0.15f);
        return e;
    }

    /** Krug: koliko je vremena otišlo na koju aplikaciju, sa legendom ispod. */
    private View donutCard(PackageManager pm, Map<String, Long> perApp, List<String> colored, long total) {
        LinearLayout card = Ui.card(this);
        card.addView(eyebrow("Gde ode vreme"));
        if (total <= 0) {
            card.addView(Ui.text(this, "Nema podataka za ovaj period.", 14, Ui.MUTED, false), Ui.fill(this, 8));
            return card;
        }
        List<Charts.Part> parts = new ArrayList<>();
        StringBuilder desc = new StringBuilder("Ukupno " + Ui.fmt(total));
        long rest = total;
        for (int i = 0; i < colored.size(); i++) {
            long ms = perApp.get(colored.get(i));
            parts.add(new Charts.Part(ms, Charts.color(i, false)));
            rest -= ms;
        }
        if (rest > 0) parts.add(new Charts.Part(rest, Charts.color(0, true)));

        Charts.Donut donut = new Charts.Donut(this, parts, Ui.fmt(total), days == 1 ? "danas" : "za " + days + " dana");
        card.addView(donut, Ui.fill(this, 12));

        for (int i = 0; i < colored.size(); i++) {
            String name = labelOf(pm, colored.get(i));
            long ms = perApp.get(colored.get(i));
            card.addView(legendRow(Charts.color(i, false), name, ms, total), Ui.fill(this, i == 0 ? 16 : 10));
            desc.append(", ").append(name).append(' ').append(Ui.fmt(ms));
        }
        if (rest > 0) {
            card.addView(legendRow(Charts.color(0, true), "Ostalo", rest, total), Ui.fill(this, 10));
        }
        donut.setContentDescription(desc);
        return card;
    }

    private View legendRow(int color, String name, long ms, long total) {
        LinearLayout row = Ui.row(this);
        View dot = new View(this);
        dot.setBackground(Ui.round(color, Ui.dp(this, 5)));
        int d = Ui.dp(this, 10);
        row.addView(dot, new LinearLayout.LayoutParams(d, d));
        TextView n = Ui.text(this, name, 15, Ui.INK, false);
        n.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, 10);
        row.addView(n, lp);
        row.addView(Ui.text(this, Ui.fmt(ms), 15, Ui.MUTED, false));
        TextView pct = Ui.text(this, Math.round(100.0 * ms / total) + "%", 13, Ui.MUTED, true);
        pct.setGravity(Gravity.END);
        row.addView(pct, new LinearLayout.LayoutParams(Ui.dp(this, 48), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** Kolone po danima, svaka podeljena po istim aplikacijama i bojama kao krug. */
    private View columnsCard(List<String> dayKeys, List<Map<String, Long>> appsPerDay, List<String> colored, long avg) {
        LinearLayout card = Ui.card(this);
        card.addView(eyebrow("Po danima"));
        List<List<Charts.Part>> cols = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        StringBuilder desc = new StringBuilder();
        for (int d = 0; d < dayKeys.size(); d++) {
            Map<String, Long> apps = appsPerDay.get(d);
            List<Charts.Part> parts = new ArrayList<>();
            long dayTotal = 0;
            for (long ms : apps.values()) dayTotal += ms;
            long rest = dayTotal;
            for (int i = 0; i < colored.size(); i++) {
                long ms = apps.getOrDefault(colored.get(i), 0L);
                if (ms > 0) parts.add(new Charts.Part(ms, Charts.color(i, false)));
                rest -= ms;
            }
            if (rest > 0) parts.add(new Charts.Part(rest, Charts.color(0, true)));
            cols.add(parts);
            labels.add(columnLabel(dayKeys.get(d)));
            if (desc.length() > 0) desc.append(", ");
            desc.append(Store.dayLabel(dayKeys.get(d))).append(' ').append(Ui.fmt(dayTotal));
        }
        Charts.Columns chart = new Charts.Columns(this, cols, labels, dayKeys.size() - 1, avg);
        chart.setContentDescription(desc);
        card.addView(chart, Ui.fill(this, 14));
        card.addView(Ui.text(this, "Boje su iste kao u krugu iznad. Isprekidana linija je prosek po danu.",
                12, Ui.MUTED, false), Ui.fill(this, 10));
        return card;
    }

    /** Ispod kolone: dan u nedelji za 7 dana, a datum za 14 dana. */
    private String columnLabel(String dayKey) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(new SimpleDateFormat("yyyyMMdd", Locale.US).parse(dayKey));
            if (days > 7) return String.valueOf(c.get(Calendar.DAY_OF_MONTH));
            return DailySchedule.DAY_NAMES[(c.get(Calendar.DAY_OF_WEEK) + 5) % 7];
        } catch (Exception e) {
            return "";
        }
    }

    private static List<Map.Entry<String, Long>> sortedEntries(Map<String, Long> src) {
        List<Map.Entry<String, Long>> out = new ArrayList<>(src.entrySet());
        Collections.sort(out, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        return out;
    }

    private View barsCard(String title, List<Row> rows) {
        LinearLayout card = Ui.card(this);
        TextView eyebrow = Ui.text(this, title.toUpperCase(Locale.ROOT), 12, Ui.MUTED, true);
        eyebrow.setLetterSpacing(0.15f);
        card.addView(eyebrow);

        if (rows.isEmpty()) {
            card.addView(Ui.text(this, "Nema podataka za ovaj period.", 14, Ui.MUTED, false),
                    Ui.fill(this, 8));
            return card;
        }

        long max = 1;
        for (Row r : rows) {
            max = Math.max(max, r.ms);
        }
        for (Row r : rows) {
            card.addView(barRow(r, max), Ui.fill(this, 14));
        }
        return card;
    }

    private View barRow(Row r, long max) {
        LinearLayout box = Ui.column(this);
        LinearLayout line = Ui.row(this);
        TextView name = Ui.text(this, r.label, 15, Ui.INK, false);
        name.setSingleLine(true);
        line.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(Ui.text(this, Ui.fmt(r.ms), 15, Ui.MUTED, false));
        box.addView(line);

        float frac = Math.max(0.02f, Math.min(1f, r.ms / (float) max));
        int hgt = Ui.dp(this, 6);
        LinearLayout bar = Ui.row(this);
        bar.setBackground(Ui.round(Ui.LINE, hgt / 2f));
        View fill = new View(this);
        fill.setBackground(Ui.round(Ui.ACCENT, hgt / 2f));
        bar.addView(fill, new LinearLayout.LayoutParams(0, hgt, frac));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, hgt, 1f - frac));
        box.addView(bar, Ui.fill(this, 6));
        return box;
    }

    private List<Row> topRows(Map<String, Long> src, Function<String, String> labeler, int limit) {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : src.entrySet()) {
            String label = labeler.apply(e.getKey());
            if (label == null) {
                continue;
            }
            Row r = new Row();
            r.label = label;
            r.ms = e.getValue();
            out.add(r);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.ms, a.ms));
        if (out.size() > limit) {
            out = out.subList(0, limit);
        }
        return out;
    }

    private List<Row> categoryRows(Map<Integer, Long> src) {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<Integer, Long> e : src.entrySet()) {
            Row r = new Row();
            r.label = categoryLabel(e.getKey());
            r.ms = e.getValue();
            out.add(r);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.ms, a.ms));
        return out;
    }

    private static int categoryOf(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationInfo(pkg, 0).category;
        } catch (Throwable t) {
            return ApplicationInfo.CATEGORY_UNDEFINED;
        }
    }

    /** Kategoriju prijavljuje sam developer aplikacije; mnoge manje aplikacije je ne prijavljuju. */
    private static String categoryLabel(int category) {
        switch (category) {
            case ApplicationInfo.CATEGORY_GAME:
                return "Igre";
            case ApplicationInfo.CATEGORY_AUDIO:
                return "Muzika i audio";
            case ApplicationInfo.CATEGORY_VIDEO:
                return "Video";
            case ApplicationInfo.CATEGORY_IMAGE:
                return "Fotografija";
            case ApplicationInfo.CATEGORY_SOCIAL:
                return "Društvene mreže";
            case ApplicationInfo.CATEGORY_NEWS:
                return "Vesti i časopisi";
            case ApplicationInfo.CATEGORY_MAPS:
                return "Mape i navigacija";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                return "Produktivnost";
            default:
                return "Ostalo";
        }
    }

    private static String labelOf(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private String homePackage() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r != null && r.activityInfo != null) {
                return r.activityInfo.packageName;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
