package com.cuvar.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Builds the guarded app and site lock screen and its unlock interactions. */
final class BlockOverlayContent {
    private final GuardService host;

    BlockOverlayContent(GuardService host) {
        this.host = host;
    }

    /** Builds the lock screen and wires its existing unlock and exit actions. */
    View build(String key, int kind, DailySchedule.Rule rule, boolean cooling, boolean code) {
        if (kind == GuardService.KIND_PAUSE) return host.pauseOverlayContent.build(key);
        final Context c = host;
        final boolean isSite = key.startsWith("site:");
        String name = isSite ? key.substring(5) : host.appLabel(key.substring(4));

        String title;
        String sub;
        String joke;
        if (kind == GuardService.KIND_NIGHT) {
            title = "Noćna blokada";
            sub = "Od " + DailyCode.LOCK_HOUR + ":00 do 0" + DailyCode.NIGHT_END_HOUR + ":00 sve iz tvojih pravila je zaključano. "
                    + name + " se ne otvara do jutra, ni šifrom.";
            joke = Jokes.pick(Jokes.SCHEDULE);
        } else if (kind == GuardService.KIND_DAY) {
            title = "Dnevni limit je potrošen";
            sub = "Danas si u aplikacijama i na sajtovima iz svojih pravila proveo "
                    + Ui.fmt(host.store.guardedToday()) + ", a limit je " + DayLimit.label(host.store.dayLimit()) + ". " + name
                    + " je zaključan do ponoći i ne može da se otključa, ni šifrom.";
            joke = Jokes.pick(Jokes.TIME_UP);
        } else if (kind == GuardService.KIND_OPENS) {
            String pkg = key.substring(4);
            title = "Otvaranja za danas su potrošena";
            sub = name + " si danas otvorio " + Ui.count(host.store.opensToday(key), "put", "puta", "puta") + ", a dozvoljeno je "
                    + host.store.appOpensNow(pkg) + ". Do ponoći se ne otvara, ni šifrom.";
            joke = Jokes.pick(Jokes.TIME_UP);
        } else if (kind == GuardService.KIND_BREAK) {
            String pkg = key.substring(4);
            long left = host.store.sessionBreakLeft(pkg);
            title = "Vreme je za pauzu";
            sub = "Bio si u " + name + " " + host.store.appSessionNow(pkg) + " min u komadu. "
                    + name + " se ponovo otvara posle pauze, za " + Ui.fmt(left) + ", ni šifrom ranije.";
            joke = Jokes.pick(Jokes.TIME_UP);
        } else if (kind == GuardService.KIND_FOCUS) {
            title = "Fokus režim je aktivan";
            long left = host.store.focusLeft();
            sub = "Dozvoljene su samo aplikacije i sajtovi koje si izabrao. Fokus traje još "
                    + Ui.fmt(left) + " i ne može se zaobići dnevnom šifrom.";
            joke = Jokes.pick(Jokes.SCHEDULE);
        } else if (kind == GuardService.KIND_SCHEDULE && rule != null) {
            title = "Režim „" + rule.name + "“ je aktivan";
            sub = name + " je blokiran " + rule.daysLabel() + " od " + DailySchedule.label(rule.start)
                    + " do " + DailySchedule.label(rule.end) + "."
                    + (rule.code ? " Posle toga se otključava dnevnom šifrom." : "");
            joke = Jokes.pick(Jokes.SCHEDULE);
        } else if (kind == GuardService.KIND_CODE) {
            title = name + " je zaključan";
            sub = "Unesi dnevnu šifru. Važi od " + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00 i vidi se u Čuvaru.";
            joke = Jokes.pick(Jokes.LOCK);
        } else if (kind == GuardService.KIND_BROWSER) {
            title = name + " je zaključan";
            sub = "U ovom pregledaču Čuvar ne vidi koji je sajt otvoren, pa je zaključan dok imaš pravila za sajtove. "
                    + "Koristi Chrome, Samsung Internet, Firefox, Edge, Brave ili Vivaldi. Otvara se dnevnom šifrom, od "
                    + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00.";
            joke = Jokes.pick(Jokes.LOCK);
        } else if (kind == GuardService.KIND_CLONER) {
            title = name + " je zaključan";
            sub = "U aplikaciji za kloniranje bi se zaključane aplikacije otvarale pod drugim imenom. "
                    + "Zato je zaključana dok imaš pravila za aplikacije. Otvara se dnevnom šifrom, od "
                    + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00.";
            joke = Jokes.pick(Jokes.LOCK);
        } else if (kind == GuardService.KIND_INAPP) {
            title = "Link je zaključan";
            sub = "U pregledaču unutar aplikacije " + name + " Čuvar ne vidi koji je sajt otvoren, a imaš pravila za sajtove. "
                    + "Vrati se nazad ili otvori link u Chrome-u (meni sa tri tačke, „Otvori u pregledaču“). "
                    + "Otvara se i dnevnom šifrom, od " + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00.";
            joke = Jokes.pick(Jokes.SITE);
        } else if (kind == GuardService.KIND_ADDRESS) {
            title = "Adresa sajta nije dostupna";
            sub = "Čuvar još nije mogao da pročita adresu u " + name + ", a imaš pravila za sajtove. "
                    + "Vrati se nazad i prikaži adresnu traku ili otvori sajt u drugom podržanom pregledaču. "
                    + "Otključavanje dnevnom šifrom važi kao i za pregledač bez dostupne adrese.";
            joke = Jokes.pick(Jokes.SITE);
        } else if (kind == GuardService.KIND_LOCK) {
            title = name + " je zaključan";
            sub = "Otvara se dnevnom šifrom, od " + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00. Šifra se vidi u Čuvaru.";
            joke = Jokes.pick(Jokes.LOCK);
        } else if (kind == GuardService.KIND_SITE) {
            title = "Sajt je blokiran";
            sub = name + " je na tvojoj listi blokiranih sajtova. Otvara se dnevnom šifrom, od "
                    + DailyCode.CHANGE_HOUR + ":00 do " + DailyCode.LOCK_HOUR + ":00.";
            joke = Jokes.pick(Jokes.SITE);
        } else {
            title = "Vreme je isteklo";
            long used = host.store.usedShared(isSite ? key : key.substring(4));
            sub = "Danas si na " + name + " proveo " + Ui.fmt(used) + ". Dnevni limit je potrošen i "
                    + name + " je zaključan do ponoći, ni šifrom se ne otvara.";
            joke = Jokes.pick(Jokes.TIME_UP);
        }

        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Ui.NIGHT);
        scroll.setFillViewport(true);
        scroll.setClickable(true);

        LinearLayout box = Ui.column(c);
        box.setGravity(Gravity.CENTER);
        int p = Ui.dp(c, 24);
        box.setPadding(p, p, p, p);

        TextView eyebrow = Ui.text(c, "ČUVAR", 12, Ui.NIGHT_ACCENT, true);
        eyebrow.setLetterSpacing(0.25f);
        eyebrow.setGravity(Gravity.CENTER);
        box.addView(eyebrow, Ui.fill(c, 0));

        TextView t = Ui.text(c, title, 25, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, Ui.fill(c, 10));

        TextView s = Ui.text(c, sub, 15, Ui.NIGHT_MUTED, false);
        s.setGravity(Gravity.CENTER);
        box.addView(s, Ui.fill(c, 8));

        int tries = host.store.opensToday("try:" + key);
        if (tries > 1) {
            TextView tr = Ui.text(c, "Ovo ti je danas " + tries + ". pokušaj.", 15, 0xFFFFFFFF, true);
            tr.setGravity(Gravity.CENTER);
            box.addView(tr, Ui.fill(c, 10));
        }

        TextView j = Ui.text(c, joke, 16, Ui.NIGHT_ACCENT, false);
        j.setGravity(Gravity.CENTER);
        j.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        box.addView(j, Ui.fill(c, 16));

        // Najveće dugme na ekranu je izlaz: najlakši izbor je da odustaneš.
        TextView close = overlayButton(c, "Zatvori");
        close.setTextSize(17);
        close.setBackground(Ui.pressable(Ui.ACCENT, Ui.ACCENT_DOWN, Ui.dp(c, 14)));
        final int k = kind;
        close.setOnClickListener(v -> host.leaveBlock(key, k));
        box.addView(close, Ui.fill(c, 20));

        if (cooling) {
            // Pauza posle otključavanja: nema dugmeta ni PIN-a, samo odbrojavanje.
            host.cooldownLabel = Ui.text(c, GuardService.cooldownText(host.store.cooldownLeft(key)), 15, 0xFFFFFFFF, true);
            host.cooldownLabel.setGravity(Gravity.CENTER);
            box.addView(host.cooldownLabel, Ui.fill(c, 22));
            if (host.store.emergencyAvailable()) {
                final LinearLayout urgent = Ui.column(c);
                urgent.setGravity(Gravity.CENTER_HORIZONTAL);
                box.addView(urgent, Ui.fill(c, 16));
                TextView ask = overlayButton(c, "Hitno otključavanje (" + Store.EMERGENCY_MS / 60000L + " min)");
                ask.setTextColor(Ui.NIGHT_MUTED);
                ask.setBackground(null);
                ask.setOnClickListener(v -> showQuiz(urgent, () -> showEmergencyPad(urgent, key, code), null));
                urgent.addView(ask);
            }
        } else if (code && !GuardService.hard(kind)) {
            final LinearLayout unlock = Ui.column(c);
            unlock.setGravity(Gravity.CENTER_HORIZONTAL);
            box.addView(unlock, Ui.fill(c, 22));
            // Zaključana aplikacija odmah traži odgovor pa PIN; kod isteklog vremena i blokiranog sajta prvo pitamo.
            if (kind == GuardService.KIND_LOCK || kind == GuardService.KIND_CODE || kind == GuardService.KIND_BROWSER || kind == GuardService.KIND_CLONER) {
                showQuiz(unlock, () -> showPinPad(unlock, key, code), null);
            } else {
                TextView ask = overlayButton(c, "Ipak želim da otključam");
                ask.setTextColor(Ui.NIGHT_MUTED);
                ask.setBackground(null);
                ask.setOnClickListener(v -> showAreYouSure(unlock, key, k, code));
                unlock.addView(ask);
            }
        }

        LinearLayout actions = Ui.row(c);
        actions.setGravity(Gravity.CENTER);
        if (isSite || kind == GuardService.KIND_INAPP || kind == GuardService.KIND_ADDRESS) {
            TextView back = overlayButton(c, "Nazad");
            back.setOnClickListener(v -> {
                if ((key.startsWith("site:") || k == GuardService.KIND_ADDRESS) && host.browserForCurrentBlock() != null) {
                    host.leaveBlock(key, k);
                } else {
                    host.overlayGlobalBack();
                    host.h.removeCallbacks(host.recheckSoon);
                    host.h.postDelayed(host.recheckSoon, 600);
                }
            });
            actions.addView(back, actionParams(c));
        }
        if (actions.getChildCount() > 0) box.addView(actions, Ui.fill(c, 12));

        scroll.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** Šaljivo „Jesi li siguran?“ pre unosa PIN-a. */
    private void showAreYouSure(final LinearLayout area, final String key, final int kind, final boolean code) {
        final Context c = host;
        area.removeAllViews();
        TextView q = Ui.text(c, "Jesi li siguran?", 22, 0xFFFFFFFF, true);
        q.setGravity(Gravity.CENTER);
        area.addView(q, Ui.fill(c, 0));
        TextView why = Ui.text(c, Jokes.pick(Jokes.ARE_YOU_SURE), 15, Ui.NIGHT_MUTED, false);
        why.setGravity(Gravity.CENTER);
        area.addView(why, Ui.fill(c, 6));
        area.addView(rulesNote(c), Ui.fill(c, 8));

        TextView no = overlayButton(c, Jokes.pick(Jokes.NO));
        no.setBackground(Ui.pressable(Ui.ACCENT, Ui.ACCENT_DOWN, Ui.dp(c, 14)));
        no.setOnClickListener(v -> host.leaveBlock(key, kind));
        area.addView(no, Ui.fill(c, 16));

        TextView yes = overlayButton(c, Jokes.pick(Jokes.YES));
        yes.setOnClickListener(v -> showQuiz(area, () -> showPinPad(area, key, code), null));
        area.addView(yes, Ui.fill(c, 10));
    }

    /** Pitanje pre PIN-a. Tačan odgovor vodi dalje, a pogrešan donosi novo pitanje. */
    private void showQuiz(final LinearLayout area, final Runnable onPass, String notice) {
        final Context c = host;
        area.removeAllViews();
        final Quiz.Question q = Quiz.next();

        TextView head = Ui.text(c, "Prvo odgovori na pitanje", 20, 0xFFFFFFFF, true);
        head.setGravity(Gravity.CENTER);
        area.addView(head, Ui.fill(c, 0));
        if (notice != null) {
            TextView n = Ui.text(c, notice, 14, Ui.NIGHT_ACCENT, false);
            n.setGravity(Gravity.CENTER);
            area.addView(n, Ui.fill(c, 8));
        }
        TextView cat = Ui.text(c, q.category.toUpperCase(Locale.ROOT), 11, Ui.NIGHT_MUTED, true);
        cat.setLetterSpacing(0.15f);
        cat.setGravity(Gravity.CENTER);
        area.addView(cat, Ui.fill(c, 16));
        TextView text = Ui.text(c, q.text, 18, 0xFFFFFFFF, false);
        text.setGravity(Gravity.CENTER);
        area.addView(text, Ui.fill(c, 6));

        if (q.typed()) {
            final PinPad pad = new PinPad(c, true);
            pad.showDigits();
            pad.setListener(given -> {
                if (q.isCorrect(given)) {
                    onPass.run();
                } else {
                    showQuiz(area, onPass, "Netačno. Evo novog pitanja.");
                }
            });
            area.addView(pad, Ui.fill(c, 12));
        } else {
            for (final String choice : q.choices) {
                TextView b = overlayButton(c, choice);
                b.setOnClickListener(v -> {
                    if (q.isCorrect(choice)) {
                        onPass.run();
                    } else {
                        showQuiz(area, onPass, "Netačno. Evo novog pitanja.");
                    }
                });
                area.addView(b, Ui.fill(c, 10));
            }
        }
    }

    /** Tastatura za PIN, ili za dnevnu šifru kod režima sa šifrom. */
    private void showPinPad(final LinearLayout area, final String key, final boolean code) {
        area.removeAllViews();
        if (code) area.addView(codeNote(host), Ui.fill(host, 0));
        area.addView(rulesNote(host), Ui.fill(host, code ? 6 : 0));
        final PinPad pad = new PinPad(host, true);
        pad.setListener(pin -> {
            if (host.store.cooldownLeft(key) > 0) {
                host.safeCheck(); // pauza je počela dok je tastatura bila otvorena
                return;
            }
            String err = host.store.tryCode(pin);
            if (err == null) {
                host.store.startUnlock(key);
                host.safeCheck(); // another matching domain or rule may still require a block
            } else {
                pad.clear();
                pad.setMessage(err);
            }
        });
        area.addView(pad, Ui.fill(host, 12));
    }

    /** Hitno otključavanje mimo pauze: posle pitanja upozorenje pa PIN. */
    private void showEmergencyPad(final LinearLayout area, final String key, final boolean code) {
        final Context c = host;
        area.removeAllViews();
        TextView t = Ui.text(c, "Hitno otključavanje", 20, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        area.addView(t, Ui.fill(c, 0));
        String times = Store.EMERGENCY_PER_DAY == 1 ? "samo jednom dnevno"
                : Store.EMERGENCY_PER_DAY + " puta dnevno";
        TextView note = Ui.text(c, "Otključava na " + Store.EMERGENCY_MS / 60000L
                + " min i može se iskoristiti " + times + ". Ako ga sada potrošiš, do ponoći ga više nemaš.",
                13, Ui.NIGHT_MUTED, false);
        note.setGravity(Gravity.CENTER);
        area.addView(note, Ui.fill(c, 6));
        if (code) area.addView(codeNote(c), Ui.fill(c, 8));
        final PinPad pad = new PinPad(c, true);
        pad.setListener(pin -> {
            if (!host.store.emergencyAvailable()) {
                pad.clear();
                pad.setMessage("Hitno otključavanje je danas već iskorišćeno");
                return;
            }
            String err = host.store.tryCode(pin);
            if (err == null) {
                host.store.startEmergency(key);
                host.safeCheck();
            } else {
                pad.clear();
                pad.setMessage(err);
            }
        });
        area.addView(pad, Ui.fill(c, 12));
    }

    private static TextView codeNote(Context c) {
        TextView t = Ui.text(c, "Unesi dnevnu šifru (4 cifre) iz Čuvara.", 15, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** Upozorenje pre otključavanja: koliko traje i šta sledi posle. */
    private static TextView rulesNote(Context c) {
        TextView t = Ui.text(c, "Otključano je " + Store.UNLOCK_USE_MS / 60000L
                + " min, a posle toga " + Store.UNLOCK_COOLDOWN_MS / 60000L
                + " min nema otključavanja ničega, ni šifrom.", 13, Ui.NIGHT_MUTED, false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static TextView overlayButton(Context c, String label) {
        TextView b = Ui.text(c, label, 15, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.pressable(Ui.NIGHT_KEY, Ui.NIGHT_KEY_DOWN, Ui.dp(c, 14)));
        int ph = Ui.dp(c, 22);
        int pv = Ui.dp(c, 13);
        b.setPadding(ph, pv, ph, pv);
        return b;
    }

    private static LinearLayout.LayoutParams actionParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = Ui.dp(c, 6);
        lp.setMargins(m, 0, m, 0);
        return lp;
    }
}
