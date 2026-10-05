package com.cuvar.app;

/** Pokretanje: javac -d .review/schedule app/src/main/java/com/cuvar/app/DailySchedule.java tests/com/cuvar/app/DailyScheduleTest.java
 *  java -cp .review/schedule com.cuvar.app.DailyScheduleTest */
public final class DailyScheduleTest {
    public static void main(String[] args) {
        for (int minute = 0; minute < 1440; minute++) {
            check(DailySchedule.contains(1260, 540, minute) == (minute >= 1260 || minute < 540), "Noćni period", minute);
            check(DailySchedule.contains(540, 1020, minute) == (minute >= 540 && minute < 1020), "Dnevni period", minute);
            check(!DailySchedule.contains(540, 540, minute), "Prazan period", minute);
            check(DailySchedule.contains(0, 1, minute) == (minute == 0), "Početak u ponoć", minute);
            check(DailySchedule.contains(1439, 0, minute) == (minute == 1439), "Kraj u ponoć", minute);
        }
        check("21:00".equals(DailySchedule.label(1260)), "Format početka", 1260);
        check("09:00".equals(DailySchedule.label(540)), "Format kraja", 540);
        System.out.println("Prošlo: 7200 provera perioda i 2 provere formata.");
    }

    private static void check(boolean result, String label, int minute) {
        if (!result) throw new AssertionError(label + ": " + minute);
    }
}
