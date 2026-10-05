package com.cuvar.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Početni ekran: podešavanje, pregled vremena za danas i ulaz u liste aplikacija i sajtova. */
public class MainActivity extends Activity {

    private static final class Row {
        String label;
        long ms;
    }

    private Store store;
    private boolean pinSetup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);
        Ui.styleWindow(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.maybeCheck(this);
        if (store.hasPin() && !Session.valid()) {
            Session.authed = false;
            showGate();
            return;
        }
        Session.seen();
        if (!pinSetup) {
            showDashboard();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Session.seen();
    }

    @Override
    public void onBackPressed() {
        if (pinSetup) {
            pinSetup = false;
            showDashboard();
        } else {
            super.onBackPressed();
        }
    }

    // ---------- PIN ekrani ----------

    private void setScreen(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        scroll.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private LinearLayout pinBox(TextView title, TextView sub, PinPad pad) {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 24);
        box.setPadding(p, p, p, p);
        TextView eyebrow = Ui.text(this, "ČUVAR", 12, Ui.ACCENT, true);
        eyebrow.setLetterSpacing(0.25f);
        eyebrow.setGravity(Gravity.CENTER);
        box.addView(eyebrow, Ui.fill(this, 0));
        title.setGravity(Gravity.CENTER);
        box.addView(title, Ui.fill(this, 10));
        sub.setGravity(Gravity.CENTER);
        box.addView(sub, Ui.fill(this, 8));
        box.addView(pad, Ui.fill(this, 24));
        return box;
    }

    private void showGate() {
        pinSetup = false;
        TextView title = Ui.text(this, "Zaključano", 26, Ui.INK, true);
        TextView sub = Ui.text(this, "Unesi PIN da otvoriš podešavanja.", 15, Ui.MUTED, false);
        final PinPad pad = new PinPad(this, false);
        pad.setListener(pin -> {
            String err = store.tryPin(pin);
            if (err == null) {
                Session.authed = true;
                Session.seen();
                showDashboard();
            } else {
                pad.clear();
                pad.setMessage(err);
            }
        });
        setScreen(pinBox(title, sub, pad));
    }

    private void showPinSetup() {
        pinSetup = true;
        final String intro = "Unesi 4 do 8 cifara, pa tapni OK.";
        final TextView title = Ui.text(this, "Novi PIN", 26, Ui.INK, true);
        final TextView sub = Ui.text(this, intro, 15, Ui.MUTED, false);
        final PinPad pad = new PinPad(this, false);
        final String[] first = {null};
        pad.setListener(pin -> {
            if (first[0] == null) {
                if (pin.length() < 4) {
                    pad.clear();
                    pad.setMessage("PIN mora imati bar 4 cifre");
                    return;
                }
                first[0] = pin;
                pad.clear();
                title.setText("Ponovi PIN");
                sub.setText("Unesi isti PIN još jednom.");
            } else if (first[0].equals(pin)) {
                store.setPin(pin);
                Session.authed = true;
                Session.seen();
                pinSetup = false;
                Toast.makeText(this, "PIN je sačuvan", Toast.LENGTH_SHORT).show();
                showDashboard();
            } else {
                first[0] = null;
                pad.clear();
                title.setText("Novi PIN");
                sub.setText(intro);
                pad.setMessage("PIN-ovi se ne poklapaju, pokušaj ponovo");
            }
        });
        setScreen(pinBox(title, sub, pad));
    }

    // ---------- Glavni ekran ----------

    private void showDashboard() {
        pinSetup = false;
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 36));

        col.addView(Ui.text(this, "Čuvar", 34, Ui.INK, true));
        String date = new SimpleDateFormat("EEEE, d. MMMM",
                new Locale.Builder().setLanguage("sr").setScript("Latn").build()).format(new Date());
        col.addView(Ui.text(this, date, 14, Ui.MUTED, false), Ui.fill(this, 2));

        boolean hasPin = store.hasPin();
        boolean enabled = GuardService.isEnabled(this);

        String pendingUpdateUrl = Updater.pendingUrl(this);
        if (pendingUpdateUrl != null) {
            col.addView(setupCard("Nova verzija Čuvara",
                    "Dostupno je ažuriranje. Tapni da ga preuzmeš, pa potvrdi instalaciju kad te telefon pita.",
                    "Preuzmi i instaliraj", v -> Updater.startDownload(this, pendingUpdateUrl), null, null),
                    Ui.fill(this, 18));
        }

        if (!hasPin) {
            col.addView(setupCard("Postavi PIN",
                    "PIN štiti ova podešavanja i otključava zaključane aplikacije i sajtove.",
                    "Postavi PIN", v -> showPinSetup(), null, null), Ui.fill(this, 18));
        }
        if (!enabled) {
            col.addView(setupCard("Uključi Čuvara",
                    "U Pristupačnosti pronađi „Čuvar“ (pod Preuzete ili Instalirane aplikacije) i uključi ga. "
                            + "Bez toga merenje vremena i blokiranje ne rade.",
                    "Otvori Pristupačnost", v -> openAccessibility(),
                    "Opcija je siva ili piše da je ograničena?", v -> showRestrictedHelp()), Ui.fill(this, 14));
        }

        col.addView(todayCard(enabled), Ui.fill(this, 18));

        int appRules = store.appRuleCount();
        int siteRules = store.siteList().size();
        col.addView(navTile("Aplikacije",
                appRules == 0 ? "Zaključaj PIN-om ili postavi dnevni limit" : "Pravila: " + appRules,
                v -> startActivity(new Intent(this, AppsActivity.class))), Ui.fill(this, 14));
        col.addView(navTile("Sajtovi",
                siteRules == 0 ? "Blokiraj sajtove ili im postavi dnevni limit" : "Na listi: " + siteRules,
                v -> startActivity(new Intent(this, SitesActivity.class))), Ui.fill(this, 10));
        col.addView(navTile("Statistika",
                "Po danima, kategorijama, aplikacijama i sajtovima",
                v -> startActivity(new Intent(this, StatsActivity.class))), Ui.fill(this, 10));

        if (hasPin) {
            TextView change = Ui.button(this, "Promeni PIN", false);
            change.setOnClickListener(v -> showPinSetup());
            col.addView(change, Ui.fill(this, 18));
        }

        TextView note = Ui.text(this,
                "Savet: zaključaj PIN-om i Podešavanja telefona, da Čuvar ne može lako da se isključi ili obriše.",
                13, Ui.MUTED, false);
        col.addView(note, Ui.fill(this, 18));

        TextView version = Ui.text(this, "Verzija " + BuildConfig.VERSION_NAME, 12, Ui.MUTED, false);
        version.setGravity(Gravity.CENTER);
        col.addView(version, Ui.fill(this, 10));

        setScreen(col);
    }

    private View setupCard(String title, String body, String action, View.OnClickListener onAction,
                           String help, View.OnClickListener onHelp) {
        LinearLayout card = Ui.card(this);
        TextView eyebrow = Ui.text(this, "POTREBNO", 12, Ui.ACCENT, true);
        eyebrow.setLetterSpacing(0.15f);
        card.addView(eyebrow);
        card.addView(Ui.text(this, title, 20, Ui.INK, true), Ui.fill(this, 4));
        card.addView(Ui.text(this, body, 14, Ui.MUTED, false), Ui.fill(this, 6));
        TextView b = Ui.button(this, action, true);
        b.setOnClickListener(onAction);
        card.addView(b, Ui.fill(this, 14));
        if (help != null) {
            TextView hlp = Ui.text(this, help, 14, Ui.ACCENT, true);
            hlp.setGravity(Gravity.CENTER);
            int p = Ui.dp(this, 10);
            hlp.setPadding(p, p, p, p);
            hlp.setOnClickListener(onHelp);
            card.addView(hlp, Ui.fill(this, 6));
        }
        return card;
    }

    private View navTile(String title, String sub, View.OnClickListener onClick) {
        LinearLayout tile = Ui.row(this);
        tile.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(this, 18)));
        int p = Ui.dp(this, 18);
        tile.setPadding(p, p, p, p);
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 18, Ui.INK, true));
        texts.addView(Ui.text(this, sub, 14, Ui.MUTED, false), Ui.fill(this, 2));
        tile.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tile.addView(Ui.text(this, "›", 26, Ui.ACCENT, false));
        tile.setOnClickListener(onClick);
        return tile;
    }

    private View todayCard(boolean enabled) {
        LinearLayout card = Ui.card(this);
        TextView eyebrow = Ui.text(this, "DANAS NA TELEFONU", 12, Ui.MUTED, true);
        eyebrow.setLetterSpacing(0.15f);
        card.addView(eyebrow);

        PackageManager pm = getPackageManager();
        String home = homePackage();
        String me = getPackageName();
        List<Row> rows = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Long> e : store.todayMap().entrySet()) {
            String key = e.getKey();
            if (key.startsWith("site:") || key.equals(home) || key.equals(me)) {
                continue;
            }
            String label = labelOf(pm, key);
            if (label == null) {
                continue;
            }
            Row r = new Row();
            r.label = label;
            r.ms = e.getValue();
            rows.add(r);
            total += r.ms;
        }
        Collections.sort(rows, (a, b) -> Long.compare(b.ms, a.ms));

        card.addView(Ui.text(this, Ui.fmt(total), 38, Ui.INK, true), Ui.fill(this, 4));

        if (rows.isEmpty()) {
            card.addView(Ui.text(this,
                    enabled ? "Još nema podataka za danas. Otvori neku aplikaciju pa se vrati."
                            : "Merenje počinje kad uključiš Čuvara.",
                    14, Ui.MUTED, false), Ui.fill(this, 6));
            return card;
        }

        long max = Math.max(1L, rows.get(0).ms);
        int n = Math.min(rows.size(), 8);
        for (int i = 0; i < n; i++) {
            card.addView(usageRow(rows.get(i), max), Ui.fill(this, 14));
        }
        return card;
    }

    private View usageRow(Row r, long max) {
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

    // ---------- Dozvole ----------

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable t) {
            Toast.makeText(this, "Otvori Podešavanja → Pristupačnost ručno", Toast.LENGTH_LONG).show();
        }
    }

    private void showRestrictedHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Ograničena podešavanja")
                .setMessage("Android ovo blokira za aplikacije koje nisu iz Play prodavnice. Uradi ovako:\n\n"
                        + "1. Tapni „Informacije o aplikaciji“ ispod.\n"
                        + "2. Tapni tri tačke gore desno.\n"
                        + "3. Izaberi „Dozvoli ograničena podešavanja“ i potvrdi.\n"
                        + "4. Vrati se ovde i ponovo otvori Pristupačnost.\n\n"
                        + "Ako ne vidiš tri tačke, prvo pokušaj da uključiš Čuvara u Pristupačnosti, "
                        + "pa kad te telefon odbije, vrati se na ovaj korak.")
                .setPositiveButton("Informacije o aplikaciji", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Throwable ignored) {
                    }
                })
                .setNegativeButton("Zatvori", null)
                .show();
    }
}
