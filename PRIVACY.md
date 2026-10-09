# Čuvar: politika privatnosti

Poslednja izmena: 9. oktobar 2026.

Čuvar je aplikacija za samokontrolu vremena na telefonu. Napravljena je tako da nijedan podatak ne napušta telefon.

## Šta Čuvar vidi

Čuvar koristi Androidovu uslugu Pristupačnosti (AccessibilityService), i to samo uz tvoj izričit pristanak u aplikaciji. Preko nje dobija podatke o aktivnom prozoru i koristi samo:

- koja je aplikacija trenutno otvorena,
- naziv sajta iz adresne trake podržanih pregledača.

Kod ugrađenih pregledača proverava ograničen broj tekstualnih čvorova pri vrhu ekrana da bi pronašao domen,
a na ekranima podešavanja traži samo naziv Čuvara radi zaštite od isključivanja.

Ovo služi samo da bi Čuvar merio vreme po aplikacijama i sajtovima i prikazao ekran za blokadu preko onoga što si sam izabrao da blokiraš. Čuvar ne čuva niti šalje poruke, lozinke ili sadržaj stranica, ne snima ekran i ne izvršava radnje umesto tebe, osim povratka na početni ekran ili korak nazad sa blokiranog sadržaja i pauziranja zvuka dok je ekran za blokadu prikazan.

## Šta Čuvar čuva

- Vreme provedeno po aplikacijama i sajtovima, za poslednjih 14 dana.
- Pravila koja si podesio (zaključavanja, limiti, režimi).
- Nasumičan tajni ključ iz kog se računa dnevna šifra.

Sve to je sačuvano samo u memoriji aplikacije na tvom telefonu. Ne ulazi u rezervnu kopiju ni u prenos na novi telefon i briše se kad obrišeš aplikaciju.

## Šta Čuvar ne radi

- Nema dozvolu za internet i ne šalje nikakve podatke.
- Nema naloge, reklame, analitiku ni biblioteke trećih strana.
- Ne deli podatke ni sa kim.

## Kontakt

Pitanja: otvori issue na https://github.com/stefanciriic/cuvar/issues

---

# Čuvar: privacy policy (English)

Last updated: October 9, 2026.

Čuvar is a self-control screen-time app. It is built so that no data leaves the phone.

**What it accesses.** With your explicit in-app consent, Čuvar uses Android's AccessibilityService to inspect the active window. It uses the foreground app, supported browser address bars, a limited set of text nodes near the top of embedded browsers, and the Čuvar label on settings screens when protection is enabled. This is used only to measure time per app and site and to show a block screen over content you chose to block. It does not store or send page, message, or password text, does not record the screen, and performs no actions on your behalf other than going Home or Back from blocked content and pausing media while the block screen is shown.

**What it stores.** Time per app and site for the last 14 days, your rules, and a random secret key used to compute the daily code. All of it stays in the app's private storage on your phone, is excluded from cloud backup and device transfer, and is deleted when you uninstall the app.

**What it does not do.** No internet permission, no data collection or sharing, no accounts, ads, analytics or third-party SDKs.

**Contact.** https://github.com/stefanciriic/cuvar/issues
