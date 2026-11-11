# Čuvar — uputstvo za Claude

Android aplikacija (Java) koja korisniku ograničava korišćenje telefona: pravila po
aplikaciji i sajtu, dnevni limiti, noćni blok 22–06, dnevna šifra 17–22 kao jedino
otključavanje, pitalice pri otključavanju. Glavna logika je u `GuardService`
(`app/src/main/java/com/cuvar/app/GuardService.java`) — Accessibility servis koji
prati koja je aplikacija/sajt otvoren, meri vreme i prikazuje blokadu preko svega.
Pregled funkcija i mapa fajlova: [README.md](README.md).

## Pravila rada

- Odgovori i komentari u kodu su na srpskom.
- Pre izmena uradi `git pull --rebase` (na repou rade i sesije iz oblaka).
- Provera: `tools/check.sh` (Windows: `powershell ./tools/check.ps1`) pokreće
  testove kao CI i `assembleDebug`, a ispisuje samo greške. Ne pokreći Gradle
  ručno bez filtera. Lokalni `javac` prihvata skrivene Android API-je koje CI
  odbija; za novije sistemske intent-e koristi string umesto konstante.
- Gotov zadatak: commit (engleski, kratko) i push direktno na `main`, bez PR-a,
  pa proveri CI. Postupak je u skill-u `izdanje`. Svaki push na `main` je novo
  izdanje (APK + release `v<run_number+100>`), zato ne pushuj polovične izmene;
  push samo `.md`, `.claude/**` ili `tools/**` ne pokreće build.
- Veliki fajlovi (`data/Store.java`, `GuardService.java`, `unlock/QuizBank.java`,
  `assets/en.tsv`): ne čitaj ih cele. Nađi mesto sa `grep -n`, pa čitaj samo taj
  opseg redova. Mapa paketa: [ARCHITECTURE.md](ARCHITECTURE.md).
- Ne briši i ne menjaj `app/cuvar.keystore` ni podešavanje potpisivanja u CI bez
  pitanja: pogrešan ključ znači da nova verzija ne može da se instalira preko stare.
- Kad kažeš da je nešto gotovo, reci i da li je provereno na telefonu. Većina
  novijih funkcija nije probana na pravom uređaju.

## Rad sa agentima

Glavna sesija je orkestrator: svaka izmena u `app/`, `tests/` ili gradle fajlovima ide po skill-u
`rad-sa-agentima`. Ukratko:
1. `tools/review-novi.sh "naslov"` i `.review/zadatak.md`; kod piše agent `izvodjac`.
2. `tools/review-stanje.sh` kaže koji pregledači su obavezni; oni rade paralelno i pišu u `.review/nalazi/<ime>.md`.
3. Ispravke jedna po jedna, pa ponovni review dok svi ne odobre isto stanje (`tools/review-ok.sh`, najviše 3 kruga).
4. `proveravac` (`tools/check.sh`), pa `tester-emulator` ako izmena dira ekrane/`guard/` i emulator radi.
5. Commit; hook (`tools/guard-hook.sh`) blokira commit koda dok fali review, check ili emulator.

Agenti: `.claude/agents/` (postupak pregleda: `.claude/agents/README.md`), skripte: `tools/`, radni fajlovi: `.review/` (nije u git-u).

| Agent | Model | Zašto |
|---|---|---|
| `orkestrator` (glavna sesija) | opus | plan, deljenje posla, spajanje nalaza, neslaganja |
| `istrazivac` | haiku | samo čita i nalazi mesto u kodu, da glavna sesija ne troši na čitanje |
| `izvodjac` | sonnet (opus za `GuardService`, `Store`, `guard/`, `enforce()`) | obične izmene; pregledači ga proveravaju |
| `pregled-bezbednosti`, `pregled-pravila` | sonnet (opus za jezgro, isto kao izvođač) | izmenu jačeg modela ne odobrava samo slabiji |
| `pregled-stabilnosti`, `pregled-prikaza` | sonnet | obrasci, tekst i raspored |
| `proveravac`, `tester-emulator` | haiku | pokreću komande i čitaju ispis |

Stalni orkestrator: `"agent": "orkestrator"` u `.claude/settings.json` (ili `claude --agent orkestrator`);
tada glavna sesija od početka deli posao, a hook joj ne da da sama menja `app/` i `tests/`.
Model se menja u polju `model:` u `.claude/agents/<ime>.md`.

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
- Greške ne smeju da obore proces (ekrani i zaštita su u istom procesu). Svaki ulaz iz Androida
  (događaj, klik, `Runnable`, prijemnik, crtanje ekrana) ide kroz `Safe.click/run/screen` ili
  `GuardService.guarded`; ponovno zakazivanje ide u `finally`. Prazan `catch` samo uz komentar zašto.
  Greške se vide u "Opterećenje telefona" (plan i stanje: [PLAN-GRESKE.md](PLAN-GRESKE.md)).
- Pazi na bateriju: `GuardService` radi stalno. Ne čitaj ceo prozor aplikacije
  bez potrebe; aplikacije bez pravila ("quiet") se ne čitaju.

## Tekst u interfejsu

- Tekst se u kodu piše na srpskom latinicom. Prikaz ga sam prevodi na ćirilicu
  (`Translit`) ili engleski (`Lang`).
- Svaki novi tekst u interfejsu mora da dobije red u `app/src/main/assets/en.tsv`
  (srpski TAB engleski; kako, bez čitanja celog fajla: skill `novi-tekst`).
- Tekst koji ne ide kroz `Ui.Label` (Toast, hint, tekst na grafikonu) obavij sa
  `Ui.cyr(...)`. Naziv aplikacije prijavi sa `Translit.keep(...)` da ostane kako jeste.

## Struktura projekta

- Kod je podeljen u pakete po ulozi (`data`, `rules`, `guard`, `web`, `unlock`, `ui`,
  `i18n`); pravila i "gde ide nova klasa" su u [ARCHITECTURE.md](ARCHITECTURE.md).
  Komponente iz manifesta (`GuardService`, aktivnosti) se
  ne premeštaju iz `com.cuvar.app`: promena imena klase gasi Pristupačnost posle
  ažuriranja.
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
- `Store` i `GuardService` zavise od Android-a i ne mogu da se testiraju na računaru.
  Novu logiku (računanje vremena, pravila, prepoznavanje) piši kao malu klasu bez
  Android-a (kao `UnlockTimer`, `SessionLimit`, `RuleMerge`, `BlockKind`) sa testom,
  a u `Store`/`GuardService` ostavi samo čitanje, čuvanje i poziv te klase.
- Provera u emulatoru preko adb-a: skill `test-emulator`. Detaljni scenariji su u
  `tests/README.md` (čitaj samo potreban odeljak).
- Komentari u kodu su kratki.
