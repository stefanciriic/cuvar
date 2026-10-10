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
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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
                row.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(AppsActivity.this, 16)));
                row.setElevation(Ui.dp(AppsActivity.this, 1));
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
        root.addView(header("Aplikacije", "Tapni aplikaciju da je zaključaš, postaviš dnevni limit, broj otvaranja ili najduže u komadu. Popuštanje važi tek sutra od 06:00."));

        EditText search = new EditText(this);
        search.setHint("Pretraži");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextSize(16);
        search.setTextColor(Ui.INK);
        search.setHintTextColor(Ui.MUTED);
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
        list.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 12));
        list.setClipToPadding(false);
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
                if (isFinishing() || isDestroyed()) return;
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
            boolean ra = store.appGuarded(a.pkg);
            boolean rb = store.appGuarded(b.pkg);
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
        String now = summary(pkg, true);
        String next = summary(pkg, false);
        if (!java.util.Objects.equals(now, next)) {
            return (now == null ? "Bez ograničenja" : now) + "\nOd sledećih 06:00: "
                    + (next == null ? "bez ograničenja" : next);
        }
        return now;
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) applyFilter();
    }

    private String summary(String pkg, boolean effective) {
        boolean lock = effective ? store.appLockNow(pkg) : store.appLock(pkg);
        int limit = effective ? store.appLimitNow(pkg) : store.appLimit(pkg);
        int opens = effective ? store.appOpensNow(pkg) : store.appOpens(pkg);
        int session = effective ? store.appSessionNow(pkg) : store.appSession(pkg);
        if (!lock && limit <= 0 && opens <= 0 && session <= 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (lock) {
            sb.append("Zaključana");
        }
        if (limit > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("Limit ").append(limit).append(" min, danas ").append(Ui.fmt(store.usedToday(pkg)));
        }
        if (opens > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("Otvaranja ").append(store.opensToday("app:" + pkg)).append(" od ").append(opens);
        }
        if (session > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("Najviše ").append(session).append(" min u komadu");
        }
        return sb.toString();
    }

    private void pendingToast() {
        if (!store.pendingChanges(getPackageManager()).isEmpty()) {
            Toast.makeText(this, "Pooštravanje važi odmah, a popuštanje tek sutra od 06:00.", Toast.LENGTH_LONG).show();
        }
    }

    private void showEdit(final Item it) {
        long busy = store.hasAppRule(it.pkg) ? store.unlockBusyLeft() : 0;
        if (busy > 0) {
            // Inače bi se pauza posle otključavanja zaobišla brisanjem ograničenja.
            Toast.makeText(this, "Nedavno je nešto otključano. Ograničenja se mogu menjati za "
                    + Ui.fmt(busy) + ".", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = Ui.column(this);

        final CheckRow lock = new CheckRow(this, null, "Zaključaj", "Otvara se samo dnevnom šifrom, od 17:00 do 22:00");
        lock.setChecked(store.appLock(it.pkg));
        box.addView(lock);

        box.addView(Sheet.label(this, "Dnevni limit u minutima (0 = bez limita)"), Ui.fill(this, 16));
        final EditText limit = Sheet.input(this, "0", true);
        limit.setText(String.valueOf(store.appLimit(it.pkg)));
        box.addView(limit, Ui.fill(this, 6));

        box.addView(Sheet.label(this, "Najviše otvaranja dnevno (0 = bez ograničenja)"), Ui.fill(this, 16));
        final EditText opens = Sheet.input(this, "0", true);
        opens.setText(String.valueOf(store.appOpens(it.pkg)));
        box.addView(opens, Ui.fill(this, 6));

        box.addView(Sheet.label(this, "Najduže u komadu, u minutima, pa pauza od "
                + Store.SESSION_BREAK_MS / 60000L + " min (0 = bez)"), Ui.fill(this, 16));
        final EditText session = Sheet.input(this, "0", true);
        session.setText(String.valueOf(store.appSession(it.pkg)));
        box.addView(session, Ui.fill(this, 6));

        box.addView(Ui.text(this, "Danas korišćeno: " + Ui.fmt(store.usedToday(it.pkg)) + ", otvoreno "
                + Ui.count(store.opensToday("app:" + it.pkg), "put", "puta", "puta"), 13, Ui.MUTED, false),
                Ui.fill(this, 8));

        Sheet sheet = new Sheet(this, it.label);
        if (!store.hasAppRule(it.pkg) && summary(it.pkg, true) != null) {
            sheet.message("Ograničenja su uklonjena i prestaju sutra u 06:00.");
        }
        sheet.view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    boolean wantLock = lock.isChecked();
                    Integer wantLimit = Ui.nonNegativeNumber(limit);
                    if (wantLimit == null) return false;
                    Integer wantOpens = Ui.nonNegativeNumber(opens);
                    if (wantOpens == null) return false;
                    Integer wantSession = Ui.nonNegativeNumber(session);
                    if (wantSession == null) return false;
                    boolean adding = wantLock || wantLimit > 0 || wantOpens > 0 || wantSession > 0;
                    saveGuarding(it.pkg, it.label, adding, () -> {
                        store.setApp(it.pkg, wantLock, wantLimit, wantOpens, wantSession);
                        pendingToast();
                        applyFilter();
                    });
                    return true;
                });
        if (store.hasAppRule(it.pkg)) {
            sheet.danger("Ukloni sva ograničenja", () -> {
                store.setApp(it.pkg, false, 0, 0, 0);
                pendingToast();
                applyFilter();
                return true;
            });
        }
        sheet.show();
    }
}
