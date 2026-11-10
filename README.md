# Čuvar

Čuvar je Android aplikacija za samokontrolu: pomaže da manje vremena provodiš na
telefonu. Za izabrane aplikacije i sajtove postavljaš pravila (zaključavanje,
dnevni limit, broj otvaranja, vremenski režimi), a Čuvar ih sprovodi tako što
preko zaključane aplikacije ili sajta prikaže ekran za blokadu.

Sve radi samo na telefonu: aplikacija nema dozvolu za internet, ništa ne šalje i
ne čita poruke ni sadržaj stranica. Detalji su u [PRIVACY.md](PRIVACY.md).

## Šta Čuvar radi (sva pravila)

Ovo je izvor istine za pravila; kad se pravilo u kodu promeni, menja se i ovde.
Stanje: 11. 11. 2026. Većina pravila nije proverena na pravom telefonu.

### 1. Šta je "pod pravilom"

Aplikacija je pod pravilom ako ima bar jedno od: zaključavanje, dnevni limit,
najviše otvaranja, najduže u komadu, ili je u **uključenom** vremenskom režimu
(bez obzira da li režim sada traje). Sajt je pod pravilom ako je na listi sajtova
ili u uključenom režimu (pokriva i poddomene).

To je važno jer se tri pravila za ceo telefon odnose na sve što je pod pravilom,
**i van perioda režima**:

- noćna blokada (22:00–06:00),
- ukupni dnevni limit (to vreme se sabira),
- kratka pauza od 6 s pre svakog otvaranja.

Primer: WhatsApp koji je samo u režimu 09–12 noću je zaključan. Da noću radi,
uključi mu "Bez noćne blokade" (važi od sledećih 06:00).

Nikad se ne blokiraju: telefon (dialer), SMS aplikacija, početni ekran i sam Čuvar.

### 2. Pravila za aplikaciju i sajt

| Pravilo | Kad blokira | Otključava se? |
|---|---|---|
| Zaključavanje | uvek | da, dnevnom šifrom (17–22) |
| Sajt "uvek blokiran" (limit 0) | uvek | da, dnevnom šifrom (17–22) |
| Dnevni limit (min) | kad se potroši, do ponoći | **ne** |
| Najviše otvaranja dnevno | posle poslednjeg dozvoljenog otvaranja, do ponoći | **ne** |
| Najduže u komadu | posle toliko minuta, obavezna pauza 15 min | **ne** |
| Vremenski režim | u periodu i izabranim danima | **ne** |
| Režim sa "van perioda traži dnevnu šifru" | sve vreme **van** perioda | da, dnevnom šifrom |

- Izlazak iz aplikacije kraći od 15 min ne prekida "komad".
- Upozorenje stiže 5 i 1 min pre isteka limita.
- Aplikacija i njen sajt (Instagram i instagram.com) su jedna stavka: zajedničko
  vreme, važi strožije zaključavanje i limit. Broj otvaranja i "najduže u komadu"
  važe samo za aplikaciju, ne i za njen sajt.
- "Van režima bez dnevne šifre" (po aplikaciji): aplikacija iz režima sa šifrom
  je van perioda slobodna. Važi od sledećih 06:00.
- "Pozivi uvek prolaze" (po aplikaciji, podrazumevano WhatsApp, Viber, Telegram,
  Signal, Messenger): ekran poziva radi i noću i preko limita; poruke ostaju
  zaključane. Dok traje bilo koji internet poziv, sve ovakve aplikacije prolaze.

### 3. Pravila za ceo telefon

- **Noćna blokada 22:00–06:00** (podrazumevano uključena): sve pod pravilom je
  zaključano, ne otključava se ničim. Izuzetak po aplikaciji: "Bez noćne
  blokade". Pregledač bez noćne blokade i dalje ne otvara sajtove iz pravila.
- **Ukupni dnevni limit**: zbir vremena svega što je pod pravilom. Kad se potroši,
  sve pod pravilom je zaključano do ponoći, bez otključavanja. Može da se poveća
  jednom dnevno i samo dok nije potrošen (do 1 h za 30 %, 2 h za 20 %, od 4 h za
  10 %); povećanje ostaje i sledećih dana. Smanjenje važi odmah, isključivanje od
  sutra.
- **Fokus**: ručno pokrenut period u kome rade samo izabrane aplikacije i sajtovi
  (i telefon/SMS). Ne otključava se šifrom, ali može da se zaustavi u Čuvaru.
- **Zaštita od isključivanja** (opciono): zatvara ekrane podešavanja na kojima bi
  se Čuvar isključio u Pristupačnosti, zaustavio ili obrisao. Nikad ne zatvara
  ekrane za mrežu, VPN ni bateriju.

### 4. Redosled (šta se prikazuje kad važi više pravila)

Ukupni limit → noć → fokus → otvaranja → pauza u komadu → režim → dnevni limit
aplikacije / zaključavanje / šifra van perioda → pregledač bez adrese i
aplikacije za kloniranje → pravila sajta. Prikazuje se prva blokada koja važi;
otključavanje jedne stavke ne otvara druge.

### 5. Otključavanje

- Jedini ključ je **dnevna šifra** (4 cifre). Vidi se i prima samo **17:00–22:00**.
  Van tog vremena ekran blokade nema ni igricu ni unos šifre.
- Pre šifre ide izazov (Podešavanja → "Pre otključavanja"): pitalice, igrica
  (brojevi redom, strelice, boje, različit znak, pamćenje, niz) ili nasumično.
- Cena raste sa brojem otključavanja tog dana: 1. lakše, 2. teže, 3. najteže.
  **Najviše 3 otključavanja dnevno**, posle toga ništa do sutra.
- Otključavanje važi **5 min** za tu stavku, zatim **60 min** nema otključavanja
  ničega (pauza počinje odmah za druge stavke).
- 5 pogrešnih šifri zaredom: 30 s čekanja.
- **Hitno otključavanje**: jednom dnevno, 10 min, preskače pauzu od 60 min, ali
  takođe samo 17–22 i uz šifru i najtežu igricu.
- Tvrde blokade (noć, ukupni limit, potrošen limit, otvaranja, pauza u komadu,
  režim, fokus) se ne otključavaju ni šifrom ni hitno.

### 6. Pooštravanje odmah, popuštanje u 06:00

Strožije pravilo važi odmah. Svako popuštanje (veći limit, skinuto zaključavanje,
isključen ili skraćen režim, aplikacija izbačena iz režima, "Bez noćne blokade",
isključena noćna blokada ili zaštita) važi tek od **sledećih 06:00**. Kartica
"Zakazane promene" pokazuje šta čeka i može da se otkaže.

Dok režim traje, ne može da se isključi, obriše, skrati, promene mu se dani ili
šifra, ni da se iz njega izbaci aplikacija ili sajt. Dodavanje može.

### 7. "Ukloni iz pravila"

1. Klik samo snima zahtev. Ništa se ne skida, pravilo i dalje važi.
2. Sledećeg dana (dan se menja u 06:00) **od 08:00 do 22:00** Čuvar pita
   "Da li si siguran?". Pitanje iskače kad otvoriš Čuvar (nema obaveštenja),
   a stoji i kao kartica na kartici Pravila.
3. "Da, ukloni": stavka se odmah skida iz svih pravila i režima.
   "Ne, ostavi" ili bez odgovora do 22:00: zahtev propada, pravilo ostaje.

Zahtev dat između ponoći i 06:00 pita isti kalendarski dan od 08:00
(npr. dat u 00:30 → pita u 08:00 tog jutra).

### 8. Dani i satovi

- Izmereno vreme, limiti, broj otvaranja, hitno otključavanje i broj
  otključavanja kreću iz početka u **ponoć**.
- Zakazana popuštanja i zahtevi za uklanjanje računaju dan od **06:00**.
- Dnevna šifra se menja u **17:00**.
- Sve se računa po "pouzdanom satu": pomeranje sata ili zone ne skraćuje režim,
  ne vraća limite i ne donosi novu šifru.

### 9. Zaobilaženja koja Čuvar zatvara

- Kopije aplikacija (Parallel Space i slične) potpadaju pod pravila originala,
  a aplikacije za kloniranje su zaključane dok postoje pravila za aplikacije.
- Pregledač u kome Čuvar ne vidi adresu (i pregledač unutar Instagrama i sl.)
  zaključan je dok postoje pravila za sajtove; dok je neki sajt tvrdo blokiran,
  ni on se ne otključava.
- Prevodilac, AMP i "ogledala" sajtova (npr. yewtu.be) računaju se kao pravi sajt.
- Podeljeni ekran i plutajući prozor se takođe proveravaju.
- Zapis "Čuvar nije radio": kad je servis bio ugašen ili je telefon paljen u
  Safe Mode-u.

### 10. Otvorena pitanja (predlozi, još nisu odlučeni)

1. Aplikacija koja je samo u režimu je noću zaključana, troši ukupni limit i
   dobija pauzu od 6 s i van režima (vidi 1.). Da li to treba da važi samo za
   stavke sa sopstvenim pravilom?
2. Gotov režim "Noćno zaključavanje" (22:30–06:00) ne radi ništa dok je noćna
   blokada uključena, jer ona već pokriva 22:00–06:00. Predlog: dugme za njega
   prikazati samo kad je noćna blokada isključena.
3. Povećanje ukupnog limita ostaje zauvek, pa limit može da raste iz dana u dan.
   Predlog: povećanje samo za danas.
4. "Hitno otključavanje" radi samo 17–22 i uz šifru, pa nije hitno: samo
   preskače pauzu od 60 min. Predlog: preimenovati u "Otključaj bez čekanja"
   ili ukloniti.
5. Dok traje jedan internet poziv, prolaze sve aplikacije sa "Pozivi uvek
   prolaze", ne samo ona koja zove.
6. Broj otvaranja i "najduže u komadu" ne važe za sajt iste aplikacije u
   pregledaču.

### Statistika i jezik

Ekrani Danas, Pravila i Statistika. Statistika ima poređenje sa ranijim
danima, krug po aplikacijama, kolone za poslednjih 7 dana i niz dana bez
otključavanja. Podešavanja → Jezik: srpski latinica, srpski ćirilica ili engleski.

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
`app/src/main/java/com/cuvar/app/`, podeljen u pakete po ulozi. Ekrani se prave
iz koda, bez XML rasporeda. Zašto baš ovako i gde ide nova klasa:
[ARCHITECTURE.md](ARCHITECTURE.md).

| Paket / fajl | Šta radi |
|------|----------|
| `GuardService.java` | Servis za Pristupačnost: šta je napred, merenje, odluka o blokadi, zaštita od isključivanja |
| `CuvarApp.java` | Pokretanje procesa: dnevnik grešaka i beleženje padova (vidi „Opterećenje telefona“) |
| `MainActivity.java` | Početni ekran sa karticama Danas / Pravila / Statistika |
| `RulesActivity.java` | "Aplikacije i sajtovi": lista pravila i prozor za izmenu jedne stavke |
| `ScheduleActivity.java`, `FocusActivity.java` | Vremenski režimi, ručni fokus |
| `StatsActivity.java` | Statistika |
| `data/` | `Store` (sva podešavanja, "popuštanje od sutra") i njegovi delovi: `Clock` (pouzdan sat), `Unlocks`, `Modes` (režimi), `DayLimits`, `UsageLog`, `Sessions`, `Focus`, `Removals` (uklanjanje iz pravila uz potvrdu sutradan), `GuardLog`, `AppLabels` (naziv aplikacije) |
| `rules/` | Čista pravila bez Android-a, sa testovima: `BlockKind` (vrste blokade), `UnlockTimer`, `UnlockCost`, `CodeAttempts`, `TrustedClock`, `SessionLimit`, `RuleMerge`, `LimitWarnings`, `DailySchedule` (režimi), `DailyCode` (dnevna šifra), `DayLimit`, `DomainRules`, `UsageTracker`, `UsageCalendar`, `UnlockStreak`, `ForegroundTime` |
| `guard/` | Delovi servisa: `BlockScreen` (prikaz blokade), `AppKinds` (vrsta aplikacije napred), `UsageAccounting` (upis vremena), `BlockOverlayContent` (ekran blokade i otključavanje), `PauseOverlayContent` (pauza 6 s), `GuardOverlayWindow` (prozor preko svega, muzika), `AndroidUsage` (statistika iz Androidovih zapisa), `ForegroundApp`, `SystemScreens`, `Clones`, `MusicOwner`, `LoadMeter`, `GuardDiagnostics` i `ErrorLog` (uhvaćene greške, bez ličnih podataka) |
| `web/` | Pregledači: `BrowserMonitor`, `BrowserWindows`, `BrowserAddressState`, `WebAddress` (pravi sajt), `Hosts`, `Links` (aplikacija ↔ sajt) |
| `unlock/` | `Quiz`, `QuizBank`, `Jokes`, `NumberGame`, `NumberGameView`, `PinPad` |
| `ui/` | `Ui`, `Sheet`, `CheckRow`, `AppPicker` (spisak aplikacija sa pretragom), `Charts`, `SettingsSheets` (prozori podešavanja) |
| `i18n/` | `Lang`, `Translit`, uz `app/src/main/assets/en.tsv` |

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
