# Čuvar

Čuvar je Android aplikacija za samokontrolu: pomaže da manje vremena provodiš na
telefonu. Za izabrane aplikacije i sajtove postavljaš pravila (zaključavanje,
dnevni limit, broj otvaranja, vremenski režimi), a Čuvar ih sprovodi tako što
preko zaključane aplikacije ili sajta prikaže ekran za blokadu.

Sve radi samo na telefonu: aplikacija nema dozvolu za internet, ništa ne šalje i
ne čita poruke ni sadržaj stranica. Detalji su u [PRIVACY.md](PRIVACY.md).

## Šta Čuvar radi

**Pravila za aplikacije i sajtove**

- Zaključavanje aplikacije ili sajta (otvara se samo dnevnom šifrom).
- Dnevni limit u minutima po aplikaciji ili sajtu. Kad se potroši, ostaje
  zaključano do ponoći, i dnevna šifra tada ne pomaže.
- Najviše otvaranja dnevno po aplikaciji.
- Najduže korišćenje u komadu, posle čega ide obavezna pauza od 15 minuta.
- Aplikacija i njen sajt (npr. Instagram i instagram.com) vode se kao jedna
  stavka sa zajedničkim vremenom i strožim pravilom.
- Upozorenje 5 i 1 minut pre isteka limita.
- Kratka pauza od 6 sekundi pre otvaranja aplikacije sa pravilom.

**Pravila za ceo telefon**

- Ukupni dnevni limit: zajedničko vreme svih aplikacija i sajtova sa pravilom.
- Noćna blokada od 22:00 do 06:00: sve što ima pravilo je zaključano, bez
  otključavanja. Pozivi i SMS rade.
- Vremenski režimi: izabrane aplikacije i sajtovi su blokirani u zadatom periodu
  i danima (npr. radno vreme). Režim može da traži dnevnu šifru van perioda.
- Fokus: ručno pokrenut period u kome rade samo izabrane aplikacije i sajtovi.
- "Pozivi uvek prolaze": glasovni i video pozivi u WhatsApp-u, Viberu,
  Telegramu i sličnim aplikacijama rade i kad je aplikacija zaključana.

**Otključavanje**

- Nema PIN-a. Jedini ključ je dnevna šifra od 4 cifre, koja se vidi i važi samo
  od 17:00 do 22:00. Šifra se računa na telefonu iz tajnog ključa, bez interneta.
- Pre šifre ide pitalica (kviz), koja posle grešaka traži 1, pa 2, pa 3 tačna
  odgovora zaredom.
- Otključavanje traje 5 minuta, a zatim sat vremena nema novog otključavanja.
  Jednom dnevno postoji hitno otključavanje od 10 minuta.

**Pooštravanje odmah, popuštanje sutra**

Strože pravilo važi odmah. Svako popuštanje (veći limit, brisanje pravila,
isključen režim ili noćna blokada) važi tek od sledećeg jutra u 06:00. Na
početnom ekranu kartica "Od sutra" pokazuje šta se menja i može da otkaže
odložene promene.

**Zaobilaženja koja Čuvar zatvara**

- Pomeranje sata i vremenske zone ne skraćuje režim i ne donosi novu šifru.
- Kopije aplikacija (Parallel Space i slične) potpadaju pod pravila originala,
  a aplikacije za kloniranje su zaključane dok postoje pravila za aplikacije.
- Pregledač u kome Čuvar ne vidi adresu zaključan je dok postoje pravila za
  sajtove. Isto važi za pregledač unutar aplikacija (link iz Instagrama).
- Prevodilac, AMP i "ogledala" sajtova (npr. yewtu.be) računaju se kao pravi sajt.
- Podeljeni ekran i plutajući prozor se takođe proveravaju.
- Zaštita od isključivanja (opciono): zatvara ekrane podešavanja na kojima bi se
  Čuvar mogao isključiti ili obrisati.
- Zapis "Čuvar nije radio": kad je servis bio ugašen ili je telefon paljen u
  Safe Mode-u.

**Statistika**

Ekrani Danas, Pravila i Statistika. Statistika ima poređenje sa ranijim
danima, krug po aplikacijama, kolone za poslednjih 7 dana i niz dana bez
otključavanja.

**Jezik i pismo**

Podešavanja → Jezik: srpski latinica, srpski ćirilica ili engleski.

## Kako da instaliraš

1. Na stranici [Releases](https://github.com/stefanciriic/cuvar/releases)
   preuzmi `cuvar.apk` iz najnovijeg izdanja i instaliraj ga.
2. Otvori Čuvara i uključi ga u Podešavanja → Pristupačnost.
3. Preporučeno: dozvoli "Pristup korišćenju" (Usage access). Tada Čuvar prati
   samo aplikacije sa pravilom i pregledače, pa manje troši bateriju.
4. Isključi optimizaciju baterije za Čuvara, da ga telefon ne gasi.

Nova verzija se instalira preko stare, bez gubitka podešavanja.

## Kako radi (ukratko)

Srce aplikacije je `GuardService`, Android servis za Pristupačnost
(AccessibilityService). On:

1. sazna koja je aplikacija napred (od Androida preko "Pristupa korišćenju",
   ili iz događaja Pristupačnosti),
2. u pregledaču pročita samo adresnu traku (po ID-u polja, nikad stranicu),
3. meri vreme po aplikaciji i sajtu,
4. proveri sva pravila koja sada važe, od najstrožeg,
5. po potrebi preko svega prikaže ekran za blokadu (`TYPE_ACCESSIBILITY_OVERLAY`).

Sva podešavanja i izmereno vreme čuva `Store` u SharedPreferences, kao JSON.
`Store` drži dve verzije pravila: ono što si podesio i ono što sada zaista važi.
Kad nešto izmeniš, važeća verzija postaje strožija od stare i nove; u 06:00
važeća postaje jednaka podešenoj. Tako popuštanje stiže tek sutra.

Vreme se meri "pouzdanim satom": unutar jednog paljenja telefona meri se od
paljenja (ne može se pomeriti), a ručno pomeranje sata se pamti i oduzima.

## Struktura koda

Ceo kod je obična Java, bez dodatnih biblioteka, u
`app/src/main/java/com/cuvar/app/`. Ekrani se prave iz koda, bez XML rasporeda.

| Fajl | Šta radi |
|------|----------|
| `GuardService.java` | Servis za Pristupačnost: šta je napred, merenje, odluka o blokadi, zaštita od isključivanja |
| `Store.java` | Sva podešavanja, izmereno vreme, otključavanja, pouzdan sat, "popuštanje od sutra" |
| `MainActivity.java` | Početni ekran sa karticama Danas / Pravila / Statistika |
| `SettingsSheets.java` | Prozori podešavanja (tema, jezik, privatnost) i pravila za ceo telefon (dnevni limit, noć, zaštita) |
| `RulesActivity.java` | "Aplikacije i sajtovi": lista pravila i prozor za izmenu jedne stavke |
| `ScheduleActivity.java` | Vremenski režimi |
| `FocusActivity.java` | Ručni fokus |
| `StatsActivity.java`, `Charts.java` | Statistika i grafikoni |
| `BlockOverlayContent.java` | Ekran za blokadu i tok otključavanja (pitalica, šifra, hitno) |
| `PauseOverlayContent.java` | Pauza od 6 sekundi pre otvaranja |
| `GuardOverlayWindow.java` | Prozor preko svega i pauziranje muzike dok je blokada prikazana |
| `BrowserMonitor.java`, `BrowserAddressState.java`, `WebAddress.java` | Čitanje adrese u pregledačima, prepoznavanje pravog sajta |
| `ForegroundApp.java` | Koja je aplikacija napred, iz Androidovih zapisa o korišćenju |
| `UsageTracker.java`, `UsageCalendar.java` | Merenje vremena i podela po danima |
| `DailySchedule.java` | Vremenski režimi (periodi, dani, pooštravanje) |
| `DailyCode.java` | Dnevna šifra (HMAC iz tajnog ključa i dana) |
| `DayLimit.java` | Ukupni dnevni limit |
| `DomainRules.java`, `Links.java` | Domeni i poddomeni, veza aplikacija ↔ sajt |
| `Quiz.java`, `QuizBank.java`, `Jokes.java` | Pitalice i šaljive poruke |
| `UnlockStreak.java` | Niz dana bez otključavanja |
| `Lang.java`, `Translit.java`, `app/src/main/assets/en.tsv` | Engleski prevod i ćirilica |
| `Ui.java`, `Sheet.java`, `CheckRow.java`, `PinPad.java`, `SubActivity.java` | Zajednički delovi interfejsa |
| `GuardDiagnostics.java` | Lokalni zapis grešaka (bez adresa i sadržaja) |

Delovi logike su izdvojeni u male klase bez Android-a, da bi mogli da se testiraju
na računaru:

| Fajl | Šta radi |
|------|----------|
| `BlockKind.java` | Vrste blokade, koje se ne otključavaju, koja se prikazuje |
| `UnlockTimer.java` | 5 minuta posle otključavanja, pa sat vremena pauze |
| `CodeAttempts.java` | Pogrešne šifre i čekanje od 30 s |
| `TrustedClock.java` | Pravila pouzdanog sata (pomeranje sata, restart) |
| `SessionLimit.java` | Najduže u komadu i pauza od 15 minuta |
| `RuleMerge.java` | Spajanje današnjeg i novog pravila (strožije ostaje) |
| `LimitWarnings.java` | Upozorenja 5 i 1 minut pre limita |
| `SystemScreens.java` | Ekrani poziva i podešavanja, pominjanje Čuvara |
| `Clones.java` | Kopije aplikacija i aplikacije za kloniranje |
| `Hosts.java` | Domen iz adrese i glavni domen |

## Prevod

Tekst se u kodu piše na srpskom latinicom. Prikaz ga prevodi:

- na ćirilicu automatski (`Translit`), osim naziva brendova, domena i sl.,
- na engleski iz `app/src/main/assets/en.tsv`: jedan red po tekstu ili delu
  rečenice, `srpski<TAB>engleski`. Svaki novi tekst u aplikaciji traži novi red
  ovde. `LangTest` ispisuje tekstove kojima nedostaje prevod.

## Build i izdanja

Svaki push na granu `main` pokreće GitHub Actions
(`.github/workflows/apk.yml`), koji:

1. pokrene automatske testove (`tests/`),
2. napravi APK,
3. potpiše ga (ključevi su u GitHub Secrets, vidi [SIGNING.md](SIGNING.md)),
4. objavi novo izdanje `v<broj>` sa `cuvar.apk`.

Izmena samo `.md` fajlova ne pokreće build.

Lokalno, iz Android Studija ili komandne linije:

```
gradlew assembleDebug
```

Debug verzija ima paket `com.cuvar.app.debug` i može se instalirati pored prave.

## Testovi

Automatski testovi su obična Java (bez Android SDK-a) i proveravaju logiku:
režime, dnevni limit, šifru, pitalice, domene, merenje vremena, prevod.

```
powershell ./tests/run.ps1
```

Spisak klasa koje testovi koriste je u `tests/sources.txt`. Scenariji za ručnu
proveru na telefonu su u [tests/README.md](tests/README.md).

## Poznata ograničenja

- Bez zaključanih Podešavanja telefona Čuvar se može isključiti u Pristupačnosti
  ili obrisati. Zaštita od isključivanja to otežava, ali ne sprečava potpuno
  (npr. Safe Mode).
- Adresa se čita samo u poznatim pregledačima (Chrome, Brave, Edge, Vivaldi,
  Samsung Internet, Firefox, Kiwi; Opera, Opera Mini, DuckDuckGo i Firefox Focus
  još nisu probani na telefonu). Ostali pregledači su zaključani dok postoje
  pravila za sajtove.
- Pravila važe za ceo sajt, ne za deo sajta (npr. samo youtube.com/shorts).
- Dok traje poziv u aplikaciji sa "Pozivi uvek prolaze", cela ta aplikacija je
  otvorena.
- Blokiranje sajtova preko DNS/VPN-a je uklonjeno 10.10.2026, jer je
  presecalo internet.
