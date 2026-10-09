# Čuvar: politika privatnosti

Poslednja izmena: 9. oktobar 2026.

Čuvar je aplikacija za samokontrolu vremena na telefonu. Napravljena je tako da nijedan podatak ne napušta telefon.

## Šta Čuvar vidi

Čuvar koristi Androidovu uslugu Pristupačnosti (AccessibilityService), i to samo uz tvoj izričit pristanak u aplikaciji. Preko nje vidi:

- koja je aplikacija trenutno otvorena,
- naziv sajta iz adresne trake podržanih pregledača.

Ovo služi samo da bi Čuvar merio vreme po aplikacijama i sajtovima i prikazao ekran za blokadu preko onoga što si sam izabrao da blokiraš. Čuvar ne čita poruke, lozinke ni drugi sadržaj ekrana, ne snima ekran i ne izvršava radnje umesto tebe, osim povratka na početni ekran ili korak nazad sa blokiranog sadržaja i pauziranja zvuka dok je ekran za blokadu prikazan.

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

**What it accesses.** With your explicit in-app consent, Čuvar uses Android's AccessibilityService to see which app is in the foreground and the site name in the address bar of supported browsers. This is used only to measure time per app and site and to show a block screen over content you chose to block. It does not read messages, passwords or other screen content, does not record the screen, and performs no actions on your behalf other than going Home or Back from blocked content and pausing media while the block screen is shown.

**What it stores.** Time per app and site for the last 14 days, your rules, and a random secret key used to compute the daily code. All of it stays in the app's private storage on your phone, is excluded from cloud backup and device transfer, and is deleted when you uninstall the app.

**What it does not do.** No internet permission, no data collection or sharing, no accounts, ads, analytics or third-party SDKs.

**Contact.** https://github.com/stefanciriic/cuvar/issues
