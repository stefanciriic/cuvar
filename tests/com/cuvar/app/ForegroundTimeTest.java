package com.cuvar.app;

import com.cuvar.app.rules.ForegroundTime;
import com.cuvar.app.rules.ForegroundTime.Event;

import java.util.Arrays;
import java.util.Map;

public final class ForegroundTimeTest {
    private static int checks;

    public static void main(String[] args) {
        Map<String, Long> m = ForegroundTime.perApp(Arrays.asList(
                new Event(50, ForegroundTime.RESUMED, "w", "Main"),
                new Event(200, ForegroundTime.PAUSED, "w", "Main"),
                new Event(200, ForegroundTime.RESUMED, "w", "Call"),
                new Event(500, ForegroundTime.STOPPED, "w", "Call"),
                new Event(600, ForegroundTime.RESUMED, "c", "A")), 100, 1000);
        check(m.get("w") == 400, "poziv u WhatsApp-u se broji, pre početka dana se seče: " + m.get("w"));
        check(m.get("c") == 400, "otvorena aplikacija se broji do sada");

        m = ForegroundTime.perApp(Arrays.asList(
                new Event(0, ForegroundTime.RESUMED, "a", "X"),
                new Event(10, ForegroundTime.RESUMED, "a", "Y"),
                new Event(20, ForegroundTime.PAUSED, "a", "X"),
                new Event(30, ForegroundTime.PAUSED, "a", "Y"),
                new Event(30, ForegroundTime.PAUSED, "a", "Y")), 0, 100);
        check(m.get("a") == 30, "dva ekrana iste aplikacije se ne broje duplo");

        m = ForegroundTime.perApp(Arrays.asList(
                new Event(0, ForegroundTime.RESUMED, "a", "X"),
                new Event(40, ForegroundTime.SCREEN_OFF, null, null),
                new Event(90, ForegroundTime.PAUSED, "a", "X")), 0, 100);
        check(m.get("a") == 40, "ugašen ekran prekida brojanje");

        m = ForegroundTime.perApp(Arrays.asList(
                new Event(0, ForegroundTime.PAUSED, "a", "X"),
                new Event(150, ForegroundTime.RESUMED, "b", "X")), 0, 100);
        check(m.isEmpty(), "pauza bez početka i događaj posle kraja se ne broje");
        System.out.println("Prošlo: " + checks + " provera ForegroundTime.");
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
