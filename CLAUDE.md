# Čuvar — uputstvo za Claude

**Srpski** · [English](CLAUDE.en.md) (prevod za ljude; kad menjaš pravila, uskladi i njega)

Ovaj fajl se učitava u svaku sesiju i u svakog agenta, zato su ovde samo pravila koja važe
uvek. Detalji se čitaju po potrebi: [ARCHITECTURE.md](ARCHITECTURE.md) (paketi, build,
testovi, agenti), [README.md](README.md) (funkcije), skill-ovi u `.claude/skills/`.

Android aplikacija (Java) koja ograničava korišćenje telefona: pravila po aplikaciji i
sajtu, dnevni limiti, noćni blok, dnevna šifra 17–22, pitalice. Glavna logika je u
`GuardService` (Accessibility servis: prati šta je otvoreno, meri vreme, prikazuje blokadu).

## Pravila rada

- Odgovori i komentari u kodu su na srpskom, kratko.
- Pre izmena `git pull --rebase` (na repou rade i sesije iz oblaka). Jednom po klonu
  `git config core.hooksPath tools/githooks` (u oblaku hook iz `.claude/settings.json` ne radi:
  git hook čuva review gate, autora i poruku).
- Provera: `tools/check.sh` (Windows: `powershell ./tools/check.ps1`): testovi kao CI +
  `assembleDebug`, ispisuje samo greške. Ne pokreći Gradle ručno bez filtera. Lokalni
  `javac` prihvata skrivene API-je koje CI odbija; za novije sistemske intent-e koristi string.
- Gotov zadatak: commit (engleski, kratko) i push direktno na `main`, bez PR-a, pa proveri
  CI (skill `izdanje`). Svaki push na `main` je novo izdanje, zato ne pushuj polovično;
  push samo `.md`, `.claude/**` ili `tools/**` ne pokreće build.
- Izmena u `app/`, `tests/` ili gradle fajlovima ide po skill-u `rad-sa-agentima`
  (izvođač + pregledači; hook `tools/guard-hook.sh` blokira commit bez review-a i check-a).
- Veliki fajlovi (`data/Store.java`, `GuardService.java`, `unlock/QuizBank.java`,
  `assets/en.tsv`): nikad ceo; `grep -n`, pa samo opseg redova.
- Ne diraj `app/cuvar.keystore` ni potpisivanje u CI bez pitanja (pogrešan ključ = nova
  verzija ne može preko stare).
- Kad kažeš da je gotovo, reci da li je provereno na telefonu (većina novijeg nije).

## Bezbednost korisnika (najvažnije)

- Nikad izmena koja može da preseče internet ili zaključa telefon bez izlaza.
- Telefon (dialer), SMS i početni ekran se nikad ne blokiraju (`exemptApps`); hitni
  pozivi uvek rade.
- Blokiranje sajtova preko lokalnog VPN/DNS-a je uklonjeno posle incidenta 10.10.2026
  (presekao internet, a zaštita je zatvarala ekran za gašenje VPN-a). Ne vraćaj ga bez
  izričitog dogovora i provere na telefonu.
- Zaštita od isključivanja (`guardsSelf`) ne sme da zatvara ekrane potrebne za izlaz iz
  problema (VPN, mreža, baterija...).

## Pravila aplikacije u kodu

- Pooštravanje važi odmah. Popuštanje može da se zatraži samo 06:00–10:00 i važi sutra u
  06:00 (`Store.change()` odbija van prozora i pravi hold, `rules/ModeHolds`; režim preko
  06:00 traje do kraja, `carry`). Servis čita samo `...Now()` (stanje koje sada važi:
  `eApps`, `eSites`, `eSchedules`, `eNight`, `eProtect`). Svako novo podešavanje koje
  olakšava ide kroz `change()`, `enforce()`, `pendingChanges()`, `cancelPending()`.
- Potrošen limit, noć, režim, fokus, broj otvaranja i pauza su "tvrde" blokade
  (`GuardService.hard`): ne otključavaju se ni šifrom.
- Vreme samo iz `store.now()` / `store.day()`, nikad `System.currentTimeMillis()`.
- Privatnost: bez dozvole za internet (ne dodaj je). U pregledaču samo adresna traka po
  ID-u polja. `GuardDiagnostics` ne beleži adrese, nazive aplikacija ni tekst sa ekrana.
- Greške ne smeju da obore proces: svaki ulaz iz Androida ide kroz `Safe.click/run/screen`
  ili `GuardService.guarded`; ponovno zakazivanje u `finally`; prazan `catch` samo uz
  komentar ([PLAN-GRESKE.md](PLAN-GRESKE.md)).
- Baterija: ne čitaj ceo prozor bez potrebe; aplikacije bez pravila ("quiet") se ne čitaju.
- Nova logika (vreme, pravila, prepoznavanje) = mala klasa bez Androida u `rules/` sa
  testom (+ red u `tests/sources.txt`); `Store`/`GuardService` je samo čitaju, čuvaju i zovu.
- Komponente iz manifesta (`GuardService`, aktivnosti) ostaju u `com.cuvar.app`
  (preimenovanje gasi Pristupačnost). Bez Kotlina i spoljnih biblioteka; ekrani iz koda
  (`Ui`, `Sheet`), ne iz XML-a.

## Tekst u interfejsu

- U kodu srpski latinicom; ćirilica (`Translit`) i engleski (`Lang`) idu automatski.
- Svaki novi tekst dobija red u `assets/en.tsv` i `assets/de.tsv` (skill `novi-tekst`).
- Tekst van `Ui.Label` (Toast, hint, grafikon) obavij sa `Ui.cyr(...)`; naziv aplikacije
  sa `Translit.keep(...)`.
