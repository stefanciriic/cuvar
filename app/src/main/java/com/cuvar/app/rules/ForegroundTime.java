package com.cuvar.app.rules;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Vreme po aplikaciji iz Androidovih događaja korišćenja, kako ga računa i Digital Wellbeing:
 * aplikacija se broji dok joj je bar jedan ekran (aktivnost) napred, a ekran telefona upaljen i otključan.
 * Tipovi događaja su iste vrednosti kao u UsageEvents.Event.
 */
public final class ForegroundTime {
    public static final int RESUMED = 1;
    public static final int PAUSED = 2;
    public static final int SCREEN_ON = 15;
    public static final int SCREEN_OFF = 16;
    public static final int KEYGUARD_SHOWN = 17;
    public static final int STOPPED = 23;
    public static final int SHUTDOWN = 26;

    public static final class Event {
        public final long time;
        public final int type;
        public final String pkg;
        public final String cls;

        public Event(long time, int type, String pkg, String cls) {
            this.time = time;
            this.type = type;
            this.pkg = pkg;
            this.cls = cls;
        }
    }

    private ForegroundTime() { }

    /**
     * Milisekunde po paketu unutar [from, to). Događaji idu po vremenu i mogu da počnu pre from,
     * da bi se znalo šta je bilo otvoreno na početku dana; ono što je otvoreno na kraju broji se do to.
     */
    public static Map<String, Long> perApp(List<Event> events, long from, long to) {
        Map<String, Long> out = new HashMap<>();
        // Ključ "paket/klasa" -> kad je ekran došao napred; paket se broji jednom i kad su mu dva ekrana napred.
        Map<String, Long> open = new HashMap<>();
        Map<String, Integer> openPerPkg = new HashMap<>();
        Map<String, Long> pkgSince = new HashMap<>();
        for (Event e : events) {
            if (e.time >= to) break;
            if (e.type == SCREEN_OFF || e.type == KEYGUARD_SHOWN || e.type == SHUTDOWN) {
                for (Map.Entry<String, Long> p : pkgSince.entrySet()) add(out, p.getKey(), p.getValue(), e.time, from);
                open.clear();
                openPerPkg.clear();
                pkgSince.clear();
                continue;
            }
            if (e.pkg == null) continue;
            String key = e.pkg + "/" + e.cls;
            if (e.type == RESUMED) {
                if (open.containsKey(key)) continue;
                open.put(key, e.time);
                int n = openPerPkg.getOrDefault(e.pkg, 0);
                openPerPkg.put(e.pkg, n + 1);
                if (n == 0) pkgSince.put(e.pkg, e.time);
            } else if (e.type == PAUSED || e.type == STOPPED) {
                if (open.remove(key) == null) continue;
                int n = openPerPkg.getOrDefault(e.pkg, 1) - 1;
                if (n > 0) {
                    openPerPkg.put(e.pkg, n);
                } else {
                    openPerPkg.remove(e.pkg);
                    Long since = pkgSince.remove(e.pkg);
                    if (since != null) add(out, e.pkg, since, e.time, from);
                }
            }
        }
        for (Iterator<Map.Entry<String, Long>> it = pkgSince.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> p = it.next();
            add(out, p.getKey(), p.getValue(), to, from);
        }
        return out;
    }

    private static void add(Map<String, Long> out, String pkg, long start, long end, long from) {
        long s = Math.max(start, from);
        if (end <= s) return;
        out.put(pkg, out.getOrDefault(pkg, 0L) + end - s);
    }
}
