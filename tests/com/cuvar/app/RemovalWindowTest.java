package com.cuvar.app;

import com.cuvar.app.rules.RemovalWindow;

import java.time.LocalDateTime;

/** Uklanjanje iz pravila: potvrda tek sutradan od 08:00 do 22:00, posle toga zahtev propada. */
public final class RemovalWindowTest {
    private static int checks;

    public static void main(String[] args) {
        String day = "20261106";
        check(st(day, 2026, 11, 6, 23, 30) == RemovalWindow.WAITING, "Isto veče čeka");
        check(st(day, 2026, 11, 7, 1, 0) == RemovalWindow.WAITING, "Posle ponoći čeka");
        check(st(day, 2026, 11, 7, 7, 59) == RemovalWindow.WAITING, "Pre 08:00 čeka");
        check(st(day, 2026, 11, 7, 8, 0) == RemovalWindow.ASK, "U 08:00 pita");
        check(st(day, 2026, 11, 7, 21, 59) == RemovalWindow.ASK, "Do 22:00 pita");
        check(st(day, 2026, 11, 7, 22, 0) == RemovalWindow.EXPIRED, "U 22:00 propada");
        check(st(day, 2026, 11, 8, 7, 0) == RemovalWindow.EXPIRED, "Sledeće jutro je propao");
        check(st(day, 2026, 11, 5, 12, 0) == RemovalWindow.WAITING, "Sat vraćen unazad: i dalje čeka");
        // Kraj meseca i godine.
        check(st("20261231", 2027, 1, 1, 9, 0) == RemovalWindow.ASK, "Prelaz godine");
        check(st("xx", 2026, 11, 7, 9, 0) == RemovalWindow.EXPIRED, "Oštećen dan propada");
        System.out.println("RemovalWindowTest: " + checks + " provera prošlo.");
    }

    private static int st(String day, int y, int m, int d, int h, int min) {
        return RemovalWindow.state(day, LocalDateTime.of(y, m, d, h, min));
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
