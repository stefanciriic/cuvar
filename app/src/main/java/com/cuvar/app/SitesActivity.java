package com.cuvar.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
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
        LinearLayout box = Ui.column(this);
        int p = Ui.dp(this, 22);
        box.setPadding(p, Ui.dp(this, 10), p, 0);

        box.addView(Ui.text(this, "Adresa sajta", 14, Ui.MUTED, false));
        final EditText address = new EditText(this);
        address.setHint("npr. facebook.com");
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (domain != null) {
            address.setText(domain);
            address.setEnabled(false);
        }
        box.addView(address);

        box.addView(Ui.text(this, "Dnevni limit u minutima (0 = uvek blokiran)", 14, Ui.MUTED, false),
                Ui.fill(this, 14));
        final EditText limit = new EditText(this);
        limit.setInputType(InputType.TYPE_CLASS_NUMBER);
        limit.setSingleLine(true);
        limit.setText(String.valueOf(domain == null ? 0 : Math.max(0, store.siteLimit(domain))));
        box.addView(limit);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(domain == null ? "Dodaj sajt" : domain)
                .setView(box)
                .setPositiveButton("Sačuvaj", (d, w) -> {
                    String host = Store.hostOf(address.getText().toString());
                    if (host == null || !host.contains(".")) {
                        Toast.makeText(this, "Unesi adresu sajta, npr. facebook.com", Toast.LENGTH_LONG).show();
                        return;
                    }
                    store.setSite(host, Ui.parseInt(limit.getText().toString()));
                    refresh();
                })
                .setNegativeButton("Otkaži", null);
        if (domain != null) {
            b.setNeutralButton("Obriši", (d, w) -> {
                store.removeSite(domain);
                refresh();
            });
        }
        b.show();
    }
}
