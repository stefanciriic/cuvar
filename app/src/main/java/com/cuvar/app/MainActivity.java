package com.cuvar.app;

import com.cuvar.app.rules.ItemKey;
import com.cuvar.app.data.AppLabels;
import com.cuvar.app.guard.UsageAccounting;

import com.cuvar.app.data.Store;
import com.cuvar.app.data.Unlocks;
import com.cuvar.app.guard.AndroidUsage;
import com.cuvar.app.guard.ForegroundApp;
import com.cuvar.app.i18n.Lang;
import com.cuvar.app.rules.DailyCode;
import com.cuvar.app.rules.DailySchedule;
import com.cuvar.app.rules.DayLimit;
import com.cuvar.app.rules.RemovalWindow;
import com.cuvar.app.data.Removals;
import com.cuvar.app.ui.Charts;
import com.cuvar.app.ui.SettingsSheets;
import com.cuvar.app.ui.Sheet;
import com.cuvar.app.ui.Safe;
import com.cuvar.app.ui.Ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
        public String label;
        public long ms;
    }

    private Store store;
    private SettingsSheets sheets;
    private boolean dark;
    private LinearLayout nowBox; // sadržaj kartice „Sada“, osvežava se dok je ekran otvoren
    private LinearLayout todaySlot;
    private LinearLayout codeSlot;
    private TextView subtitle;
    private String shownDate;
    private String shownCodeState;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable refreshNow = new Runnable() {
        @Override
        public void run() {
            fillNow();
            refreshTimedCards();
            h.postDelayed(this, nextRefreshDelay());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.theme(this);
        dark = Ui.dark;
        super.onCreate(savedInstanceState);
        Safe.screen(this, "mainCreate", () -> {
            store = Store.get(this);
            sheets = new SettingsSheets(this, store, this::showDashboard);
        });
        Ui.styleWindow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.wantsDark(this) != dark) {
            recreate();
            return;
        }
        Safe.screen(this, "dashboard", this::showDashboard);
        Safe.run("askRemoval", this::askRemoval);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(refreshNow);
    }

    // ---------- Ekrani ----------

    /** Izabrana kartica donje trake: 0 = Danas, 1 = Pravila (Statistika je poseban ekran). */
    public static int tab;

    private void setScreen(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        scroll.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(Ui.bottomBar(this, tab, this::openTab));
        setContentView(root);
    }

    private void openTab(int t) {
        if (t == 2) {
            startActivity(new Intent(this, StatsActivity.class).putExtra(StatsActivity.AS_TAB, true));
            overridePendingTransition(0, 0);
            return;
        }
        tab = t;
        showDashboard();
    }

    // ---------- Glavni ekran ----------

    private void showDashboard() {
        if (tab == 1) {
            showRules();
        } else {
            showToday();
        }
        h.removeCallbacks(refreshNow);
        h.postDelayed(refreshNow, nextRefreshDelay());
    }

    /** Naslov kartice sa zupčanikom za podešavanja desno. */
    private LinearLayout titled(String title, String sub) {
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 28));
        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, title, 34, Ui.INK, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView gear = Ui.text(this, "⚙", 26, Ui.MUTED, false);
        int p = Ui.dp(this, 8);
        gear.setPadding(p, p, p, p);
        gear.setContentDescription("Podešavanja");
        gear.setOnClickListener(v -> sheets.showSettings());
        top.addView(gear);
        col.addView(top);
        subtitle = Ui.text(this, sub, 14, Ui.MUTED, false);
        col.addView(subtitle, Ui.fill(this, 2));
        return col;
    }

    /** Kartica Danas: šta važi sada, vreme danas i dnevna šifra. */
    private void showToday() {
        shownDate = dateLabel();
        LinearLayout col = titled("Čuvar", shownDate);

        boolean enabled = GuardService.isEnabled(this);
        if (!enabled) {
            col.addView(setupCard("Uključi Čuvara",
                    "U Pristupačnosti pronađi „Čuvar“ (na Samsungu pod Instalirane aplikacije, na drugim telefonima pod Preuzete aplikacije) i uključi ga. "
                            + "Bez toga merenje vremena i blokiranje ne rade. "
                            + "Čuvar tako vidi samo koja je aplikacija i koji sajt otvoren; ništa ne šalje sa telefona.\n\n"
                            + "Ako je prekidač siv ili piše „Ograničeno podešavanje“: Podešavanja → Aplikacije → Čuvar → "
                            + "meni ⋮ gore desno → „Dozvoli ograničena podešavanja“, pa ponovo ovde.",
                    "Otvori Pristupačnost", v -> openAccessibility(),
                    "Opcija je siva ili piše da je ograničena?", v -> showRestrictedHelp()), Ui.fill(this, 14));
        }

        if (enabled && !ForegroundApp.granted(this)) {
            col.addView(setupCard("Dozvoli pristup korišćenju",
                    "Tako Čuvar od samog telefona saznaje koja je aplikacija otvorena, pa ne opterećuje aplikacije "
                            + "bez pravila (mape, pozive, kameru). Pronađi „Čuvar“ na listi i uključi dozvolu.",
                    "Otvori podešavanje", v -> openUsageAccess(), null, null), Ui.fill(this, 14));
        }

        col.addView(Ui.section(this, "Sada"), Ui.fill(this, 24));
        col.addView(nowCard(), Ui.fill(this, 8));

        col.addView(Ui.section(this, "Danas"), Ui.fill(this, 24));
        todaySlot = Ui.column(this);
        todaySlot.addView(todayCard(enabled));
        col.addView(todaySlot, Ui.fill(this, 8));
        codeSlot = Ui.column(this);
        codeSlot.addView(codeCard());
        shownCodeState = codeState();
        col.addView(codeSlot, Ui.fill(this, 10));
        setScreen(col);
    }

    /** Kartica Pravila: ukupni limit i noćna blokada na vrhu, pa pravila po aplikaciji i sajtu. */
    private void showRules() {
        nowBox = null;
        todaySlot = null;
        codeSlot = null;
        LinearLayout col = titled("Pravila", "Pooštravanje važi odmah, a popuštanje tek sutra od 0"
                + DailyCode.NIGHT_END_HOUR + ":00.");

        View removals = removalCard(this, store, this::showDashboard);
        if (removals != null) col.addView(removals, Ui.fill(this, 18));
        View pending = pendingCard(this, store, this::showDashboard);
        if (pending != null) col.addView(pending, Ui.fill(this, 18));

        col.addView(Ui.section(this, "Za ceo telefon"), Ui.fill(this, 24));
        LinearLayout whole = Ui.group(this);
        Ui.groupRow(this, whole, "Ukupni dnevni limit", sheets.dayLimitSummary(), v -> sheets.chooseDayLimit());
        Ui.groupRow(this, whole, "Fokus · dozvoli samo izabrano", focusSummary(),
                v -> startActivity(new Intent(this, FocusActivity.class)));
        Ui.groupRow(this, whole, "Noćna blokada", store.nightBlockNow()
                ? "Uključena · od " + DailyCode.LOCK_HOUR + ":00 do 0" + DailyCode.NIGHT_END_HOUR + ":00 sve iz pravila je zaključano"
                    + (store.nightBlock() ? "" : " · isključuje se od sledećih 06:00")
                : "Isključena", v -> sheets.chooseNight());
        Ui.groupRow(this, whole, "Zaštita od isključivanja", store.protectNow()
                ? "Uključena · Čuvar ne može da se isključi ni obriše"
                    + (store.protectSelf() ? "" : " · isključuje se od sledećih 06:00")
                : "Isključena", v -> sheets.chooseProtect());
        col.addView(whole, Ui.fill(this, 8));

        int items = store.appListNow().size() + store.siteListNow().size();
        col.addView(Ui.section(this, "Po aplikaciji i sajtu"), Ui.fill(this, 24));
        LinearLayout rules = Ui.group(this);
        Ui.groupRow(this, rules, "Aplikacije i sajtovi",
                items == 0 ? "Zaključaj, postavi dnevni limit ili blokiraj u režimu" : "Šta je kad blokirano · pravila: " + items,
                v -> startActivity(new Intent(this, RulesActivity.class)));
        Ui.groupRow(this, rules, "Vremenski režimi", scheduleSummary(),
                v -> startActivity(new Intent(this, ScheduleActivity.class)));
        col.addView(rules, Ui.fill(this, 8));

        TextView note = Ui.text(this,
                "Savet: uključi Zaštitu od isključivanja, ili zaključaj Podešavanja telefona (u Aplikacijama), da Čuvar ne može lako da se isključi ili obriše.",
                13, Ui.MUTED, false);
        col.addView(note, Ui.fill(this, 18));
        setScreen(col);
    }

    private String focusSummary() {
        if (!store.focus.focusActive()) return "Isključen · pokreće se ručno kad ti treba";
        return "Uključen · još " + Ui.fmt(store.focus.focusLeft()) + " · samo izabrano je dostupno";
    }

    // ---------- Kartica „Sada“ ----------

    private static long nextRefreshDelay() {
        return Math.min(30000L, 60000L - System.currentTimeMillis() % 60000L);
    }

    private String dateLabel() {
        return new SimpleDateFormat(Lang.english ? "EEEE, d MMMM" : "EEEE, d. MMMM", Ui.locale()).format(new Date());
    }

    private String codeState() {
        DailySchedule.Rule active = store.modes.activeCodeRule();
        if (active != null) return "hidden:" + active.id + ":" + active.name + ":" + active.end;
        return store.unlock.dailyCodeVisible() ? "visible:" + store.unlock.dailyCode() : "unavailable";
    }

    /** Menja samo kartice čiji je vremenski sadržaj promenjen; dijalozi i skrol ostaju otvoreni. */
    private void refreshTimedCards() {
        if (todaySlot == null || codeSlot == null) return;
        String date = dateLabel();
        if (!date.equals(shownDate)) {
            shownDate = date;
            subtitle.setText(date);
            todaySlot.removeAllViews();
            todaySlot.addView(todayCard(GuardService.isEnabled(this)));
        }
        String state = codeState();
        if (!state.equals(shownCodeState)) {
            shownCodeState = state;
            codeSlot.removeAllViews();
            codeSlot.addView(codeCard());
        }
    }

    private View nowCard() {
        LinearLayout card = Ui.card(this);
        nowBox = Ui.column(this);
        card.addView(nowBox);
        fillNow();
        return card;
    }

    /** Šta upravo važi: da li Čuvar radi, režimi, otključavanje i pauza, potrošeni limiti, hitno otključavanje. */
    private void fillNow() {
        if (nowBox == null) return;
        nowBox.removeAllViews();

        boolean enabled = GuardService.isEnabled(this);
        if (enabled && GuardService.running) {
            nowLine("●  Čuvar radi", null, Charts.good());
        } else if (enabled) {
            View v = nowLine("●  Čuvar trenutno ne radi",
                    "Uključen je u Pristupačnosti, ali ga je telefon zaustavio. Tapni ovde, isključi ga i ponovo uključi.",
                    Ui.ACCENT);
            v.setOnClickListener(x -> openAccessibility());
        } else {
            nowLine("●  Čuvar je isključen", "Ništa se ne meri ni blokira dok ga ne uključiš.", Ui.ACCENT);
        }

        List<String> gaps = store.guardLog.guardGaps(7);
        if (!gaps.isEmpty()) {
            nowLine("Čuvar nije radio", android.text.TextUtils.join("\n", gaps.subList(0, Math.min(3, gaps.size()))), Ui.ACCENT);
        }

        boolean any = false;
        if (store.nightActive()) {
            nowLine("Noćna blokada", "Do 0" + DailyCode.NIGHT_END_HOUR + ":00 sve iz pravila je zaključano.", Ui.INK);
            any = true;
        }
        for (DailySchedule.Rule r : store.modes.activeSchedules()) {
            if (r.apps.isEmpty() && r.sites.isEmpty()) continue;
            nowLine("Režim „" + r.name + "“", "Traje do " + DailySchedule.label(r.end)
                    + ". Do tada se " + (r.apps.size() + r.sites.size() == 1 ? "njegova stavka ne otključava." : "njegove stavke ne otključavaju."),
                    Ui.INK);
            any = true;
        }

        long use = store.unlock.unlockUseLeft();
        long busy = store.unlock.unlockBusyLeft();
        if (use > 0) {
            nowLine("Otključano", "Još " + minutes(use) + ", pa pauza od "
                    + Unlocks.UNLOCK_COOLDOWN_MS / 60000L + " min bez otključavanja.", Ui.INK);
            any = true;
        } else if (busy > 0) {
            nowLine("Pauza posle otključavanja", "Još " + minutes(busy) + " ništa ne može da se otključa.", Ui.INK);
            any = true;
        }

        List<String> over = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (String pkg : store.appsOverLimit()) {
            String label = AppLabels.orNull(pm, pkg);
            over.add(label == null ? pkg : label);
        }
        over.addAll(store.sitesOverLimit());
        List<String> rest = new ArrayList<>();
        for (String pkg : store.sessions.appsOnBreak()) {
            String label = AppLabels.orNull(pm, pkg);
            rest.add((label == null ? pkg : label) + " još " + minutes(store.sessions.sessionBreakLeft(pkg)));
        }
        if (!rest.isEmpty()) {
            nowLine("Pauza posle korišćenja u komadu", android.text.TextUtils.join(", ", rest) + ".", Ui.INK);
            any = true;
        }
        if (!over.isEmpty()) {
            nowLine("Potrošen dnevni limit", android.text.TextUtils.join(", ", over)
                    + ". Važi do ponoći.", Ui.INK);
            any = true;
        }

        if (!any) {
            nowLine("Nijedan režim ni limit trenutno ne traje", null, Ui.MUTED);
        }
        nowLine("Hitno otključavanje", store.unlock.emergencyAvailable()
                ? "Dostupno još jednom danas."
                : "Danas je iskorišćeno. Novo je posle ponoći.", Ui.MUTED);
        int streak = store.unlock.unlockStreak();
        if (streak > 0) {
            nowLine("Niz bez otključavanja", Ui.count(streak, "dan", "dana", "dana") + " zaredom.", Ui.MUTED);
        }
    }

    private View nowLine(String title, String detail, int color) {
        LinearLayout line = Ui.column(this);
        line.addView(Ui.text(this, title, 15, color, true));
        if (detail != null) {
            line.addView(Ui.text(this, detail, 14, Ui.MUTED, false), Ui.fill(this, 2));
        }
        nowBox.addView(line, Ui.fill(this, nowBox.getChildCount() == 0 ? 0 : 12));
        return line;
    }

    private static String minutes(long ms) {
        return ((ms + 59999L) / 60000L) + " min";
    }

    private void openUsageAccess() {
        GuardService.usageSetupUntil = android.os.SystemClock.elapsedRealtime() + 2 * 60000L;
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Throwable t) {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        }
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

    /** Dnevna šifra za aplikacije režima sa šifrom; vidi se samo od 17:00 do 22:00 i dok takav režim ne traje. */
    private View codeCard() {
        LinearLayout card = Ui.card(this);
        TextView eyebrow = Ui.text(this, "DNEVNA ŠIFRA", 12, Ui.MUTED, true);
        eyebrow.setLetterSpacing(0.15f);
        card.addView(eyebrow);
        DailySchedule.Rule active = store.modes.activeCodeRule();
        if (active != null) {
            card.addView(Ui.text(this, "Skrivena", 30, Ui.INK, true), Ui.fill(this, 4));
            card.addView(Ui.text(this, "Režim „" + active.name + "“ traje do " + DailySchedule.label(active.end)
                    + ". Šifra se vidi tek posle toga.", 14, Ui.MUTED, false), Ui.fill(this, 6));
            return card;
        }
        if (!store.unlock.dailyCodeVisible()) {
            card.addView(Ui.text(this, "Stiže u " + DailyCode.CHANGE_HOUR + ":00", 30, Ui.INK, true), Ui.fill(this, 4));
            card.addView(Ui.text(this, "Svaki dan dobijaš jednu novu šifru u " + DailyCode.CHANGE_HOUR
                    + ":00 i vidi se ovde do " + DailyCode.LOCK_HOUR + ":00.", 14, Ui.MUTED, false), Ui.fill(this, 6));
            return card;
        }
        TextView code = Ui.text(this, store.unlock.dailyCode(), 38, Ui.INK, true);
        code.setLetterSpacing(0.2f);
        card.addView(code, Ui.fill(this, 4));
        card.addView(Ui.text(this, "Današnja šifra. Jedino ona otključava zaključane aplikacije i sajtove, i to samo do " + DailyCode.LOCK_HOUR + ":00."
                + " Sutra u " + DailyCode.CHANGE_HOUR + ":00 dobijaš novu.",
                14, Ui.MUTED, false), Ui.fill(this, 6));
        return card;
    }

    private String scheduleSummary() {
        List<DailySchedule.Rule> rules = store.modes.schedulesNow();
        if (rules.isEmpty()) return "Još nema režima";
        java.util.Set<String> ids = new java.util.HashSet<>(), enabled = new java.util.HashSet<>();
        java.util.Set<String> periods = new java.util.LinkedHashSet<>();
        for (DailySchedule.Rule r : rules) {
            ids.add(r.id);
            if (r.enabled) enabled.add(r.id);
            periods.add(r.label());
        }
        if (ids.size() == 1) return (enabled.isEmpty() ? "Isključen · " : "Uključen · ")
                + android.text.TextUtils.join("; ", periods);
        return ids.size() + " režima · uključeno " + enabled.size();
    }

    /** Zahtevi za koje je već pitano u ovom pokretanju, da se pitanje ne ponavlja na svakom povratku. */
    private static final java.util.Set<String> asked = new java.util.HashSet<>();

    /** Od 08:00 do 22:00 sutradan pita za svaki zahtev za uklanjanje, jednom po pokretanju aplikacije. */
    private void askRemoval() {
        for (Removals.Request r : store.removals.list()) {
            if (r.state != RemovalWindow.ASK || !asked.add(r.key + "|" + store.day())) continue;
            removalSheet(this, store, r, this::showDashboard);
            return;
        }
    }

    private static String removalName(Activity a, Removals.Request r) {
        return r.pkg != null ? AppLabels.of(a.getPackageManager(), r.pkg) : r.key;
    }

    /** Pitanje „Da li si siguran?“; bez potvrde do 22:00 pravilo ostaje. */
    static void removalSheet(Activity a, Store store, Removals.Request r, Runnable refresh) {
        new Sheet(a, "Ukloniti iz pravila?")
                .message(removalName(a, r) + "\n\n" + "Da li si siguran da želiš da ukloniš? Bez potvrde do 22:00 ostaje zaključano.")
                .secondary("Ne, ostavi", () -> {
                    store.removals.cancel(r.key);
                    refresh.run();
                    return true;
                })
                .primary("Da, ukloni", () -> {
                    if (store.removals.confirm(r.key)) Ui.toast(a, "Uklonjeno iz pravila.");
                    refresh.run();
                    return true;
                })
                .show();
    }

    /** Zahtevi za uklanjanje iz pravila: čekaju sutra od 08:00 ili traže potvrdu; null ako ih nema. */
    public static View removalCard(Activity a, Store store, Runnable refresh) {
        List<Removals.Request> list = store.removals.list();
        if (list.isEmpty()) return null;
        LinearLayout card = Ui.card(a);
        card.addView(Ui.text(a, "Uklanjanje iz pravila", 16, Ui.ACCENT, true));
        for (Removals.Request r : list) {
            boolean ask = r.state == RemovalWindow.ASK;
            card.addView(Ui.text(a, removalName(a, r), 15, Ui.INK, true), Ui.fill(a, 10));
            card.addView(Ui.text(a, ask
                            ? "Da li si siguran da želiš da ukloniš? Bez potvrde do 22:00 ostaje zaključano."
                            : "Ostaje zaključano. Sutra od 08:00 do 22:00 Čuvar pita da li si siguran, i tek tada se uklanja.",
                    13, Ui.MUTED, false), Ui.fill(a, 2));
            TextView b = Ui.button(a, ask ? "Odgovori" : "Odustani od uklanjanja", false);
            b.setOnClickListener(Safe.click("removalCard", v -> {
                if (ask) {
                    removalSheet(a, store, r, refresh);
                } else {
                    store.removals.cancel(r.key);
                    refresh.run();
                }
            }));
            card.addView(b, Ui.fill(a, 8));
        }
        return card;
    }

    /** Popuštanja pravila koja čekaju sutra, sa dugmetom da se od njih odustane; null ako ih nema. */
    public static View pendingCard(android.app.Activity a, Store store, Runnable refresh) {
        List<String> items = store.pendingChanges(a.getPackageManager());
        if (items.isEmpty()) return null;
        LinearLayout card = Ui.card(a);
        card.addView(Ui.text(a, "Zakazane promene", 16, Ui.ACCENT, true));
        card.addView(Ui.text(a, (items.size() == 1 ? "1 promena" : items.size() + " promena")
                        + " čeka primenu sutra u 0" + DailyCode.NIGHT_END_HOUR + ":00. Do tada ostaje strožije pravilo.",
                13, Ui.MUTED, false),
                Ui.fill(a, 4));
        TextView details = Ui.button(a, "Prikaži promene", false);
        details.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (String it : items) sb.append(sb.length() == 0 ? "" : "\n\n").append(it);
            new Sheet(a, "Promene od sutra")
                    .message("Ove izmene će početi u 0" + DailyCode.NIGHT_END_HOUR + ":00:\n\n" + sb)
                    .secondary("Zatvori", null)
                    .show();
        });
        card.addView(details, Ui.fill(a, 10));
        TextView cancel = Ui.button(a, "Odustani od ovih promena", false);
        cancel.setOnClickListener(v -> {
            store.cancelPending();
            refresh.run();
        });
        card.addView(cancel, Ui.fill(a, 12));
        return card;
    }

    /**
     * Današnje vreme po aplikaciji iz Androidovih zapisa (isto što pokazuje Digital Wellbeing), ili null dok se
     * računa. Čuvarovo merenje je za limite i ne broji sve (pauze u merenju), pa je pokazivalo manje.
     */
    private Map<String, Long> todayFromAndroid() {
        String today = store.usageLog.lastDays(1).get(0);
        return AndroidUsage.day(this, today, today, () -> h.post(Safe.task("todayUsage", () -> {
            if (todaySlot == null || isFinishing()) return;
            todaySlot.removeAllViews();
            todaySlot.addView(todayCard(GuardService.isEnabled(this)));
        })));
    }

    private View todayCard(boolean enabled) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Vreme na telefonu", 14, Ui.MUTED, false));

        PackageManager pm = getPackageManager();
        String home = homePackage();
        List<Row> rows = new ArrayList<>();
        long total = 0;
        Map<String, Long> android = todayFromAndroid();
        boolean fromAndroid = android != null && !android.isEmpty();
        for (Map.Entry<String, Long> e : (fromAndroid ? android : store.usageLog.todayMap()).entrySet()) {
            String key = e.getKey();
            if (ItemKey.isSite(key) || key.startsWith("web:") || key.equals(home)
                    || !UsageAccounting.tracked(this, key)) {
                continue;
            }
            String label = AppLabels.orNull(pm, key);
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

        card.addView(Ui.text(this, Ui.fmt(total), 38, Ui.INK, true), Ui.fill(this, 2));
        if (fromAndroid) card.addView(Ui.text(this, "Po zapisima Androida, kao Digital Wellbeing.", 12, Ui.MUTED, false));
        int limit = store.dayLimits.dayLimit();
        if (limit > 0) {
            // Limit troši samo vreme pod pravilima, pa je manje od ukupnog vremena iznad.
            long counted = store.dayLimits.guardedToday();
            String line = DayLimit.reached(limit, counted)
                    ? "Dnevni limit od " + DayLimit.label(limit).toLowerCase(Locale.ROOT) + " je potrošen. Zaključano je do ponoći."
                    : "Dnevni limit: " + Ui.fmt(counted) + " od " + DayLimit.label(limit).toLowerCase(Locale.ROOT)
                    + " u aplikacijama i sajtovima sa pravilom. Ostalo je " + Ui.fmt(limit * 60000L - counted) + ".";
            card.addView(Ui.text(this, line, 14, DayLimit.reached(limit, counted) ? Ui.ACCENT : Ui.MUTED, true),
                    Ui.fill(this, 4));
        }

        if (rows.isEmpty()) {
            card.addView(Ui.text(this,
                    enabled ? "Još nema podataka za danas. Otvori neku aplikaciju pa se vrati."
                            : "Merenje počinje kad uključiš Čuvara.",
                    14, Ui.MUTED, false), Ui.fill(this, 6));
            return card;
        }

        long max = Math.max(1L, rows.get(0).ms);
        int n = Math.min(rows.size(), 5);
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

    private String homePackage() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (r != null && r.activityInfo != null) {
                return r.activityInfo.packageName;
            }
        } catch (Throwable ignored) {
            // nema podrazumevane aplikacije: vraća se null
        }
        return "";
    }

    // ---------- Dozvole ----------

    /**
     * Objašnjenje i pristanak pre uključivanja Pristupačnosti, kako traže pravila Google Play-a:
     * šta Čuvar vidi, zašto, i da ništa ne napušta telefon.
     */
    private void openAccessibility() {
        if (GuardService.isEnabled(this) || getSharedPreferences("ui", MODE_PRIVATE).getBoolean("a11yConsent", false)) {
            openAccessibilitySettings();
            return;
        }
        new Sheet(this, "Čuvar koristi Pristupačnost")
                .message("Da bi merio vreme i blokirao ono što izabereš, Čuvar koristi Androidovu uslugu Pristupačnosti (AccessibilityService). Iz aktivnog prozora koristi:\n\n"
                        + "•  koja je aplikacija trenutno otvorena,\n"
                        + "•  adresu sajta u pregledaču (samo naziv sajta),\n"
                        + "•  ograničen broj tekstualnih čvorova pri vrhu ugrađenog pregledača,\n"
                        + "•  da bi preko blokirane aplikacije prikazao ekran za blokadu.\n\n"
                        + "Čuvar ne čuva niti šalje poruke, lozinke ili tekst stranica, ne snima ekran i nema dozvolu za internet. "
                        + "Ako uključiš Zaštitu od isključivanja, na ekranima podešavanja proverava samo da li se pominje Čuvar. "
                        + "Sve što izmeri ostaje samo na ovom telefonu i briše se kad obrišeš aplikaciju.\n\n"
                        + "Ako se slažeš, u sledećem koraku uključi „Čuvar“ u Pristupačnosti.")
                .secondary("Ne sada", null)
                .primary("Slažem se", () -> {
                    getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("a11yConsent", true).apply();
                    openAccessibilitySettings();
                    return true;
                })
                .show();
    }

    private void openAccessibilitySettings() {
        // Android 13+: odmah Čuvarov prekidač u Pristupačnosti, bez traženja po listi.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                startActivity(new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                        .putExtra(Intent.EXTRA_COMPONENT_NAME,
                                new android.content.ComponentName(this, GuardService.class).flattenToString()));
                return;
            } catch (Throwable ignored) {
                // ovaj Android nema direktan ekran za servis: ispod se otvara opšti ekran Pristupačnosti
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable t) {
            Ui.toast(this, "Otvori Podešavanja → Pristupačnost ručno");
        }
    }

    private void showRestrictedHelp() {
        new Sheet(this, "Ograničena podešavanja")
                .message("Android ovo blokira za aplikacije koje nisu iz Play prodavnice. Uradi ovako:\n\n"
                        + "1. Tapni „Informacije o aplikaciji“ ispod.\n"
                        + "2. Tapni tri tačke gore desno.\n"
                        + "3. Izaberi „Dozvoli ograničena podešavanja“ i potvrdi.\n"
                        + "4. Vrati se ovde i ponovo otvori Pristupačnost.\n\n"
                        + "Ako ne vidiš tri tačke, prvo pokušaj da uključiš Čuvara u Pristupačnosti, "
                        + "pa kad te telefon odbije, vrati se na ovaj korak.")
                .secondary("Zatvori", null)
                .primary("Informacije o aplikaciji", () -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Throwable ignored) {
                        // nema ni ekrana sa podacima o aplikaciji: korisnik ostaje na Čuvaru
                    }
                    return true;
                })
                .show();
    }
}
