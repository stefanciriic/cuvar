package com.cuvar.app.guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uhvaćene greške, sačuvane na telefonu: mesto, vrsta, naša klasa i red, broj i poslednje vreme.
 * Nikad poruka greške, adresa, paket ni tekst sa ekrana. Čuva se najviše MAX različitih grešaka.
 */
public final class ErrorLog {
    public static final int MAX = 30;
    private static final String OURS = "com.cuvar.app.";

    public static final class Entry {
        public final String where;
        public final String type;
        public final String frame;
        public int count;
        public long last;

        Entry(String where, String type, String frame, int count, long last) {
            this.where = where;
            this.type = type;
            this.frame = frame;
            this.count = count;
            this.last = last;
        }

        String key() {
            return where + "\t" + type + "\t" + frame;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Čita sačuvan tekst; neispravni redovi se preskaču, a null ili prazno daje prazan dnevnik. */
    public static ErrorLog parse(String text) {
        ErrorLog log = new ErrorLog();
        if (text == null || text.isEmpty()) return log;
        for (String line : text.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length != 5 || p[0].isEmpty()) continue;
            try {
                int count = Integer.parseInt(p[3]);
                long last = Long.parseLong(p[4]);
                if (count <= 0) continue;
                Entry e = new Entry(p[0], p[1], p[2], count, last);
                log.entries.put(e.key(), e);
            } catch (NumberFormatException ignored) {
                // oštećen red se samo preskače
            }
        }
        log.trim();
        return log;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries.values()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.where).append('\t').append(e.type).append('\t').append(e.frame)
                    .append('\t').append(e.count).append('\t').append(e.last);
        }
        return sb.toString();
    }

    public void record(String where, Throwable error, long now) {
        record(where, error == null ? "?" : error.getClass().getSimpleName(), frame(error), now);
    }

    public void record(String where, String type, String frame, long now) {
        Entry probe = new Entry(clean(where), clean(type), clean(frame), 1, now);
        Entry old = entries.get(probe.key());
        if (old != null) {
            old.count = old.count == Integer.MAX_VALUE ? old.count : old.count + 1;
            old.last = now;
        } else {
            entries.put(probe.key(), probe);
            trim();
        }
    }

    /** Greške od najskorije. */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>(entries.values());
        Collections.sort(out, (a, b) -> Long.compare(b.last, a.last));
        return out;
    }

    public int total() {
        long sum = 0;
        for (Entry e : entries.values()) sum += e.count;
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    /** Prvo mesto u našem kodu gde je greška nastala ("Klasa:red"), gledajući i uzroke; "?" ako ga nema. */
    public static String frame(Throwable error) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 5; t = t.getCause(), depth++) {
            for (StackTraceElement s : t.getStackTrace()) {
                String c = s.getClassName();
                if (c == null || !c.startsWith(OURS)) continue;
                if (c.endsWith(".ErrorLog") || c.endsWith(".GuardDiagnostics")) continue;
                String simple = c.substring(c.lastIndexOf('.') + 1);
                int inner = simple.indexOf('$');
                if (inner > 0) simple = simple.substring(0, inner);
                return simple + ":" + s.getLineNumber();
            }
        }
        return "?";
    }

    /** Izbacuje najstarije dok ih ima više od MAX. */
    private void trim() {
        while (entries.size() > MAX) {
            Entry oldest = null;
            for (Entry e : entries.values()) if (oldest == null || e.last < oldest.last) oldest = e;
            entries.remove(oldest.key());
        }
    }

    private static String clean(String s) {
        if (s == null || s.isEmpty()) return "?";
        return s.replace('\t', ' ').replace('\n', ' ');
    }
}
