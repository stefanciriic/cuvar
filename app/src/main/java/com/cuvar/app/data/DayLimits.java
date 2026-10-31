package com.cuvar.app.data;

import com.cuvar.app.GuardService;
import com.cuvar.app.guard.AppKinds;
import com.cuvar.app.rules.DayLimit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Ukupni dnevni limit za ceo telefon. */
public final class DayLimits {
    private final Store store;

    DayLimits(Store store) {
        this.store = store;
    }

    /**
     * Ključ u izmerenom vremenu pod kojim servis beleži vreme pod pravilima: dok je napred aplikacija
     * sa pravilom ili se vidi sajt sa pravilom. Nije aplikacija, pa se ne prikazuje u vremenu ni statistici.
     */
    public static final String GUARDED_KEY = "limit:guarded";

    /** Danas pod pravilima. Samo ovo troši ukupni dnevni limit; mape, pozivi i ostalo bez pravila ne. */
    public long guardedToday() {
        synchronized (store) {
            return store.usageLog.usedToday(GUARDED_KEY);
        }
    }

    /**
     * Ranije je limit trošilo sve vreme na telefonu. Pri prvom pokretanju ove verzije današnje vreme
     * pod pravilima se procenjuje iz već izmerenog, a ako po tome limit nije potrošen, današnja
     * blokada i upozorenje se poništavaju.
     */
    void countOnlyGuarded(Context c) {
        if (store.sp.getBoolean("dayLimitGuarded", false)) return;
        String today = store.day();
        JSONObject d = store.usageLog.usage.optJSONObject(today);
        if (d != null && !d.has(GUARDED_KEY)) {
            Set<String> guarded = store.guardedApps();
            guarded.removeAll(AppKinds.exemptApps(c));
            try {
                d.put(GUARDED_KEY, DayLimit.guardedEstimate(store.usageLog.dayMap(today), guarded, store.siteKeysNow()));
                store.dirty = true;
            } catch (JSONException ignored) {
            }
        }
        SharedPreferences.Editor e = store.sp.edit().putBoolean("dayLimitGuarded", true);
        if (!DayLimit.reached(dayLimit(), guardedToday())) e.remove("dayLimitHit").remove("dayLimitWarned");
        e.apply();
        store.flush();
    }

    /** Ukupni dnevni limit u minutima koji danas važi (0 = isključen); zakazana promena stupa na snagu u ponoć. */
    public int dayLimit() {
        synchronized (store) {
            int cur = store.sp.getInt("dayLimit", 0);
            String from = store.sp.getString("dayLimitFrom", null);
            if (from != null && store.sp.getInt("dayLimitNext", -1) > 0) {
                // Zakazano povećanje iz ranije verzije više ne važi: povećava se samo jednom dnevno i malo.
                store.sp.edit().remove("dayLimitNext").remove("dayLimitFrom").apply();
                from = null;
            }
            String today = store.day();
            int eff = DayLimit.effective(cur, store.sp.getInt("dayLimitNext", -1), from, today);
            if (from != null && today.compareTo(from) >= 0) {
                store.sp.edit().putInt("dayLimit", eff).remove("dayLimitNext").remove("dayLimitFrom").apply();
            }
            return eff;
        }
    }

    /** Vrednost zakazana od sutra, ili -1 ako je nema. */
    public int dayLimitNext() {
        synchronized (store) {
            dayLimit();
            return store.sp.contains("dayLimitFrom") ? store.sp.getInt("dayLimitNext", -1) : -1;
        }
    }

    /** Strožiji limit (ili uključivanje) važi odmah i poništava zakazano isključivanje. Vraća false ako bi bio blaži. */
    public boolean lowerDayLimit(int min) {
        synchronized (store) {
            int cur = dayLimit();
            if (!DayLimit.appliesNow(cur, min)) return false;
            store.sp.edit().putInt("dayLimit", min).remove("dayLimitNext").remove("dayLimitFrom").apply();
            return true;
        }
    }

    /** Da li danas još može da se poveća limit (jednom dnevno, samo kad je uključen i još nije potrošen). */
    public boolean canRaiseDayLimit() {
        synchronized (store) {
            String d = store.day();
            return dayLimit() > 0 && !d.equals(store.sp.getString("dayLimitRaised", null)) && !d.equals(store.sp.getString("dayLimitHit", null));
        }
    }

    /** Da li je ukupni limit danas već potrošen; tada je sve zaključano do ponoći i povećanje ne pomaže. */
    public boolean dayLimitHitToday() {
        synchronized (store) {
            return store.day().equals(store.sp.getString("dayLimitHit", null));
        }
    }

    /** Servis beleži da je limit potrošen, pa se posle toga danas više ne može povećati. */
    /** Zašto bi aplikacija bez pravila bila zaključana do jutra čim dobije pravilo, ili null. */
    public String lockedOnceGuarded() {
        synchronized (store) {
            if (store.nightActive()) return "Sada traje noćna blokada";
            if (dayLimit() > 0 && dayLimitHitToday()) return "Ukupni dnevni limit je danas potrošen";
            return null;
        }
    }

    public void markDayLimitHit() {
        synchronized (store) {
            String d = store.day();
            if (!d.equals(store.sp.getString("dayLimitHit", null))) store.sp.edit().putString("dayLimitHit", d).apply();
        }
    }

    /** Povećava limit za najviše dozvoljeno; vraća koliko je minuta dodato (0 ako danas više ne može). */
    public int raiseDayLimit() {
        synchronized (store) {
            if (!canRaiseDayLimit()) return 0;
            int cur = dayLimit();
            int add = DayLimit.maxRaise(cur);
            store.sp.edit().putInt("dayLimit", cur + add).putString("dayLimitRaised", store.day()).apply();
            return add;
        }
    }

    /** Isključivanje važi tek od sutra. */
    public void turnOffDayLimitTomorrow() {
        synchronized (store) {
            if (dayLimit() <= 0) return;
            Calendar c = store.clock.calendarNow();
            c.add(Calendar.DAY_OF_MONTH, 1);
            String tomorrow = String.format(Locale.US, "%04d%02d%02d",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            store.sp.edit().putInt("dayLimitNext", 0).putString("dayLimitFrom", tomorrow).apply();
        }
    }

    /** Poništava zakazano isključivanje. */
    public void keepDayLimit() {
        synchronized (store) {
            store.sp.edit().remove("dayLimitNext").remove("dayLimitFrom").apply();
        }
    }

    /** Da li je danas već stiglo upozorenje pred kraj limita (pa se vraća true samo prvi put). */
    public boolean firstDayLimitWarning() {
        synchronized (store) {
            String d = store.day();
            if (d.equals(store.sp.getString("dayLimitWarned", null))) return false;
            store.sp.edit().putString("dayLimitWarned", d).apply();
            return true;
        }
    }
}
