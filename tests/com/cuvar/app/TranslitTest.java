package com.cuvar.app;

/** Latinica u ćirilicu: srpske reči se menjaju, nazivi aplikacija i adrese ostaju. */
public final class TranslitTest {
    private static int checks;

    public static void main(String[] args) {
        eq("Ljubav i njiva, džep", "Љубав и њива, џеп");
        eq("LJUBAV Đorđe Ćuprija", "ЉУБАВ Ђорђе Ћуприја");
        eq("Noćna blokada do 06:00: sve je zaključano.", "Ноћна блокада до 06:00: све је закључано.");
        eq("Pozivi na WhatsApp i LinkedIn", "Позиви на WhatsApp и LinkedIn");
        eq("Blokiraj youtube.com i reddit.com.", "Блокирај youtube.com и reddit.com.");
        eq("Otvori Chrome ili Brave", "Отвори Chrome или Brave");
        eq("Verzija v183, limit 15min", "Верзија v183, лимит 15мин");
        eq("ZA CEO TELEFON", "ЗА ЦЕО ТЕЛЕФОН");
        Translit.keep("EON TV");
        eq("EON TV · ukloni", "EON TV · уклони");
        check(Translit.toLatin("линкедин.цом").equals("linkedin.com"), "Ćirilica u latinicu");
        check(Translit.toLatin("Љубав џеп").equals("Ljubav džep"), "Digrafi nazad");
        System.out.println("TranslitTest: " + checks + " provera prošlo");
    }

    private static void eq(String in, String want) {
        String got = Translit.toCyrillic(in);
        check(want.equals(got), in + " -> " + got);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
