package com.cuvar.app.rules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Broj primljenih notifikacija po danu i aplikaciji. Čuva se samo paket i broj, nikad tekst. */
public final class NotifCounts {
    /** Koliko dana se čuva (danas i prethodnih 13, za poređenje dve nedelje). */
    public static final int KEEP_DAYS = 14;
    private static final DateTimeFormatter KEY = DateTimeFormatter.BASIC_ISO_DATE;

    private NotifCounts() {
    }

    /** Da li se notifikacija broji: trajne (muzika, navigacija), zbirne i tihe izmene iste notifikacije ne. */
    public static boolean counts(boolean ongoing, boolean groupSummary, boolean alertOnce, boolean seenBefore) {
        if (ongoing || groupSummary) return false;
        return !(alertOnce && seenBefore);
    }

    /** Zapis: "dan:paket=broj,paket=broj;dan:...". */
    public static Map<String, Map<String, Integer>> decode(String s) {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        if (s == null || s.isEmpty()) return out;
        for (String day : s.split(";")) {
            int c = day.indexOf(':');
            if (c <= 0) continue;
            Map<String, Integer> apps = new HashMap<>();
            for (String part : day.substring(c + 1).split(",")) {
                int e = part.lastIndexOf('=');
                if (e <= 0) continue;
                try {
                    apps.put(part.substring(0, e), Integer.parseInt(part.substring(e + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
            out.put(day.substring(0, c), apps);
        }
        return out;
    }

    public static String encode(Map<String, Map<String, Integer>> days) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, Integer>> d : days.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(d.getKey()).append(':');
            boolean first = true;
            for (Map.Entry<String, Integer> a : d.getValue().entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(a.getKey()).append('=').append(a.getValue());
            }
        }
        return sb.toString();
    }

    /** Dodaje jednu notifikaciju i briše dane starije od {@link #KEEP_DAYS}. */
    public static void add(Map<String, Map<String, Integer>> days, String today, String pkg) {
        Map<String, Integer> apps = days.get(today);
        if (apps == null) {
            apps = new HashMap<>();
            days.put(today, apps);
        }
        Integer n = apps.get(pkg);
        apps.put(pkg, n == null ? 1 : n + 1);
        LocalDate t = date(today);
        if (t == null) return;
        String oldest = t.minusDays(KEEP_DAYS - 1).format(KEY);
        days.keySet().removeIf(k -> k.compareTo(oldest) < 0);
    }

    /** Zbir po danu, za {@link UnlockStreak#week}. */
    public static Map<String, Integer> perDay(Map<String, Map<String, Integer>> days) {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> d : days.entrySet()) {
            int sum = 0;
            for (Integer v : d.getValue().values()) sum += v;
            out.put(d.getKey(), sum);
        }
        return out;
    }

    /** Aplikacije po broju notifikacija u poslednjih {@code n} dana sa danas, od najviše. */
    public static List<Map.Entry<String, Integer>> topApps(Map<String, Map<String, Integer>> days, String today, int n) {
        Map<String, Integer> sum = new LinkedHashMap<>();
        LocalDate t = date(today);
        if (t != null) {
            for (int i = 0; i < n; i++) {
                Map<String, Integer> apps = days.get(t.minusDays(i).format(KEY));
                if (apps == null) continue;
                for (Map.Entry<String, Integer> a : apps.entrySet()) sum.merge(a.getKey(), a.getValue(), Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> out = new ArrayList<>(sum.entrySet());
        out.sort((a, b) -> b.getValue() != a.getValue().intValue() ? b.getValue() - a.getValue() : a.getKey().compareTo(b.getKey()));
        return out;
    }

    private static LocalDate date(String key) {
        try {
            return LocalDate.parse(key, KEY);
        } catch (Throwable t) {
            return null;
        }
    }
}
