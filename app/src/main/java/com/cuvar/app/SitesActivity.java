package com.cuvar.app;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Lista sajtova: svaki je ili uvek blokiran ili ima dnevni limit. */
public class SitesActivity extends SubActivity {

    private final List<String> sites = new ArrayList<>();
    private TextView empty;

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return sites.size();
        }

        @Override
        public Object getItem(int position) {
            return sites.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Lista je kratka, pa red pravimo svaki put iznova.
            String domain = sites.get(position);
            LinearLayout row = Ui.column(SitesActivity.this);
            row.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, Ui.dp(SitesActivity.this, 16)));
            row.setElevation(Ui.dp(SitesActivity.this, 1));
            row.setPadding(Ui.dp(SitesActivity.this, 20), Ui.dp(SitesActivity.this, 12),
                    Ui.dp(SitesActivity.this, 20), Ui.dp(SitesActivity.this, 12));
            row.addView(Ui.text(SitesActivity.this, domain, 17, Ui.INK, true));
            int limit = store.siteLimitNow(domain);
            String s = siteSummary(limit);
            if (limit > 0) s += ", danas " + Ui.fmt(store.usedToday("site:" + domain));
            int next = store.siteLimit(domain);
            if (next != limit) s += "\nOd sledećih 06:00: " + siteSummary(next);
            row.addView(Ui.text(SitesActivity.this, s, 13, Ui.ACCENT, false));
            return row;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(header("Sajtovi",
                "Radi u Chrome-u. Ako imaš i druge pregledače, zaključaj ih na listi aplikacija."));

        TextView add = Ui.button(this, "Dodaj sajt", true);
        add.setOnClickListener(v -> showEdit(null));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.setMargins(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8));
        root.addView(add, alp);

        empty = Ui.text(this, "Lista je prazna. Dodaj prvi sajt, npr. facebook.com", 14, Ui.MUTED, false);
        empty.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        root.addView(empty);

        ListView list = new ListView(this);
        list.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 12));
        list.setClipToPadding(false);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < sites.size()) {
                showEdit(sites.get(position));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refresh();
    }

    private void refresh() {
        sites.clear();
        java.util.Set<String> all = new java.util.TreeSet<>(store.siteListNow());
        all.addAll(store.siteList());
        sites.addAll(all);
        empty.setVisibility(sites.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    @Override protected void onResume() {
        super.onResume();
        if (empty != null) refresh();
    }

    private static String siteSummary(int limit) {
        return limit < 0 ? "Bez ograničenja" : limit == 0 ? "Uvek blokiran"
                : "Limit " + limit + " min dnevno";
    }

    /** domain == null znači dodavanje novog sajta. */
    private void showEdit(final String domain) {
        long busy = domain == null ? 0 : store.unlockBusyLeft();
        if (busy > 0) {
            // Inače bi se pauza posle otključavanja zaobišla brisanjem sajta sa liste.
            Toast.makeText(this, "Nedavno je nešto otključano. Može se menjati za " + Ui.fmt(busy) + ".",
                    Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = Ui.column(this);

        final CheckRow listed = new CheckRow(this, null, "Ograniči sajt", "Isključivanje važi od sledećih 06:00");
        listed.setChecked(domain == null || store.siteLimit(domain) >= 0);
        if (domain != null) box.addView(listed, Ui.fill(this, 0));

        box.addView(Sheet.label(this, "Adresa sajta"));
        final EditText address = Sheet.input(this, "npr. facebook.com", false);
        if (domain != null) {
            address.setText(domain);
            address.setEnabled(false);
            address.setTextColor(Ui.MUTED);
        }
        box.addView(address, Ui.fill(this, 6));

        box.addView(Sheet.label(this, "Dnevni limit u minutima (0 = uvek blokiran, otvara se dnevnom šifrom)"), Ui.fill(this, 16));
        final EditText limit = Sheet.input(this, "0", true);
        limit.setText(String.valueOf(domain == null ? 0 : Math.max(0, store.siteLimit(domain) < 0
                ? store.siteLimitNow(domain) : store.siteLimit(domain))));
        limit.setEnabled(listed.isChecked());
        listed.setListener(limit::setEnabled);
        box.addView(limit, Ui.fill(this, 6));
        if (domain != null && store.siteLimit(domain) > 0) {
            box.addView(Ui.text(this, "Danas: " + Ui.fmt(store.usedToday("site:" + domain)), 13, Ui.MUTED, false),
                    Ui.fill(this, 8));
        }

        Sheet sheet = new Sheet(this, domain == null ? "Dodaj sajt" : domain)
                .view(box)
                .secondary("Otkaži", null)
                .primary("Sačuvaj", () -> {
                    String host = Store.hostOf(address.getText().toString());
                    if (host == null || !host.contains(".")) {
                        address.setError("Unesi adresu sajta, npr. facebook.com");
                        return false;
                    }
                    String covering = store.matchSiteNow(host);
                    if (domain == null && covering != null && store.unlockBusyLeft() > 0) {
                        address.setError(covering + " je već na listi, a pauza posle otključavanja još traje");
                        return false;
                    }
                    Integer value = 0;
                    if (listed.isChecked()) value = Ui.nonNegativeNumber(limit);
                    if (value == null) return false;
                    if (listed.isChecked()) store.setSite(host, value); else store.removeSite(host);
                    refresh();
                    return true;
                });
        if (domain != null) {
            sheet.danger("Obriši sa liste", () -> {
                store.removeSite(domain);
                refresh();
                return true;
            });
        }
        sheet.show();
    }
}
