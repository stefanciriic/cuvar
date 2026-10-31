package com.cuvar.app;

import com.cuvar.app.guard.ErrorLog;

import java.util.List;

/** Dnevnik grešaka: brojanje, čuvanje, oštećen tekst, ograničenje broja i mesto u našem kodu. */
public final class ErrorLogTest {
    private static int checks;

    public static void main(String[] args) {
        ErrorLog log = ErrorLog.parse(null);
        check(log.isEmpty() && log.total() == 0, "null daje prazan dnevnik");
        check(ErrorLog.parse("").isEmpty(), "Prazan tekst daje prazan dnevnik");

        log.record("tick", "IllegalStateException", "GuardService:150", 1000);
        log.record("tick", "IllegalStateException", "GuardService:150", 2000);
        log.record("event", "NullPointerException", "AppKinds:40", 1500);
        check(log.entries().size() == 2, "Ista greška se broji, ne ponavlja");
        check(log.total() == 3, "Ukupno tri");
        List<ErrorLog.Entry> e = log.entries();
        check(e.get(0).where.equals("tick") && e.get(0).count == 2 && e.get(0).last == 2000, "Najskorija prva, sa brojem");

        ErrorLog back = ErrorLog.parse(log.serialize());
        check(back.serialize().equals(log.serialize()), "Čuvanje i čitanje daju isto");
        check(back.total() == 3, "Broj preživi čuvanje");

        ErrorLog bad = ErrorLog.parse("a\tb\tc\t2\t5\nsmeće\nx\ty\tz\tnije broj\t1\n\t\t\t1\t1\nq\tw\te\t0\t9\nok\tT\tF:1\t1\t7");
        check(bad.entries().size() == 2 && bad.total() == 3, "Oštećeni redovi se preskaču");

        ErrorLog tabs = new ErrorLog();
        tabs.record("a\tb", "x\ny", null, 1);
        ErrorLog tabsBack = ErrorLog.parse(tabs.serialize());
        check(tabsBack.entries().size() == 1 && tabsBack.entries().get(0).frame.equals("?"), "Tab i novi red ne kvare zapis");

        ErrorLog many = new ErrorLog();
        for (int i = 0; i < ErrorLog.MAX + 5; i++) many.record("op" + i, "E", "F:1", i);
        check(many.entries().size() == ErrorLog.MAX, "Najviše MAX grešaka");
        check(!many.serialize().contains("op0\t") && many.serialize().contains("op34\t"), "Izbacuju se najstarije");

        Throwable t = new IllegalStateException("tajna poruka");
        t.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("android.view.View", "draw", "View.java", 10),
                new StackTraceElement("com.cuvar.app.guard.GuardDiagnostics", "report", "GuardDiagnostics.java", 5),
                new StackTraceElement("com.cuvar.app.GuardService$1", "run", "GuardService.java", 153),
        });
        check(ErrorLog.frame(t).equals("GuardService:153"), "Mesto je prvi red iz našeg koda, bez unutrašnje klase");
        Throwable outer = new RuntimeException(t);
        outer.setStackTrace(new StackTraceElement[]{new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1)});
        check(ErrorLog.frame(outer).equals("GuardService:153"), "Mesto se traži i u uzroku");
        check(ErrorLog.frame(null).equals("?"), "Bez greške je mesto nepoznato");

        ErrorLog priv = new ErrorLog();
        priv.record("check", t, 5);
        check(!priv.serialize().contains("tajna"), "Poruka greške se ne čuva");
        check(priv.entries().get(0).type.equals("IllegalStateException"), "Čuva se vrsta greške");

        log.clear();
        check(log.isEmpty() && log.serialize().isEmpty(), "Brisanje");
        System.out.println("ErrorLogTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
