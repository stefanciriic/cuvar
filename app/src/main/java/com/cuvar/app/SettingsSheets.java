package com.cuvar.app;

import android.app.Activity;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Prozori sa početnog ekrana: podešavanja (tema, jezik, privatnost) i pravila za ceo telefon (dnevni limit, noć, zaštita). */
final class SettingsSheets {
    private final Activity a;
    private final Store store;
    private final Runnable refresh; // osvežava početni ekran posle izmene

    SettingsSheets(Activity a, Store store, Runnable refresh) {
        this.a = a;
        this.store = store;
        this.refresh = refresh;
    }

    /** Zupčanik: tema, privatnost i verzija. */
    void showSettings() {
        LinearLayout box = Ui.column(a);
        LinearLayout g = Ui.group(a);
        Sheet sheet = new Sheet(a, "Podešavanja").view(box).secondary("Zatvori", null);
        Ui.groupRow(a, g, "Tema", Ui.THEME_NAMES[Ui.themeChoice(a)], v -> {
            sheet.dismiss();
            chooseTheme();
        });
        Ui.groupRow(a, g, "Jezik", langName(Ui.langChoice(a)), v -> {
            sheet.dismiss();
            chooseScript();
        });
        Ui.groupRow(a, g, "Opterećenje telefona", "Koliko Čuvar troši procesor i memoriju", v -> {
            sheet.dismiss();
            showLoad();
        });
        Ui.groupRow(a, g, "Privatnost", "Šta Čuvar vidi i gde se čuva", v -> {
            sheet.dismiss();
            showPrivacy();
        });
        box.addView(g);
        TextView version = Ui.text(a, "Verzija " + BuildConfig.VERSION_NAME, 12, Ui.MUTED, false);
        version.setGravity(Gravity.CENTER);
        box.addView(version, Ui.fill(a, 14));
        sheet.show();
    }

    void chooseTheme() {
        LinearLayout box = Ui.column(a);
        Sheet sheet = new Sheet(a, "Tema").view(box).secondary("Zatvori", null);
        int current = Ui.themeChoice(a);
        for (int i = 0; i < Ui.THEME_NAMES.length; i++) {
            final int choice = i;
            TextView b = Ui.button(a, Ui.THEME_NAMES[i], i == current);
            b.setOnClickListener(v -> {
                sheet.dismiss();
                if (choice != Ui.themeChoice(a)) {
                    Ui.setThemeChoice(a, choice);
                    a.recreate();
                }
            });
            box.addView(b, Ui.fill(a, i == 0 ? 0 : 10));
        }
        sheet.show();
    }

    private static final String[] LANGS = {Ui.LANG_LATIN, Ui.LANG_CYRILLIC, Ui.LANG_ENGLISH};

    /** Naziv jezika uvek na tom jeziku, da se nađe i kad je izabran pogrešan. */
    static String langName(String lang) {
        if (Ui.LANG_ENGLISH.equals(lang)) return "English";
        if (Ui.LANG_CYRILLIC.equals(lang)) return "Српски (ћирилица)";
        return "Srpski (latinica)";
    }

    void chooseScript() {
        LinearLayout box = Ui.column(a);
        Sheet sheet = new Sheet(a, "Jezik").view(box).secondary("Zatvori", null);
        String current = Ui.langChoice(a);
        for (int i = 0; i < LANGS.length; i++) {
            final String lang = LANGS[i];
            TextView b = Ui.button(a, "", lang.equals(current));
            ((Ui.Label) b).setRaw(langName(lang)); // naziv je već na svom jeziku
            b.setOnClickListener(v -> {
                sheet.dismiss();
                if (!lang.equals(Ui.langChoice(a))) {
                    Ui.setLang(a, lang);
                    a.recreate();
                }
            });
            box.addView(b, Ui.fill(a, i == 0 ? 0 : 10));
        }
        if (!Lang.english) {
            box.addView(Ui.text(a, "Nazivi aplikacija i adrese sajtova ostaju latinicom. Adresu sajta možeš da ukucaš i ćirilicom.",
                    13, Ui.MUTED, false), Ui.fill(a, 12));
        }
        sheet.show();
    }

    String dayLimitSummary() {
        int limit = store.dayLimit();
        if (limit <= 0) return "Zajedničko vreme za sve iz tvojih pravila; kad ga potrošiš, zaključano je do ponoći";
        String s = DayLimit.label(limit) + " za sve iz tvojih pravila, posle toga ništa ne otključava";
        if (store.dayLimitNext() == 0) s += " · isključuje se od sutra";
        return s;
    }

    /** Izbor ukupnog limita: manji važi odmah, povećanje jednom dnevno za deo limita, isključivanje od sutra. */
    void chooseDayLimit() {
        final int limit = store.dayLimit();
        boolean offTomorrow = store.dayLimitNext() == 0;
        LinearLayout box = Ui.column(a);
        String intro = "Računa se samo vreme u aplikacijama i na sajtovima koji imaju pravilo "
                + "(zaključani, ograničeni ili u režimu). Mape, pozivi, poruke i sve ostalo bez pravila ne troši limit. "
                + "Kad se limit potroši, sve zaključane i ograničene aplikacije i sajtovi ostaju zaključani "
                + "do ponoći: bez dnevne šifre i hitnog otključavanja. Upozorenje stiže "
                + DayLimit.WARN_MS / 60000L + " min ranije.\n\n";
        intro += limit > 0
                ? "Manji limit važi odmah. Povećati se može jednom dnevno, najviše za "
                        + Math.round(DayLimit.raisePercent(limit)) + " % (što je limit veći, to manje), "
                        + "i samo dok limit još nije potrošen. "
                        + "Isključivanje važi tek od sutra."
                : "Izaberi limit. Posle toga se može smanjiti u svako doba, a povećati jednom dnevno i samo malo.";
        Sheet sheet = new Sheet(a, "Ukupni dnevni limit").message(intro).view(box).secondary("Zatvori", null);

        if (limit > 0) {
            box.addView(Sheet.label(a, "TRENUTNI LIMIT"), Ui.fill(a, 0));
            box.addView(Ui.text(a, DayLimit.label(limit), 28, Ui.INK, true), Ui.fill(a, 2));

            boolean can = store.canRaiseDayLimit();
            final int add = DayLimit.maxRaise(limit);
            String raise = can ? "Povećaj za " + Ui.fmt(add * 60000L) + " → " + DayLimit.label(limit + add)
                    : store.dayLimitHitToday() ? "Limit je potrošen, danas se više ne povećava"
                    : "Povećanje je danas već iskorišćeno";
            TextView up = Ui.button(a, raise, can);
            up.setEnabled(can);
            up.setAlpha(can ? 1f : 0.5f);
            up.setOnClickListener(v -> {
                sheet.dismiss();
                confirm("Povećati limit?", "Limit ide sa " + DayLimit.label(limit) + " na " + DayLimit.label(limit + add)
                        + ". Ovo je jedino povećanje danas i ne može se ponoviti do sutra.",
                        "Povećaj", () -> {
                            int added = store.raiseDayLimit();
                            Toast.makeText(a, Ui.cyr(added > 0 ? "Limit je sada " + DayLimit.label(store.dayLimit())
                                    : "Povećanje je danas već iskorišćeno."), Toast.LENGTH_LONG).show();
                        });
            });
            box.addView(up, Ui.fill(a, 14));
        }

        boolean header = false;
        for (final int min : DayLimit.CHOICES) {
            if (min <= 0 || (limit > 0 && min >= limit)) continue;
            if (!header && limit > 0) {
                box.addView(Sheet.label(a, "SMANJI LIMIT (VAŽI ODMAH)"), Ui.fill(a, 18));
                header = true;
            }
            String label = (limit > 0 ? "Smanji na " : "Postavi na ") + DayLimit.label(min)
                    + (min == DayLimit.SUGGESTED ? " (predlog)" : "");
            TextView b = Ui.button(a, label, false);
            final boolean wasFirst = box.getChildCount() == 0;
            b.setOnClickListener(v -> {
                sheet.dismiss();
                Runnable apply = () -> {
                    if (store.lowerDayLimit(min)) {
                        Toast.makeText(a, Ui.cyr("Limit je sada " + DayLimit.label(min)), Toast.LENGTH_LONG).show();
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
            box.addView(b, Ui.fill(a, wasFirst ? 0 : 10));
        }

        if (limit > 0) {
            TextView off = Ui.button(a, offTomorrow ? "Ipak ne isključuj sutra" : "Isključi od sutra", false);
            off.setOnClickListener(v -> {
                sheet.dismiss();
                if (offTomorrow) {
                    store.keepDayLimit();
                    Toast.makeText(a, Ui.cyr("Limit ostaje uključen."), Toast.LENGTH_LONG).show();
                } else {
                    store.turnOffDayLimitTomorrow();
                    Toast.makeText(a, Ui.cyr("Isključuje se od sutra. Danas ostaje "
                            + DayLimit.label(limit).toLowerCase(Locale.ROOT) + "."), Toast.LENGTH_LONG).show();
                }
                refresh.run();
            });
            box.addView(off, Ui.fill(a, 18));
        }
        sheet.show();
    }

    /** Pitanje pre promene limita koja se ne može vratiti; posle potvrde osvežava početni ekran. */
    private void confirm(String title, String message, String yes, Runnable onYes) {
        new Sheet(a, title).message(message)
                .secondary("Otkaži", null)
                .primary(yes, () -> {
                    onYes.run();
                    refresh.run();
                    return true;
                })
                .show();
    }

    /** Uključivanje važi odmah, isključivanje tek sutra od 06:00. */
    void chooseNight() {
        boolean on = store.nightBlock();
        Sheet sheet = new Sheet(a, "Noćna blokada").message("Od " + DailyCode.LOCK_HOUR + ":00 do 0"
                + DailyCode.NIGHT_END_HOUR + ":00 sve aplikacije i sajtovi iz tvojih pravila su zaključani i ne otvaraju se, "
                + "ni dnevnom šifrom ni hitnim otključavanjem. Pozivi i poruke rade.\n\n"
                + (on ? "Isključivanje važi tek sutra od 0" + DailyCode.NIGHT_END_HOUR + ":00." : "Uključivanje važi odmah."))
                .secondary("Zatvori", null);
        sheet.primary(on ? "Isključi od sutra" : "Uključi", () -> {
            store.setNightBlock(!on);
            refresh.run();
            return true;
        });
        sheet.show();
    }

    /** Zaštita od isključivanja: uključivanje važi odmah, isključivanje tek sutra od 06:00. */
    void chooseProtect() {
        boolean on = store.protectSelf();
        Sheet sheet = new Sheet(a, "Zaštita od isključivanja").message("Dok je uključena, Čuvar zatvara ekrane telefona "
                + "na kojima bi mogao da se isključi u Pristupačnosti, zaustavi, obriše mu se podaci ili da se deinstalira. "
                + "Ažuriranje Čuvara i dalje radi.\n\n"
                + (on ? "Isključivanje važi tek sutra od 0" + DailyCode.NIGHT_END_HOUR + ":00, pa tek tada Čuvar može da se ukloni."
                      : "Uključivanje važi odmah, a isključivanje tek sledećeg jutra od 0" + DailyCode.NIGHT_END_HOUR + ":00."))
                .secondary("Zatvori", null);
        sheet.primary(on ? "Isključi od sutra" : "Uključi", () -> {
            store.setProtectSelf(!on);
            refresh.run();
            return true;
        });
        sheet.show();
    }

    /** Merenje na samom telefonu: procesor celog Čuvara, memorija i provere ekrana od pokretanja servisa. */
    void showLoad() {
        long now = android.os.SystemClock.elapsedRealtime();
        long procUp = now - android.os.Process.getStartElapsedRealtime();
        long cpu = android.os.Process.getElapsedCpuTime();
        Runtime rt = Runtime.getRuntime();
        long mb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        StringBuilder sb = new StringBuilder();
        sb.append("Procesor: ").append(one(LoadMeter.percent(cpu, procUp))).append(" % vremena otkako Čuvar radi (")
                .append(Ui.fmt(procUp)).append(").\n");
        sb.append("Memorija: ").append(mb).append(" MB.\n\n");
        if (GuardService.running) {
            LoadMeter.Snapshot s = LoadMeter.GUARD.snapshot(now);
            sb.append("Provera ekrana: ").append(s.checks).append(", ").append(one(s.perMinute())).append(" u minutu.\n");
            sb.append("Jedna provera traje prosečno ").append(one(s.avgMs())).append(" ms, najduže ")
                    .append(one(s.maxMs())).append(" ms.\n");
            sb.append("Događaja Pristupačnosti: ").append(s.events).append(".\n\n");
        } else {
            sb.append("Servis Čuvara sada ne radi.\n\n");
        }
        sb.append("Ispod 1 % procesora Čuvar skoro ne troši bateriju. Brojevi kreću od nule kad se Čuvar ponovo pokrene.");
        new Sheet(a, "Opterećenje telefona")
                .message(sb.toString())
                .secondary("Zatvori", null)
                .primary("Osveži", () -> {
                    showLoad();
                    return true;
                })
                .show();
    }

    private static String one(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    void showPrivacy() {
        new Sheet(a, "Privatnost")
                .message("Čuvar nema dozvolu za internet i ne šalje nikakve podatke sa telefona. Nema naloga, reklama ni analitike.\n\n"
                        + "Preko Pristupačnosti dobija naziv otvorene aplikacije i sajta u pregledaču, a kod ugrađenih pregledača proverava ograničen deo ekrana. Iz toga pamti samo "
                        + "koliko si vremena proveo u kojoj aplikaciji i na kom sajtu, za poslednjih 14 dana, i tvoja pravila.\n\n"
                        + "Sve je sačuvano samo u memoriji Čuvara na ovom telefonu, ne ulazi u rezervnu kopiju i briše se kad obrišeš aplikaciju.")
                .secondary("Zatvori", null)
                .show();
    }
}
