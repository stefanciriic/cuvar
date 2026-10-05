package com.cuvar.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lista instaliranih aplikacija: za svaku se bira PIN zaključavanje i/ili dnevni limit. */
public class AppsActivity extends SubActivity {

    private static final class Item {
        String pkg;
        String label;
        Drawable icon;
    }

    private static final class Holder {
        ImageView icon;
        TextView label;
        TextView summary;
    }

    private final List<Item> all = new ArrayList<>();
    private final List<Item> shown = new ArrayList<>();
    private String filter = "";
    private TextView status;

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder hd;
            if (convertView == null) {
                LinearLayout row = Ui.row(AppsActivity.this);
                row.setPadding(Ui.dp(AppsActivity.this, 20), Ui.dp(AppsActivity.this, 11),
                        Ui.dp(AppsActivity.this, 20), Ui.dp(AppsActivity.this, 11));
                hd = new Holder();
                hd.icon = new ImageView(AppsActivity.this);
                int size = Ui.dp(AppsActivity.this, 40);
                row.addView(hd.icon, new LinearLayout.LayoutParams(size, size));
                LinearLayout texts = Ui.column(AppsActivity.this);
                hd.label = Ui.text(AppsActivity.this, "", 16, Ui.INK, false);
                hd.label.setSingleLine(true);
                hd.summary = Ui.text(AppsActivity.this, "", 13, Ui.MUTED, false);
                texts.addView(hd.label);
                texts.addView(hd.summary);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tlp.leftMargin = Ui.dp(AppsActivity.this, 14);
                row.addView(texts, tlp);
                row.setTag(hd);
                convertView = row;
            } else {
                hd = (Holder) convertView.getTag();
            }
            Item it = shown.get(position);
            hd.icon.setImageDrawable(it.icon);
            hd.label.setText(it.label);
            String s = summary(it.pkg);
            hd.summary.setText(s == null ? "Bez ograničenja" : s);
            hd.summary.setTextColor(s == null ? Ui.MUTED : Ui.ACCENT);
            return convertView;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(header("Aplikacije", "Tapni aplikaciju da je zaključaš PIN-om ili joj postaviš dnevni limit."));

        EditText search = new EditText(this);
        search.setHint("Pretraži");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextSize(16);
        search.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 14)));
        int p = Ui.dp(this, 14);
        search.setPadding(p, p, p, p);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 8));
        root.addView(search, slp);

        status = Ui.text(this, "Učitavam aplikacije…", 14, Ui.MUTED, false);
        status.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        root.addView(status);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shown.size()) {
                showEdit(shown.get(position));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        load();
    }

    private void load() {
        new Thread(() -> {
            final List<Item> out = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> found = pm.queryIntentActivities(i, 0);
                Set<String> seen = new HashSet<>();
                String me = getPackageName();
                for (ResolveInfo ri : found) {
                    if (ri.activityInfo == null) {
                        continue;
                    }
                    String pkg = ri.activityInfo.packageName;
                    if (pkg == null || pkg.equals(me) || !seen.add(pkg)) {
                        continue;
                    }
                    Item it = new Item();
                    it.pkg = pkg;
                    CharSequence l = ri.loadLabel(pm);
                    it.label = l == null ? pkg : l.toString();
                    try {
                        it.icon = ri.loadIcon(pm);
                    } catch (Throwable t) {
                        it.icon = null;
                    }
                    out.add(it);
                }
            } catch (Throwable ignored) {
            }
            runOnUiThread(() -> {
                all.clear();
                all.addAll(out);
                applyFilter();
            });
        }).start();
    }

    private void applyFilter() {
        shown.clear();
        for (Item it : all) {
            if (filter.isEmpty() || it.label.toLowerCase(Locale.ROOT).contains(filter)) {
                shown.add(it);
            }
        }
        // Aplikacije koje već imaju pravilo idu na vrh, ostale po abecedi.
        Collections.sort(shown, (a, b) -> {
            boolean ra = store.hasAppRule(a.pkg);
            boolean rb = store.hasAppRule(b.pkg);
            if (ra != rb) {
                return ra ? -1 : 1;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        if (status != null) {
            if (all.isEmpty()) {
                status.setText("Učitavam aplikacije…");
                status.setVisibility(View.VISIBLE);
            } else if (shown.isEmpty()) {
                status.setText("Nema aplikacije sa tim imenom.");
                status.setVisibility(View.VISIBLE);
            } else {
                status.setVisibility(View.GONE);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private String summary(String pkg) {
        boolean lock = store.appLock(pkg);
        int limit = store.appLimit(pkg);
        if (!lock && limit <= 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (lock) {
            sb.append("PIN");
        }
        if (limit > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("Limit ").append(limit).append(" min, danas ").append(Ui.fmt(store.usedToday(pkg)));
        }
        return sb.toString();
    }

    private void showEdit(final Item it) {
        LinearLayout box = Ui.column(this);
        int p = Ui.dp(this, 22);
        box.setPadding(p, Ui.dp(this, 10), p, 0);

        final CheckBox lock = new CheckBox(this);
        lock.setText("Zaključaj PIN-om (traži PIN pri svakom otvaranju)");
        lock.setChecked(store.appLock(it.pkg));
        box.addView(lock);

        box.addView(Ui.text(this, "Dnevni limit u minutima (0 = bez limita)", 14, Ui.MUTED, false), Ui.fill(this, 14));
        final EditText limit = new EditText(this);
        limit.setInputType(InputType.TYPE_CLASS_NUMBER);
        limit.setSingleLine(true);
        limit.setText(String.valueOf(store.appLimit(it.pkg)));
        box.addView(limit);

        box.addView(Ui.text(this, "Danas korišćeno: " + Ui.fmt(store.usedToday(it.pkg)), 13, Ui.MUTED, false),
                Ui.fill(this, 6));

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(it.label)
                .setView(box)
                .setPositiveButton("Sačuvaj", (d, w) -> {
                    boolean wantLock = lock.isChecked();
                    if (wantLock && !store.hasPin()) {
                        Toast.makeText(this, "Prvo postavi PIN na početnom ekranu", Toast.LENGTH_LONG).show();
                        wantLock = false;
                    }
                    store.setApp(it.pkg, wantLock, Ui.parseInt(limit.getText().toString()));
                    applyFilter();
                })
                .setNegativeButton("Otkaži", null);
        if (store.hasAppRule(it.pkg)) {
            b.setNeutralButton("Ukloni", (d, w) -> {
                store.setApp(it.pkg, false, 0);
                applyFilter();
            });
        }
        b.show();
    }
}
