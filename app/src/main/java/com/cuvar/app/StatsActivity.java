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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Statistika za danas ili više dana: ukupno po danima, sajtovi u pregledaču, kategorije i najkorišćenije aplikacije. */
public class StatsActivity extends SubActivity {

    private static final class Row {
        String label;
        long ms;
    }

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
        root.addView(header("Statistika", "Pregled korišćenja telefona kroz vreme."));

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
        setContentView(scroll);

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
        String homePkg = homePackage();
        String me = getPackageName();

        Map<String, Long> perApp = new HashMap<>();
        Map<String, Long> perSite = new HashMap<>();
        Map<Integer, Long> perCategory = new HashMap<>();
        List<Row> perDay = new ArrayList<>();
        long grandTotal = 0;

        List<String> dayKeys = days == 1 ? Collections.singletonList(store.day()) : store.recentDays(days);
        for (String dk : dayKeys) {
            long dayTotal = 0;
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
                if (key.equals(homePkg) || key.equals(me)) {
                    continue;
                }
                dayTotal += ms;
                perApp.put(key, perApp.getOrDefault(key, 0L) + ms);
                int cat = categoryOf(pm, key);
                perCategory.put(cat, perCategory.getOrDefault(cat, 0L) + ms);
            }
            grandTotal += dayTotal;
            Row r = new Row();
            r.label = Store.dayLabel(dk);
            r.ms = dayTotal;
            perDay.add(r);
        }

        body.addView(summaryCard(grandTotal, dayKeys.size()), Ui.fill(this, 14));
        if (days > 1) {
            body.addView(barsCard("Po danima", perDay), Ui.fill(this, 16));
        }
        body.addView(barsCard("Sajtovi u pregledaču", topRows(perSite, k -> k, 10)), Ui.fill(this, 16));
        body.addView(barsCard("Po kategorijama", categoryRows(perCategory)), Ui.fill(this, 16));
        body.addView(barsCard("Najkorišćenije aplikacije",
                topRows(perApp, k -> labelOf(pm, k), 8)), Ui.fill(this, 16));
    }

    private View summaryCard(long total, int dayCount) {
        LinearLayout card = Ui.card(this);
        TextView eyebrow = Ui.text(this, days == 1 ? "UKUPNO DANAS" : "UKUPNO ZA PERIOD", 12, Ui.MUTED, true);
        eyebrow.setLetterSpacing(0.15f);
        card.addView(eyebrow);
        card.addView(Ui.text(this, Ui.fmt(total), 34, Ui.INK, true), Ui.fill(this, 4));
        if (days == 1) {
            return card;
        }
        long avg = dayCount > 0 ? total / dayCount : 0;
        card.addView(Ui.text(this, "Prosečno " + Ui.fmt(avg) + " po danu", 14, Ui.MUTED, false),
                Ui.fill(this, 4));
        return card;
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
