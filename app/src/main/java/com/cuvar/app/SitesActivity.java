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
            row.setPadding(Ui.dp(SitesActivity.this, 20), Ui.dp(SitesActivity.this, 12),
                    Ui.dp(SitesActivity.this, 20), Ui.dp(SitesActivity.this, 12));
            row.addView(Ui.text(SitesActivity.this, domain, 17, Ui.INK, true));
            int limit = store.siteLimit(domain);
            String s = limit <= 0
                    ? "Uvek blokiran"
                    : "Limit " + limit + " min dnevno, danas " + Ui.fmt(store.usedToday("site:" + domain));
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
                "Radi u Chrome-u. Ako imaš i druge pregledače, zaključaj ih PIN-om na listi aplikacija."));

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
        sites.addAll(store.siteList());
        empty.setVisibility(sites.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
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

        box.addView(Sheet.label(this, "Adresa sajta"));
        final EditText address = Sheet.input(this, "npr. facebook.com", false);
        if (domain != null) {
            address.setText(domain);
            address.setEnabled(false);
            address.setTextColor(Ui.MUTED);
        }
        box.addView(address, Ui.fill(this, 6));

        box.addView(Sheet.label(this, "Dnevni limit u minutima (0 = uvek blokiran)"), Ui.fill(this, 16));
        final EditText limit = Sheet.input(this, "0", true);
        limit.setText(String.valueOf(domain == null ? 0 : Math.max(0, store.siteLimit(domain))));
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
                    String covering = store.matchSite(host);
                    if (domain == null && covering != null && store.unlockBusyLeft() > 0) {
                        address.setError(covering + " je već na listi, a pauza posle otključavanja još traje");
                        return false;
                    }
                    store.setSite(host, Ui.parseInt(limit.getText().toString()));
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
