# Čuvar architecture

[Srpski](ARCHITECTURE.md) · **English**

> English translation of [ARCHITECTURE.md](ARCHITECTURE.md); the Serbian file is
> the source of truth.

In short: Čuvar uses **layers by role** (packages `ui`, `data`, `rules`, `guard`,
`web`, `unlock`, `i18n`). In spirit it is MVC: `data.Store` is the model, the
activities are controllers, and `ui` builds the views in code. The rules that
decide what is blocked are pure Java classes in `rules`, with tests.

## Why not MVVM / Clean Architecture

For Android, Google recommends a UI layer + data layer (+ optional domain layer),
usually as MVVM with `ViewModel`, `LiveData`/`Flow` and a Room database. Those are
Jetpack libraries, and Čuvar intentionally has no external libraries (smaller APK,
fewer permissions, easier review). Without them MVVM would be a home-made framework
that gains nothing:

- The main work is not done by a screen but by `GuardService`, which runs all the
  time and has no screen lifecycle, so `ViewModel` does not help there.
- Screens are simple: read `Store`, draw, save the change.

From the recommendation we take what is worth having without libraries: a
**separate data layer** (`data`), a **separate domain layer without Android**
(`rules`, tested on the desktop) and **one-way flow**: a screen changes `Store`,
the service reads `Store`.

## Packages

```
com.cuvar.app            Components from AndroidManifest (activities, services)
├── data                 Store and its parts: settings, time, modes, unlocking, delayed loosening
├── rules                Pure rules without Android: limits, modes, code, clock, block types
├── guard                Service parts: foreground app, block window, pause, diagnostics
├── web                  Browsers: address bar, domains, app ↔ site link
├── unlock               Quiz, games, code keypad
├── ui                   Shared screen parts, charts, settings dialogs
└── i18n                 English/German translation and Cyrillic
```

| Package | Classes |
|---|---|
| `com.cuvar.app` | `CuvarApp` (process start, crash recording), `MainActivity`, `RulesActivity`, `ScheduleActivity`, `FocusActivity`, `StatsActivity`, `SubActivity`, `BootReceiver` (light mode after boot/update), `GuardService` |
| `data` | `Store` (entry point: `Store.get(c)`, app and site rules, delayed loosening) and its parts: `Clock` (trusted time), `Unlocks` (code, unlocking, emergency), `Modes` (time modes), `DayLimits` (overall daily limit), `UsageLog` (measured time, opens), `Sessions` (continuous use), `Focus`, `GuardLog` (when Čuvar was not running), `AppLabels` (app name) |
| `rules` | `AppRule` (an app's rule, "stricter of two" merge), `EnforcedRules` (all rules: tightening immediately, loosening from 06:00), `ModeHolds` (loosening only in the morning window, takes effect later), `BlockKind`, `BlockRules` (which block applies now), `CodeAttempts`, `DailyCode`, `DailySchedule`, `DayPlan` (an item's day for the "Today" line), `CodeRegimeMigration`, `DayLimit`, `DomainRules`, `ItemKey` ("app:" and "site:" keys), `LimitWarnings`, `RuleMerge`, `SessionLimit`, `TrustedClock`, `UnlockCost`, `UnlockStreak`, `UnlockTimer`, `UsageCalendar`, `UsageTracker`, `ForegroundTime` (time per app from Android's events), `LightMode` (when the service runs as a foreground light service, without Accessibility) |
| `guard` | service parts: `BlockScreen` (showing and hiding the block, "back"), `AppKinds` (kind of foreground app: no rule, copy, WebAPK, exempt, settings), `UsageAccounting` (recording time, warnings before a limit), `GuardFacts` (rules from `Store` for `BlockRules`); other: `BlockOverlayContent`, `Clones`, `ErrorLog` (error log, pure and tested), `AndroidUsage` (time for statistics from Android's records, like Digital Wellbeing), `ForegroundApp`, `GuardDiagnostics` (reports errors to logcat and the on-phone log), `GuardOverlayWindow`, `LoadMeter`, `MusicOwner`, `PauseOverlayContent`, `SystemScreens` |
| `web` | `BrowserWindows` (windows on screen, the browser's main window), `BrowserAddressState`, `BrowserMonitor`, `Browsers` (known browsers, address from text; pure), `Hosts`, `Links`, `WebAddress` |
| `unlock` | `Jokes`, `NumberGame`, `NumberGameView`, `PinPad`, `QuickGame` (arrows, colors, odd one out, memory, sequence; pure), `QuickGameView`, `Quiz`, `QuizBank` |
| `ui` | `AppPicker`, `Charts`, `CheckRow`, `Safe` (clicks and work that must not crash the process), `SettingsSheets`, `Sheet`, `Ui` |
| `i18n` | `Lang`, `Translit` |

## Rules

1. **Manifest components stay in `com.cuvar.app`.** Android remembers an enabled
   Accessibility service by the full class name (`com.cuvar.app.GuardService`).
   If the class moves, Accessibility is off after the update and the phone is
   unprotected. The same goes for activities (shortcuts, permissions).
2. **`rules` does not depend on Android or on other packages.** Every class in
   `rules` (and the other pure classes from `tests/sources.txt`) must compile
   without the SDK. Write new logic here, with a test in `tests/com/cuvar/app/`.
3. **Dependency direction:** `com.cuvar.app` → `ui`/`guard`/`web`/`unlock` → `data` → `rules`.
   `i18n` is used by everyone. The goal is that `data` knows nothing about screens
   or `GuardService`. Existing exceptions to remove when `Store` is split: `Store`
   uses `Ui` for date formatting and counting ("3 apps") and `AppKinds.exemptApps`
   (guard). Do not add new links like these.
4. **Time only from `Store.now()` / `Store.day()`**, never
   `System.currentTimeMillis()` in rules.
5. Classes and methods used by another package are `public`. What is used by only
   one class stays `private`.

## Where a new thing goes

| You are adding... | Package |
|---|---|
| a new rule, time calculation, detection | `rules` (+ test), called from `Store`/`GuardService` |
| a new stored setting | `data.Store` (through `enforce()`/`pendingChanges()` if it loosens) |
| a new screen | an activity in `com.cuvar.app` (and in the manifest) or a dialog (`Sheet`) in `ui` |
| a new way to unlock | `unlock` |
| support for a new browser | `web.BrowserMonitor` |
| UI text | a line in `app/src/main/assets/en.tsv` (and `de.tsv`) |

## How to use `Store`

`Store.get(c)` is the only entry point. The parts are fields of `Store` and share
the same state and the same lock (`synchronized` on `Store`):

```java
store.now();                 // trusted time, always from here
store.day();                 // today's day "yyyyMMdd"
store.unlock.tryCode(code);  // daily code
store.modes.schedules();     // time modes
store.dayLimits.dayLimit();  // overall daily limit
store.usageLog.usedToday(k); // measured time
```

App and site rules and the loosening mechanism (`change()`, `enforce()`,
`pendingChanges()`, `cancelPending()`) live in `Store` itself, because they touch
all parts. Every change to rule settings goes through `change()`: loosening can
only be requested in the morning window (06:00–10:00) and takes effect at 06:00
the next day (`ModeHolds`).

## How `GuardService` is split

`GuardService` (~850 lines) stays in `com.cuvar.app` and holds the service
lifecycle, events and the main check `check()`. Its parts are fields of the
service: `svc.screen` (`BlockScreen`), `svc.kinds` (`AppKinds`), `svc.accounting`
(`UsageAccounting`), `svc.windows` (`BrowserWindows`). State shared by the parts
(`currentPkg`, `rawPkg`, `overlayRule`...) is therefore `public` in the service.
Only the service and its parts change it, never screens.

## Build and tests

- Gradle project, module `app/`: `applicationId com.cuvar.app`, `minSdk 26`,
  `compileSdk/targetSdk 36`, Java 11. Launcher activity: `MainActivity`.
- The debug build has no signing config of its own (Gradle's debug key signs it), so
  `assembleDebug` is enough for an emulator/phone (package `com.cuvar.app.debug`).
- Tests: plain classes with `main` in `tests/com/cuvar/app/*Test.java`, no Android SDK,
  over the classes in `tests/sources.txt` (run by `tools/check.sh` or `tests/run.ps1`).
  `Store` and `GuardService` depend on Android and aren't tested on a computer.
- Emulator via adb: `test-emulator` skill; scenarios in `tests/README.en.md` (only the needed section).

## Agents (Claude Code)

The main session is `orkestrator` (`"agent"` in `.claude/settings.json`); the workflow is
the `rad-sa-agentima` skill, the review procedure is `.claude/agents/README.md`, work files
are in `.review/` (not in git). Each agent's model is the `model:` field in
`.claude/agents/<name>.md`:

| Agent | Model |
|---|---|
| `orkestrator` | opus |
| `istrazivac`, `proveravac`, `tester-emulator` | haiku |
| `izvodjac`, `pregled-*` | sonnet (opus per call for `GuardService`, `Store`, `guard/`, `enforce()`) |

## Block decision

`check()` gathers the state (what is in the foreground, which site, other windows)
and shows the result. The decision "which blocks apply now and in what order" is
made by `rules.BlockRules` (test: `BlockRulesTest`); it reads the rules through
`BlockRules.Facts`, provided in the service by `guard.GuardFacts`. The order in the
list matters: `BlockKind.pick` shows the first block that applies. A new block rule
goes into `BlockRules`, with a test.
