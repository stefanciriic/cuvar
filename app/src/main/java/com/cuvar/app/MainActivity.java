package com.cuvar.app;

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
        store = Store.get(this);
        Ui.styleWindow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.wantsDark(this) != dark) {
            recreate();
            return;
        }
        showDashboard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(refreshNow);
    }

    // ---------- Ekrani ----------

    /** Izabrana kartica donje trake: 0 = Danas, 1 = Pravila (Statistika je poseban ekran). */
    static int tab;

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
        gear.setOnClickListener(v -> showSettings());
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
                    "U Pristupačnosti pronađi „Čuvar“ (pod Preuzete ili Instalirane aplikacije) i uključi ga. "
                            + "Bez toga merenje vremena i blokiranje ne rade. "
                            + "Čuvar tako vidi samo koja je aplikacija i koji sajt otvoren; ništa ne šalje sa telefona.\n\n"
                            + "Ako je prekidač siv ili piše „Ograničeno podešavanje“: Podešavanja → Aplikacije → Čuvar → "
                            + "meni ⋮ gore desno → „Dozvoli ograničena podešavanja“, pa ponovo ovde.",
                    "Otvori Pristupačnost", v -> openAccessibility(),
                    "Opcija je siva ili piše da je ograničena?", v -> showRestrictedHelp()), Ui.fill(this, 14));
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

        View pending = pendingCard(this, store, this::showDashboard);
        if (pending != null) col.addView(pending, Ui.fill(this, 18));

        col.addView(Ui.section(this, "Za ceo telefon"), Ui.fill(this, 24));
        LinearLayout whole = group();
        groupRow(whole, "Ukupni dnevni limit", dayLimitSummary(), v -> chooseDayLimit());
        groupRow(whole, "Noćna blokada", store.nightBlockNow()
                ? "Uključena · od " + DailyCode.LOCK_HOUR + ":00 do 0" + DailyCode.NIGHT_END_HOUR + ":00 sve iz pravila je zaključano"
                    + (store.nightBlock() ? "" : " · isključuje se od sledećih 06:00")
                : "Isključena", v -> chooseNight());
        groupRow(whole, "Zaštita od isključivanja", store.protectNow()
                ? "Uključena · Čuvar ne može da se isključi ni obriše"
                    + (store.protectSelf() ? "" : " · isključuje se od sledećih 06:00")
                : "Isključena", v -> chooseProtect());
        col.addView(whole, Ui.fill(this, 8));

        int appRules = store.appListNow().size();
        int siteRules = store.siteListNow().size();
        col.addView(Ui.section(this, "Po aplikaciji i sajtu"), Ui.fill(this, 24));
        LinearLayout rules = group();
        groupRow(rules, "Sva pravila na jednom mestu",
                "Šta je kad blokirano, po aplikaciji i sajtu",
                v -> startActivity(new Intent(this, RulesActivity.class)));
        groupRow(rules, "Aplikacije",
                appRules == 0 ? "Zaključaj, postavi dnevni limit ili broj otvaranja" : "Pravila: " + appRules,
                v -> startActivity(new Intent(this, AppsActivity.class)));
        groupRow(rules, "Sajtovi",
                siteRules == 0 ? "Blokiraj sajtove ili im postavi dnevni limit" : "Na listi: " + siteRules,
                v -> startActivity(new Intent(this, SitesActivity.class)));
        groupRow(rules, "Vremenski režimi", scheduleSummary(),
                v -> startActivity(new Intent(this, ScheduleActivity.class)));
        col.addView(rules, Ui.fill(this, 8));

        TextView note = Ui.text(this,
                "Savet: uključi Zaštitu od isključivanja, ili zaključaj Podešavanja telefona (u Aplikacijama), da Čuvar ne može lako da se isključi ili obriše.",
                13, Ui.MUTED, false);
        col.addView(note, Ui.fill(this, 18));
        setScreen(col);
    }

    // ---------- Kartica „Sada“ ----------

    private static long nextRefreshDelay() {
        return Math.min(30000L, 60000L - System.currentTimeMillis() % 60000L);
    }

    private String dateLabel() {
        return new SimpleDateFormat("EEEE, d. MMMM",
                new Locale.Builder().setLanguage("sr").setScript("Latn").build()).format(new Date());
    }

    private String codeState() {
        DailySchedule.Rule active = store.activeCodeRule();
        if (active != null) return "hidden:" + active.id + ":" + active.name + ":" + active.end;
        return store.dailyCodeVisible() ? "visible:" + store.dailyCode() : "unavailable";
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

        List<String> gaps = store.guardGaps(7);
        if (!gaps.isEmpty()) {
            nowLine("Čuvar nije radio", android.text.TextUtils.join("\n", gaps.subList(0, Math.min(3, gaps.size()))), Ui.ACCENT);
        }

        boolean any = false;
        if (store.nightActive()) {
            nowLine("Noćna blokada", "Do 0" + DailyCode.NIGHT_END_HOUR + ":00 sve iz pravila je zaključano.", Ui.INK);
            any = true;
        }
        for (DailySchedule.Rule r : store.activeSchedules()) {
            if (r.apps.isEmpty() && r.sites.isEmpty()) continue;
            nowLine("Režim „" + r.name + "“", "Traje do " + DailySchedule.label(r.end)
                    + ". Do tada se " + (r.apps.size() + r.sites.size() == 1 ? "njegova stavka ne otključava." : "njegove stavke ne otključavaju."),
                    Ui.INK);
            any = true;
        }

        long use = store.unlockUseLeft();
        long busy = store.unlockBusyLeft();
        if (use > 0) {
            nowLine("Otključano", "Još " + minutes(use) + ", pa pauza od "
                    + Store.UNLOCK_COOLDOWN_MS / 60000L + " min bez otključavanja.", Ui.INK);
            any = true;
        } else if (busy > 0) {
            nowLine("Pauza posle otključavanja", "Još " + minutes(busy) + " ništa ne može da se otključa.", Ui.INK);
            any = true;
        }

        List<String> over = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (String pkg : store.appsOverLimit()) {
            String label = labelOf(pm, pkg);
            over.add(label == null ? pkg : label);
        }
        over.addAll(store.sitesOverLimit());
        List<String> rest = new ArrayList<>();
        for (String pkg : store.appsOnBreak()) {
            String label = labelOf(pm, pkg);
            rest.add((label == null ? pkg : label) + " još " + minutes(store.sessionBreakLeft(pkg)));
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
        nowLine("Hitno otključavanje", store.emergencyAvailable()
                ? "Dostupno još jednom danas."
                : "Danas je iskorišćeno. Novo je posle ponoći.", Ui.MUTED);
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

    /** Zupčanik: tema, privatnost i verzija. */
    private void showSettings() {
        LinearLayout box = Ui.column(this);
        LinearLayout g = group();
        Sheet sheet = new Sheet(this, "Podešavanja").view(box).secondary("Zatvori", null);
        groupRow(g, "Tema", Ui.THEME_NAMES[Ui.themeChoice(this)], v -> {
            sheet.dismiss();
            chooseTheme();
        });
        groupRow(g, "Privatnost", "Šta Čuvar vidi i gde se čuva", v -> {
            sheet.dismiss();
            showPrivacy();
        });
        box.addView(g);
        TextView version = Ui.text(this, "Verzija " + BuildConfig.VERSION_NAME, 12, Ui.MUTED, false);
        version.setGravity(Gravity.CENTER);
        box.addView(version, Ui.fill(this, 14));
        sheet.show();
    }

    private void chooseTheme() {
        LinearLayout box = Ui.column(this);
        Sheet sheet = new Sheet(this, "Tema").view(box).secondary("Zatvori", null);
        int current = Ui.themeChoice(this);
        for (int i = 0; i < Ui.THEME_NAMES.length; i++) {
            final int choice = i;
            TextView b = Ui.button(this, Ui.THEME_NAMES[i], i == current);
            b.setOnClickListener(v -> {
                sheet.dismiss();
                if (choice != Ui.themeChoice(this)) {
                    Ui.setThemeChoice(this, choice);
                    recreate();
                }
            });
            box.addView(b, Ui.fill(this, i == 0 ? 0 : 10));
        }
        sheet.show();
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
        DailySchedule.Rule active = store.activeCodeRule();
        if (active != null) {
            card.addView(Ui.text(this, "Skrivena", 30, Ui.INK, true), Ui.fill(this, 4));
            card.addView(Ui.text(this, "Režim „" + active.name + "“ traje do " + DailySchedule.label(active.end)
                    + ". Šifra se vidi tek posle toga.", 14, Ui.MUTED, false), Ui.fill(this, 6));
            return card;
        }
        if (!store.dailyCodeVisible()) {
            card.addView(Ui.text(this, "Stiže u " + DailyCode.CHANGE_HOUR + ":00", 30, Ui.INK, true), Ui.fill(this, 4));
            card.addView(Ui.text(this, "Svaki dan dobijaš jednu novu šifru u " + DailyCode.CHANGE_HOUR
                    + ":00 i vidi se ovde do " + DailyCode.LOCK_HOUR + ":00.", 14, Ui.MUTED, false), Ui.fill(this, 6));
            return card;
        }
        TextView code = Ui.text(this, store.dailyCode(), 38, Ui.INK, true);
        code.setLetterSpacing(0.2f);
        card.addView(code, Ui.fill(this, 4));
        card.addView(Ui.text(this, "Današnja šifra. Jedino ona otključava zaključane aplikacije i sajtove, i to samo do " + DailyCode.LOCK_HOUR + ":00."
                + " Sutra u " + DailyCode.CHANGE_HOUR + ":00 dobijaš novu.",
                14, Ui.MUTED, false), Ui.fill(this, 6));
        return card;
    }

    private String dayLimitSummary() {
        int limit = store.dayLimit();
        if (limit <= 0) return "Zaključaj sve do ponoći kad pređeš ukupno vreme";
        String s = DayLimit.label(limit) + " na telefonu, posle toga ništa ne otključava";
        if (store.dayLimitNext() == 0) s += " · isključuje se od sutra";
        return s;
    }

    /** Izbor ukupnog limita: manji važi odmah, povećanje jednom dnevno za deo limita, isključivanje od sutra. */
    private void chooseDayLimit() {
        final int limit = store.dayLimit();
        boolean offTomorrow = store.dayLimitNext() == 0;
        LinearLayout box = Ui.column(this);
        String intro = "Računa se sve vreme na telefonu osim poziva, poruka, početnog ekrana i Čuvara. "
                + "Kad se limit potroši, sve zaključane i ograničene aplikacije i sajtovi ostaju zaključani "
                + "do ponoći: bez dnevne šifre i hitnog otključavanja. Upozorenje stiže "
                + DayLimit.WARN_MS / 60000L + " min ranije.\n\n";
        intro += limit > 0
                ? "Manji limit važi odmah. Povećati se može jednom dnevno, najviše za "
                        + Math.round(DayLimit.raisePercent(limit)) + " % (što je limit veći, to manje), "
                        + "i samo dok limit još nije potrošen. "
                        + "Isključivanje važi tek od sutra."
                : "Izaberi limit. Posle toga se može smanjiti u svako doba, a povećati jednom dnevno i samo malo.";
        Sheet sheet = new Sheet(this, "Ukupni dnevni limit").message(intro).view(box).secondary("Zatvori", null);

        if (limit > 0) {
            box.addView(Sheet.label(this, "TRENUTNI LIMIT"), Ui.fill(this, 0));
            box.addView(Ui.text(this, DayLimit.label(limit), 28, Ui.INK, true), Ui.fill(this, 2));

            boolean can = store.canRaiseDayLimit();
            final int add = DayLimit.maxRaise(limit);
            String raise = can ? "Povećaj za " + Ui.fmt(add * 60000L) + " → " + DayLimit.label(limit + add)
                    : store.dayLimitHitToday() ? "Limit je potrošen, danas se više ne povećava"
                    : "Povećanje je danas već iskorišćeno";
            TextView up = Ui.button(this, raise, can);
            up.setEnabled(can);
            up.setAlpha(can ? 1f : 0.5f);
            up.setOnClickListener(v -> {
                sheet.dismiss();
                confirm("Povećati limit?", "Limit ide sa " + DayLimit.label(limit) + " na " + DayLimit.label(limit + add)
                        + ". Ovo je jedino povećanje danas i ne može se ponoviti do sutra.",
                        "Povećaj", () -> {
                            int added = store.raiseDayLimit();
                            Toast.makeText(this, added > 0 ? "Limit je sada " + DayLimit.label(store.dayLimit())
                                    : "Povećanje je danas već iskorišćeno.", Toast.LENGTH_LONG).show();
                        });
            });
            box.addView(up, Ui.fill(this, 14));
        }

        boolean header = false;
        for (final int min : DayLimit.CHOICES) {
            if (min <= 0 || (limit > 0 && min >= limit)) continue;
            if (!header && limit > 0) {
                box.addView(Sheet.label(this, "SMANJI LIMIT (VAŽI ODMAH)"), Ui.fill(this, 18));
                header = true;
            }
            String label = (limit > 0 ? "Smanji na " : "Postavi na ") + DayLimit.label(min)
                    + (min == DayLimit.SUGGESTED ? " (predlog)" : "");
            TextView b = Ui.button(this, label, false);
            final boolean wasFirst = box.getChildCount() == 0;
            b.setOnClickListener(v -> {
                sheet.dismiss();
                Runnable apply = () -> {
                    if (store.lowerDayLimit(min)) {
                        Toast.makeText(this, "Limit je sada " + DayLimit.label(min), Toast.LENGTH_LONG).show();
                    }
                };
                if (limit <= 0) {
                    confirm("Uključiti limit od " + DayLimit.label(min) + "?",
                            "Važi odmah. Posle se može smanjiti u svako doba, a povećati samo jednom dnevno i malo "
                                    + "(sa " + DayLimit.label(min) + " najviše za " + Ui.fmt(DayLimit.maxRaise(min) * 60000L) + ").",
                            "Uključi", apply);
                } else {
                    confirm("Smanjiti limit na " + DayLimit.label(min) + "?",
                            "Limit postaje ukupno " + DayLimit.label(min) + " umesto " + DayLimit.label(limit)
                                    + ". Važi odmah i ne može se vratiti: posle toga se danas "
                                    + "može povećati najviše za " + Ui.fmt(DayLimit.maxRaise(min) * 60000L)
                                    + (store.canRaiseDayLimit() ? "." : ", a povećanje je danas već iskorišćeno."),
                            "Smanji", apply);
                }
            });
            box.addView(b, Ui.fill(this, wasFirst ? 0 : 10));
        }

        if (limit > 0) {
            TextView off = Ui.button(this, offTomorrow ? "Ipak ne isključuj sutra" : "Isključi od sutra", false);
            off.setOnClickListener(v -> {
                sheet.dismiss();
                if (offTomorrow) {
                    store.keepDayLimit();
                    Toast.makeText(this, "Limit ostaje uključen.", Toast.LENGTH_LONG).show();
                } else {
                    store.turnOffDayLimitTomorrow();
                    Toast.makeText(this, "Isključuje se od sutra. Danas ostaje "
                            + DayLimit.label(limit).toLowerCase(Locale.ROOT) + ".", Toast.LENGTH_LONG).show();
                }
                showDashboard();
            });
            box.addView(off, Ui.fill(this, 18));
        }
        sheet.show();
    }

    /** Pitanje pre promene limita koja se ne može vratiti; posle potvrde osvežava početni ekran. */
    private void confirm(String title, String message, String yes, Runnable onYes) {
        new Sheet(this, title).message(message)
                .secondary("Otkaži", null)
                .primary(yes, () -> {
                    onYes.run();
                    showDashboard();
                    return true;
                })
                .show();
    }

    /** Uključivanje važi odmah, isključivanje tek sutra od 06:00. */
    private void chooseNight() {
        boolean on = store.nightBlock();
        Sheet sheet = new Sheet(this, "Noćna blokada").message("Od " + DailyCode.LOCK_HOUR + ":00 do 0"
                + DailyCode.NIGHT_END_HOUR + ":00 sve aplikacije i sajtovi iz tvojih pravila su zaključani i ne otvaraju se, "
                + "ni dnevnom šifrom ni hitnim otključavanjem. Pozivi i poruke rade.\n\n"
                + (on ? "Isključivanje važi tek sutra od 0" + DailyCode.NIGHT_END_HOUR + ":00." : "Uključivanje važi odmah."))
                .secondary("Zatvori", null);
        sheet.primary(on ? "Isključi od sutra" : "Uključi", () -> {
            store.setNightBlock(!on);
            showDashboard();
            return true;
        });
        sheet.show();
    }

    /** Zaštita od isključivanja: uključivanje važi odmah, isključivanje tek sutra od 06:00. */
    private void chooseProtect() {
        boolean on = store.protectSelf();
        Sheet sheet = new Sheet(this, "Zaštita od isključivanja").message("Dok je uključena, Čuvar zatvara ekrane telefona "
                + "na kojima bi mogao da se isključi u Pristupačnosti, zaustavi, obriše mu se podaci ili da se deinstalira. "
                + "Ažuriranje Čuvara i dalje radi.\n\n"
                + (on ? "Isključivanje važi tek sutra od 0" + DailyCode.NIGHT_END_HOUR + ":00, pa tek tada Čuvar može da se ukloni."
                      : "Uključivanje važi odmah, a isključivanje tek sledećeg jutra od 0" + DailyCode.NIGHT_END_HOUR + ":00."))
                .secondary("Zatvori", null);
        sheet.primary(on ? "Isključi od sutra" : "Uključi", () -> {
            store.setProtectSelf(!on);
            showDashboard();
            return true;
        });
        sheet.show();
    }

    private String scheduleSummary() {
        List<DailySchedule.Rule> rules = store.schedulesNow();
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

    /** Popuštanja pravila koja čekaju sutra, sa dugmetom da se od njih odustane; null ako ih nema. */
    static View pendingCard(android.app.Activity a, Store store, Runnable refresh) {
        List<String> items = store.pendingChanges(a.getPackageManager());
        if (items.isEmpty()) return null;
        LinearLayout card = Ui.card(a);
        card.addView(Ui.text(a, "Od sutra", 16, Ui.ACCENT, true));
        card.addView(Ui.text(a, "Blaža pravila važe tek sutra od 0" + DailyCode.NIGHT_END_HOUR + ":00. Do tada važi strožije.", 13, Ui.MUTED, false),
                Ui.fill(a, 4));
        StringBuilder sb = new StringBuilder();
        for (String it : items) sb.append(sb.length() == 0 ? "" : "\n").append("•  ").append(it);
        card.addView(Ui.text(a, sb.toString(), 14, Ui.INK, false), Ui.fill(a, 8));
        TextView cancel = Ui.button(a, "Odustani od ovih promena", false);
        cancel.setOnClickListener(v -> {
            store.cancelPending();
            refresh.run();
        });
        card.addView(cancel, Ui.fill(a, 12));
        return card;
    }

    /** Kartica sa više redova odvojenih tankom linijom. */
    private LinearLayout group() {
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 18)));
        box.setClipToOutline(true);
        return box;
    }

    private void groupRow(LinearLayout group, String title, String sub, View.OnClickListener onClick) {
        if (group.getChildCount() > 0) {
            View line = new View(this);
            line.setBackgroundColor(Ui.LINE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
            lp.leftMargin = Ui.dp(this, 18);
            group.addView(line, lp);
        }
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.pressable(Ui.CARD, Ui.SOFT, 0));
        int p = Ui.dp(this, 16);
        row.setPadding(Ui.dp(this, 18), p, p, p);
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 16, Ui.INK, true));
        texts.addView(Ui.text(this, sub, 13, Ui.MUTED, false), Ui.fill(this, 2));
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, "›", 24, Ui.ACCENT, false), Ui.wrap(this, 12));
        row.setOnClickListener(onClick);
        group.addView(row, Ui.fill(this, 0));
    }

    private View todayCard(boolean enabled) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Vreme na telefonu", 14, Ui.MUTED, false));

        PackageManager pm = getPackageManager();
        String home = homePackage();
        String me = getPackageName();
        List<Row> rows = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Long> e : store.todayMap().entrySet()) {
            String key = e.getKey();
            if (key.startsWith("site:") || key.startsWith("web:") || key.equals(home) || key.equals(me)
                    || !GuardService.tracked(this, key)) {
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

        card.addView(Ui.text(this, Ui.fmt(total), 38, Ui.INK, true), Ui.fill(this, 2));
        int limit = store.dayLimit();
        if (limit > 0) {
            long counted = store.phoneToday(GuardService.exemptApps(this));
            String line = DayLimit.reached(limit, counted)
                    ? "Dnevni limit od " + DayLimit.label(limit).toLowerCase(Locale.ROOT) + " je potrošen. Zaključano je do ponoći."
                    : "Do dnevnog limita od " + DayLimit.label(limit).toLowerCase(Locale.ROOT) + " ostalo je "
                    + Ui.fmt(limit * 60000L - counted) + ".";
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
                .message("Da bi merio vreme i blokirao ono što izabereš, Čuvar koristi Androidovu uslugu Pristupačnosti (AccessibilityService). Preko nje vidi:\n\n"
                        + "•  koja je aplikacija trenutno otvorena,\n"
                        + "•  adresu sajta u pregledaču (samo naziv sajta),\n"
                        + "•  da bi preko blokirane aplikacije prikazao ekran za blokadu.\n\n"
                        + "Čuvar ne čita poruke, lozinke ni drugi tekst sa ekrana, ne snima ekran i nema dozvolu za internet. "
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
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable t) {
            Toast.makeText(this, "Otvori Podešavanja → Pristupačnost ručno", Toast.LENGTH_LONG).show();
        }
    }

    private void showPrivacy() {
        new Sheet(this, "Privatnost")
                .message("Čuvar nema dozvolu za internet i ne šalje nikakve podatke sa telefona. Nema naloga, reklama ni analitike.\n\n"
                        + "Preko Pristupačnosti vidi koja je aplikacija otvorena i naziv sajta u pregledaču. Iz toga pamti samo "
                        + "koliko si vremena proveo u kojoj aplikaciji i na kom sajtu, za poslednjih 14 dana, i tvoja pravila.\n\n"
                        + "Sve je sačuvano samo u memoriji Čuvara na ovom telefonu, ne ulazi u rezervnu kopiju i briše se kad obrišeš aplikaciju.")
                .secondary("Zatvori", null)
                .show();
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
                    }
                    return true;
                })
                .show();
    }
}
