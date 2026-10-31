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
├── data                 Store: podešavanja, izmereno vreme, "popuštanje od sutra"
├── rules                Čista pravila bez Android-a: limiti, režimi, šifra, sat, vrste blokade
├── guard                Delovi servisa: aplikacija napred, prozor blokade, pauza, dijagnostika
├── web                  Pregledači: adresna traka, domeni, veza aplikacija ↔ sajt
├── unlock               Pitalice, igrica, tastatura za šifru
├── ui                   Zajednički delovi ekrana, grafikoni, prozori podešavanja
└── i18n                 Engleski prevod i ćirilica
```

| Paket | Klase |
|---|---|
| `com.cuvar.app` | `MainActivity`, `RulesActivity`, `ScheduleActivity`, `FocusActivity`, `StatsActivity`, `SubActivity`, `GuardService`, `NotificationCounter` |
| `data` | `Store` |
| `rules` | `BlockKind`, `CodeAttempts`, `DailyCode`, `DailySchedule`, `DayLimit`, `DomainRules`, `LimitWarnings`, `NotifCounts`, `RuleMerge`, `SessionLimit`, `TrustedClock`, `UnlockCost`, `UnlockStreak`, `UnlockTimer`, `UsageCalendar`, `UsageTracker` |
| `guard` | `BlockOverlayContent`, `Clones`, `ForegroundApp`, `GuardDiagnostics`, `GuardOverlayWindow`, `LoadMeter`, `MusicOwner`, `PauseOverlayContent`, `SystemScreens` |
| `web` | `BrowserAddressState`, `BrowserMonitor`, `Hosts`, `Links`, `WebAddress` |
| `unlock` | `Jokes`, `NumberGame`, `NumberGameView`, `PinPad`, `Quiz`, `QuizBank` |
| `ui` | `Charts`, `CheckRow`, `SettingsSheets`, `Sheet`, `Ui` |
| `i18n` | `Lang`, `Translit` |

## Pravila

1. **Komponente iz manifesta ostaju u `com.cuvar.app`.** Android pamti uključen
   servis za Pristupačnost po punom imenu klase (`com.cuvar.app.GuardService`).
   Ako se klasa premesti, posle ažuriranja Pristupačnost je isključena i telefon
   je bez zaštite. Isto važi za `NotificationCounter` i aktivnosti (prečice, dozvole).
2. **`rules` ne zavisi od Android-a ni od drugih paketa.** Svaka klasa u `rules`
   (i ostale čiste klase iz `tests/sources.txt`) mora da se kompajlira bez SDK-a.
   Novu logiku piši ovde, sa testom u `tests/com/cuvar/app/`.
3. **Smer zavisnosti:** `com.cuvar.app` → `ui`/`guard`/`web`/`unlock` → `data` → `rules`.
   `i18n` koriste svi. Cilj je da `data` ne zna za ekrane ni za `GuardService`.
   Postojeći izuzeci koje treba ukloniti pri podeli `Store`-a: `Store` koristi
   `Ui` za format datuma i brojanje ("3 aplikacije") i `GuardService.exemptApps`.
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

## Sledeći korak

`data/Store.java` (~2.200 redova) i `GuardService.java` (~1.500 redova) su i dalje
preveliki. Plan, deo po deo i bez promene ponašanja:

- `Store` → `Store` (prefs, keš, `...Now()`), `StorePending` (`enforce`,
  `pendingChanges`, `cancelPending`), `StoreStats` (vreme, otvaranja, otključavanja),
  `StoreSchedules` (režimi).
- `GuardService` → odluka "koja blokada sada važi" kao čista klasa u `rules`
  (sa testom), a servis samo skuplja stanje i prikazuje rezultat.
