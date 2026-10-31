package com.cuvar.app.ui;

import com.cuvar.app.guard.GuardDiagnostics;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.cuvar.app.i18n.Translit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Spisak instaliranih aplikacija sa pretragom, za prozore izbora aplikacija. */
public final class AppPicker {
    private AppPicker() {
    }

    public static final class App {
        public final String pkg;
        public final String label;
        public final Drawable icon;

        App(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    /** Učitava aplikacije sa početnog ekrana (bez Čuvara) u pozadini, po abecedi; rezultat stiže na glavnu nit. */
    public static void load(Activity a, Consumer<List<App>> done) {
        new Thread(() -> {
            List<App> out = new ArrayList<>();
            try {
                PackageManager pm = a.getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                Set<String> seen = new HashSet<>();
                for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (pkg == null || pkg.equals(a.getPackageName()) || !seen.add(pkg)) continue;
                    Drawable icon = null;
                    try { icon = info.loadIcon(pm); } catch (Throwable ignored) { /* ikona nije obavezna */ }
                    out.add(new App(pkg, Translit.keep(String.valueOf(info.loadLabel(pm))), icon));
                }
            } catch (Throwable error) {
                GuardDiagnostics.report("appList", error); // prazan spisak umesto pada
            }
            out.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                Safe.run("appListShow", () -> done.accept(out));
            });
        }).start();
    }

    /**
     * Dodaje u box polje za pretragu, pa header (može null), pa po red za svaku aplikaciju.
     * Vraća redove istim redom kao apps, da pozivalac podesi štikliranje i klik.
     */
    public static List<CheckRow> addList(Context c, LinearLayout box, List<App> apps, View header) {
        EditText search = Sheet.input(c, "Pretraži aplikacije", false);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(search);
        if (header != null) box.addView(header, Ui.fill(c, 10));
        List<CheckRow> rows = new ArrayList<>();
        for (App app : apps) {
            CheckRow row = new CheckRow(c, app.icon, app.label, null);
            rows.add(row);
            box.addView(row, Ui.fill(c, 6));
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) { }
            @Override public void afterTextChanged(Editable s) {
                String q = Translit.toLatin(s.toString().trim()).toLowerCase(Locale.ROOT);
                for (int i = 0; i < rows.size(); i++) {
                    boolean show = q.isEmpty() || apps.get(i).label.toLowerCase(Locale.ROOT).contains(q);
                    rows.get(i).setVisibility(show ? View.VISIBLE : View.GONE);
                }
            }
        });
        return rows;
    }
}
