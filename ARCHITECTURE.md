# Arhitektura Čuvara

Kratko: Čuvar koristi **slojeve po ulozi** (paketi `ui`, `data`, `rules`, `guard`,
`web`, `unlock`, `i18n`). Po duhu je to MVC: `data.Store` je model, aktivnosti su
kontroleri, a `ui` pravi prikaz iz koda. Pravila koja odlučuju šta se blokira su
čiste Java klase u `rules`, sa testovima.

## Zašto ne MVVM / Clean Architecture

Google za Android preporučuje UI sloj + sloj podataka (+ opcioni domenski sloj),
obično kao MVVM sa `ViewModel`, `LiveData`/`Flow` i Room bazom. To su Jetpack
biblioteke, a Čuvar namerno nema spoljne biblioteke (manji APK, manje dozvola,
lakši pregled). Bez njih bi MVVM bio sopstveni okvir koji ništa ne dobija:

- Glavni posao ne radi ekran nego `GuardService`, koji radi stalno i nema
  životni ciklus ekrana, pa `ViewModel` tu ne pomaže.
- Ekrani su jednostavni: pročitaju `Store`, nacrtaju, sačuvaju izmenu.

Od preporuke uzimamo ono što vredi i bez biblioteka: **odvojen sloj podataka**
(`data`), **odvojen domenski sloj bez Android-a** (`rules`, testiran na računaru)
i **tok u jednom smeru**: ekran menja `Store`, servis čita `Store`.

## Paketi

```
com.cuvar.app            Komponente iz AndroidManifest-a (aktivnosti, servisi)
├── data                 Store i delovi: podešavanja, vreme, režimi, otključavanje, "popuštanje od sutra"
├── rules                Čista pravila bez Android-a: limiti, režimi, šifra, sat, vrste blokade
├── guard                Delovi servisa: aplikacija napred, prozor blokade, pauza, dijagnostika
├── web                  Pregledači: adresna traka, domeni, veza aplikacija ↔ sajt
├── unlock               Pitalice, igrica, tastatura za šifru
├── ui                   Zajednički delovi ekrana, grafikoni, prozori podešavanja
└── i18n                 Engleski prevod i ćirilica
```

| Paket | Klase |
|---|---|
| `com.cuvar.app` | `CuvarApp` (pokretanje procesa, beleženje padova), `MainActivity`, `RulesActivity`, `ScheduleActivity`, `FocusActivity`, `StatsActivity`, `SubActivity`, `GuardService` |
| `data` | `Store` (ulaz: `Store.get(c)`, pravila aplikacija i sajtova, popuštanje od sutra) i njegovi delovi: `Clock` (pouzdano vreme), `Unlocks` (šifra, otključavanje, hitno), `Modes` (vremenski režimi), `DayLimits` (ukupni dnevni limit), `UsageLog` (izmereno vreme, otvaranja), `Sessions` (korišćenje u komadu), `Focus`, `GuardLog` (kad Čuvar nije radio), `AppLabels` (naziv aplikacije) |
| `rules` | `AppRule` (pravilo aplikacije, spajanje „strože od dva“), `BlockKind`, `BlockRules` (koja blokada sada važi), `CodeAttempts`, `DailyCode`, `DailySchedule`, `DayLimit`, `DomainRules`, `ItemKey` (ključevi "app:" i "site:"), `LimitWarnings`, `RuleMerge`, `SessionLimit`, `TrustedClock`, `UnlockCost`, `UnlockStreak`, `UnlockTimer`, `UsageCalendar`, `UsageTracker` |
| `guard` | delovi servisa: `BlockScreen` (prikaz i sakrivanje blokade, „nazad“), `AppKinds` (vrsta aplikacije napred: bez pravila, kopija, WebAPK, izuzeta, podešavanja), `UsageAccounting` (upis vremena, upozorenja pred limit), `GuardFacts` (pravila iz `Store`-a za `BlockRules`); ostalo: `BlockOverlayContent`, `Clones`, `ErrorLog` (dnevnik grešaka, čist i testiran), `ForegroundApp`, `GuardDiagnostics` (prijava greške u logcat i dnevnik na telefonu), `GuardOverlayWindow`, `LoadMeter`, `MusicOwner`, `PauseOverlayContent`, `SystemScreens` |
| `web` | `BrowserWindows` (prozori na ekranu, glavni prozor pregledača), `BrowserAddressState`, `BrowserMonitor`, `Hosts`, `Links`, `WebAddress` |
| `unlock` | `Jokes`, `NumberGame`, `NumberGameView`, `PinPad`, `Quiz`, `QuizBank` |
| `ui` | `AppPicker`, `Charts`, `CheckRow`, `SettingsSheets`, `Sheet`, `Ui` |
| `i18n` | `Lang`, `Translit` |

## Pravila

1. **Komponente iz manifesta ostaju u `com.cuvar.app`.** Android pamti uključen
   servis za Pristupačnost po punom imenu klase (`com.cuvar.app.GuardService`).
   Ako se klasa premesti, posle ažuriranja Pristupačnost je isključena i telefon
   je bez zaštite. Isto važi za aktivnosti (prečice, dozvole).
2. **`rules` ne zavisi od Android-a ni od drugih paketa.** Svaka klasa u `rules`
   (i ostale čiste klase iz `tests/sources.txt`) mora da se kompajlira bez SDK-a.
   Novu logiku piši ovde, sa testom u `tests/com/cuvar/app/`.
3. **Smer zavisnosti:** `com.cuvar.app` → `ui`/`guard`/`web`/`unlock` → `data` → `rules`.
   `i18n` koriste svi. Cilj je da `data` ne zna za ekrane ni za `GuardService`.
   Postojeći izuzeci koje treba ukloniti pri podeli `Store`-a: `Store` koristi
   `Ui` za format datuma i brojanje ("3 aplikacije") i `AppKinds.exemptApps` (guard).
   Ne dodaji nove ovakve veze.
4. **Vreme samo iz `Store.now()` / `Store.day()`**, nikad `System.currentTimeMillis()`
   u pravilima.
5. Klase i metode koje koristi drugi paket su `public`. Ono što je samo za
   jednu klasu ostaje `private`.

## Gde ide nova stvar

| Dodaješ... | Paket |
|---|---|
| novo pravilo, računanje vremena, prepoznavanje | `rules` (+ test), poziv iz `Store`/`GuardService` |
| novo podešavanje koje se čuva | `data.Store` (kroz `enforce()`/`pendingChanges()` ako olakšava) |
| novi ekran | aktivnost u `com.cuvar.app` (i u manifest) ili prozor (`Sheet`) u `ui` |
| novi način otključavanja | `unlock` |
| podrška za novi pregledač | `web.BrowserMonitor` |
| tekst u interfejsu | red u `app/src/main/assets/en.tsv` |

## Kako se koristi `Store`

`Store.get(c)` je jedini ulaz. Delovi su polja `Store`-a i dele isto stanje i isto
zaključavanje (`synchronized` na `Store`):

```java
store.now();                 // pouzdano vreme, uvek odavde
store.day();                 // današnji dan "yyyyMMdd"
store.unlock.tryCode(code);  // dnevna šifra
store.modes.schedules();     // vremenski režimi
store.dayLimits.dayLimit();  // ukupni dnevni limit
store.usageLog.usedToday(k); // izmereno vreme
```

Pravila aplikacija i sajtova i mehanizam "popuštanje od sutra" (`enforce()`,
`pendingChanges()`, `cancelPending()`) su u samom `Store`-u, jer dodiruju sve delove.

## Kako je podeljen `GuardService`

`GuardService` (~850 redova) ostaje u `com.cuvar.app` i drži životni ciklus servisa,
događaje i glavnu proveru `check()`. Njegovi delovi su polja servisa:
`svc.screen` (`BlockScreen`), `svc.kinds` (`AppKinds`), `svc.accounting`
(`UsageAccounting`), `svc.windows` (`BrowserWindows`). Stanje koje delovi dele
(`currentPkg`, `rawPkg`, `overlayRule`...) je zato `public` u servisu. Menja ga samo
servis i njegovi delovi, nikad ekrani.

## Odluka o blokadi

`check()` skuplja stanje (šta je napred, koji sajt, drugi prozori) i prikazuje rezultat.
Odluku "koje blokade sada važe i kojim redom" donosi `rules.BlockRules` (test:
`BlockRulesTest`); pravila čita preko `BlockRules.Facts`, koje u servisu daje
`guard.GuardFacts`. Redosled u listi je bitan: `BlockKind.pick` prikazuje prvu
blokadu koja važi. Novo pravilo blokade ide u `BlockRules`, sa testom.
