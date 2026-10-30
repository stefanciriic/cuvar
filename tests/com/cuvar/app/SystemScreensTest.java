package com.cuvar.app;

/** Ekrani poziva i podešavanja, i pominjanje Čuvara na ekranu. */
public final class SystemScreensTest {
    private static int checks;

    public static void main(String[] args) {
        check(SystemScreens.callScreen("com.whatsapp.voipcalling.VoipActivityV2"), "WhatsApp poziv");
        check(SystemScreens.callScreen("com.viber.voip.phone.PhoneFragmentActivity"), "Viber poziv");
        check(SystemScreens.callScreen("org.thoughtcrime.securesms.WebRtcCallActivity"), "Signal poziv");
        check(!SystemScreens.callScreen("com.whatsapp.calling.CallLogActivity"), "Istorija poziva nije poziv");
        check(!SystemScreens.callScreen("com.whatsapp.calling.CallLinkActivity"), "Link za poziv nije poziv");
        check(!SystemScreens.callScreen("com.whatsapp.Conversation"), "Razgovor nije poziv");
        check(!SystemScreens.callScreen("com.whatsapp.contact.picker.ContactPicker"), "Izbor kontakta nije poziv");

        check(SystemScreens.settingsLike("com.android.settings"), "Podešavanja");
        check(SystemScreens.settingsLike("com.google.android.packageinstaller"), "Instalacija");
        check(SystemScreens.settingsLike("com.samsung.accessibility"), "Samsung Pristupačnost");
        check(SystemScreens.settingsLike("com.android.vending"), "Play prodavnica");
        check(!SystemScreens.settingsLike("com.instagram.android"), "Obična aplikacija");
        check(SystemScreens.installer("com.android.vending") && !SystemScreens.installer("com.android.settings"),
                "U Play prodavnici se zatvara samo brisanje");

        check(SystemScreens.namesGuard("Čuvar"), "Naziv latinicom");
        check(SystemScreens.namesGuard("ЧУВАР"), "Naziv ćirilicom");
        check(SystemScreens.namesGuard("Deinstaliraj Čuvar?"), "Pitanje za brisanje");
        check(!SystemScreens.namesGuard("Čuvar ekrana"), "Čuvar ekrana nije Čuvar");
        check(!SystemScreens.namesGuard("Чувар екрана"), "Чувар екрана није Чувар");
        check(SystemScreens.namesGuard("Čuvar ekrana, Čuvar"), "Oba na istom ekranu");
        System.out.println("SystemScreensTest: " + checks + " provera prošlo.");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
}
