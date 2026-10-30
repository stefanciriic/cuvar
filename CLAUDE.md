# Čuvar — uputstvo za Claude

Android aplikacija (Java) koja korisniku ograničava korišćenje telefona: pravila po
aplikaciji i sajtu, dnevni limiti, noćni blok 22–06, dnevna šifra 17–22 kao jedino
otključavanje, pitalice pri otključavanju. Glavna logika je u `GuardService`
(`app/src/main/java/com/cuvar/app/GuardService.java`) — Accessibility servis koji
prati koja je aplikacija/sajt otvoren, meri vreme i prikazuje blokadu preko svega.
Pregled funkcija i mapa fajlova: [README.md](README.md).

## Korisnik

- Vlasnik je Stefan. Ne programira: piši mu na srpskom, jednostavno, bez žargona.
- Ne traži od njega da kuca git ili druge komande; uradi to sam.
- Želi što stroža pravila za sebe ("što više ograničenja"), ali nikad po cenu da
  ostane bez telefona ili interneta.

## Pravila rada

- Pre izmena uradi `git pull`. Na repou rade i Claude sesije iz oblaka, pa
  uradi `git pull --rebase` i neposredno pre push-a.
- Kad je zadatak završen, commituj i pushuj direktno na `main` — bez pravljenja
  pull request-a. Commit poruke pišu se na engleskom, kratko, šta se promenilo.
- Build radi GitHub Actions (`.github/workflows/apk.yml`) na svaki push na `main`
  i pravi novu verziju aplikacije (APK + release sa tagom `v<run_number+100>`).
  Svaki push je novo izdanje, zato ne pushuj polovične izmene. Push u kome su
  promenjeni samo `.md` fajlovi ne pokreće build.
- Posle push-a proveri da je build prošao (GitHub Actions); ako padne, popravi.
- Lokalni `javac` prihvata skrivene (hidden/internal) Android API-je koje CI
  (Gradle sa SDK stub-ovima) odbija. Pre nego što prijaviš da je nešto gotovo,
  proveri da build prolazi onako kako ga pravi CI (`gradlew assembleRelease` ili
  bar `assembleDebug`), ne samo lokalnim `javac` testom. Za novije sistemske
  intent-e koristi string (npr. `"android.settings.ACCESSIBILITY_DETAILS_SETTINGS"`)
  umesto konstante koja možda nije javna.
- Pokreni testove: `powershell ./tests/run.ps1` (isto radi CI pre build-a).
- Ne briši i ne menjaj `app/cuvar.keystore` ni podešavanje potpisivanja u CI bez
  pitanja: pogrešan ključ znači da nova verzija ne može da se instalira preko stare.
- Kad kažeš da je nešto gotovo, reci i da li je provereno na telefonu. Većina
  novijih funkcija nije probana na pravom uređaju.

## Bezbednost korisnika (najvažnije)

- Nikad ne pravi izmenu koja može korisniku da preseče internet ili da zaključa
  telefon bez izlaza — uvek mora postojati način da se izađe/otključa.
- Telefon (dialer), SMS i početni ekran se nikad ne blokiraju (`exemptApps`).
  Hitni pozivi moraju uvek da rade.
- Blokiranje sajtova preko lokalnog VPN/DNS-a je uklonjeno posle incidenta
  10.10.2026 (presekao je internet, a zaštita je zatvarala ekran za gašenje VPN-a).
  Ne vraćaj ga bez izričitog dogovora i provere na telefonu.
- Zaštita od isključivanja (`guardsSelf`) zatvara sistemske ekrane. Ne proširuj je
  na ekrane koji su potrebni za izlaz iz problema (VPN, mreža, baterija...).

## Pravila aplikacije koja treba poštovati u kodu

- Pooštravanje važi odmah, popuštanje tek od sledećeg jutra u 06:00. `Store` drži
  podešeno stanje (`apps`, `sites`, `schedules`) i stanje koje sada važi (`eApps`,
  `eSites`, `eSchedules`, `eNight`, `eProtect`); servis čita samo `...Now()` metode.
  Svako novo podešavanje koje može da olakša korisniku mora da prođe kroz isti
  mehanizam (`enforce()`, `pendingChanges()`, `cancelPending()`).
- Potrošen limit (dnevni, aplikacije, sajta), noćna blokada, režim, fokus,
  broj otvaranja i pauza su "tvrde" blokade (`GuardService.hard`): ne otključavaju
  se ni šifrom.
- Vreme uvek uzimaj iz `store.now()` / `store.day()` (pouzdan sat), ne iz
  `System.currentTimeMillis()`, inače pomeranje sata zaobilazi pravila.
- Privatnost: aplikacija nema dozvolu za internet. Ne dodaj je. U pregledaču se
  čita samo adresna traka po ID-u polja, nikad sadržaj stranice. `GuardDiagnostics`
  ne sme da beleži adrese, nazive aplikacija ni tekst sa ekrana.
- Pazi na bateriju: `GuardService` radi stalno. Ne čitaj ceo prozor aplikacije
  bez potrebe; aplikacije bez pravila ("quiet") se ne čitaju.

## Tekst u interfejsu

- Tekst se u kodu piše na srpskom latinicom. Prikaz ga sam prevodi na ćirilicu
  (`Translit`) ili engleski (`Lang`).
- Svaki novi tekst u interfejsu mora da dobije red u
  `app/src/main/assets/en.tsv` (engleski prevod). Format: srpski tekst, TAB,
  engleski tekst — jedan red po tekstu ili delu rečenice. Kad se rečenica sklapa
  od delova i vrednosti, svaki deo je poseban red (pazi na razmake na krajevima).
  `LangTest` ispisuje tekstove bez prevoda.
- Tekst koji ne ide kroz `Ui.Label` (Toast, hint, tekst na grafikonu) obavij sa
  `Ui.cyr(...)`. Naziv aplikacije prijavi sa `Translit.keep(...)` da ostane kako jeste.

## Struktura projekta

- Gradle projekat, modul `app/`. `applicationId com.cuvar.app`, `minSdk 26`,
  `compileSdk/targetSdk 36`, Java 11. Bez Kotlina i bez spoljnih biblioteka;
  ekrani se prave iz koda (`Ui`, `Sheet`), ne iz XML-a. Launcher aktivnost: `MainActivity`.
- Debug build (`app/build.gradle`) nema custom signing config — Gradle ga
  potpisuje default debug keystore-om, pa je `./gradlew assembleDebug` dovoljan
  za lokalni test na emulatoru/telefonu (paket `com.cuvar.app.debug`).
- `tests/run.ps1` pokreće čiste Java unit testove (bez Android SDK-a) nad
  klasama iz `tests/sources.txt`. Test je obična klasa sa `main` u
  `tests/com/cuvar/app/*Test.java`. Nova klasa bez Android zavisnosti koju test
  koristi mora da se doda u `tests/sources.txt`.
- `tests/README.md` sadrži detaljne scenarije za ručnu proveru na
  telefonu/emulatoru (dnevni limiti, noćni blok, pitalice, vremenski režimi...).
- Komentari u kodu su kratki i na srpskom.
