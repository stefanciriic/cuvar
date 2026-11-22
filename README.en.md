# Čuvar

[Srpski](README.md) · **English** · Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)

> This is an English translation of [README.md](README.md). The Serbian file is the
> source of truth; if the two disagree, the Serbian one wins (and please open an issue).

Čuvar ("guardian") is a self-control Android app: it helps you spend less time on
your phone. You set rules for chosen apps and websites (lock, daily limit, number
of opens, time-based modes), and Čuvar enforces them by showing a block screen on
top of the locked app or site.

Everything runs on the phone only: the app has no internet permission, sends
nothing, and does not read messages or page content. Details are in
[PRIVACY.md](PRIVACY.md).

## What Čuvar does (all rules)

This section is the reference for the rules; when a rule changes in code, it is
updated here too. State: 15 Nov 2026. Most rules have not been tested on a real phone.

### 1. What counts as "under a rule"

An app is under a rule if it has at least one of: lock, daily limit, max opens,
max continuous use, or it is in an **enabled** time mode (whether or not the mode
is running right now). A site is under a rule if it is on the site list or in an
enabled mode (subdomains included).

This matters because the short 6‑second pause before every open applies to
everything under a rule, **also outside a mode's period**.

The overall daily limit only counts items with their **own** rule: an app with a
lock, limit, opens or "continuous use" (and an app whose linked site has a rule),
and a site from the site list. Things that are only in a mode do not consume the
overall limit and are not locked when it runs out; the mode holds them during its
period.

Never blocked: the phone app (dialer), the SMS app, the home screen and Čuvar itself.

### 2. Where things are configured

Rules → "Apps, sites and modes" is the only screen for this:

- **Modes**: each mode is one block (time, days, what it blocks). An app or site
  is added to or removed from a mode only inside that block. This is also where
  "+ New mode" and the ready-made "Night lock" and "Work hours" live.
- **All day**: lock, daily limit, number of opens and "max continuous use",
  regardless of modes. An app's card only shows which modes it is in.
  In the list an item shows only its own rules, and the red line "Now: ..." only
  when a mode is blocking it right now or its limit is used up. At the top of an
  app or site card there is **"Now: ..."** (e.g. "blocked by Work hours until
  17:00", "locked, code from 17:00", "limit used up, until midnight") and
  **"Today: ..."**, the whole day of rules from 06:00 to 06:00, e.g.
  "06–09 locked · 09–17 blocked by Work hours · 17–22 opens with code · ...".
  It is computed from the rules currently in effect (`rules/DayPlan`).

### 2a. Rules for an app or site

| Rule | When it blocks | Can it be unlocked? |
|---|---|---|
| Lock | always | yes, with the daily code (17–22) |
| Site "always blocked" (limit 0) | always | yes, with the daily code (17–22) |
| Daily limit (min) | once used up, until midnight | **no** |
| Max opens per day | after the last allowed open, until midnight | **no** |
| Max continuous use | after that many minutes, mandatory 15 min break | **no** |
| Time mode | **only** during its period and on chosen days | **no** |

A mode outside its period blocks nothing. To keep an app locked for the rest of
the day, turn on its "Lock". The former mode option "Outside the period, ask for
the daily code" (and the per-app "Outside modes without daily code") was removed
on 14 Nov 2026: on the first start of the new version, every item of such an
enabled mode got "Lock" (site: "always blocked"), except exempted ones, so nothing
became looser.

- Leaving an app for less than 15 min does not end a "continuous" session.
- A warning comes 5 and 1 min before a limit runs out.
- An app and its site (Instagram and instagram.com) are one item: shared time, the
  stricter lock and limit apply. Number of opens and "max continuous use" apply
  only to the app, not to its site.
- A site without an installed app (e.g. facebook.com) has the same options:
  modes, "Lock", "Outside the overall daily limit" and daily limit. "Max opens",
  "Max continuous use" and "Calls always go through" exist only for apps.
- "Calls always go through" (per app; default WhatsApp, Viber, Telegram, Signal,
  Messenger): the call screen works at night and over the limit too; messages stay
  locked. While any internet call is running, all such apps go through.

### 3. Phone-wide rules

- **Night**: at night only what is in the **"Night lock"** mode is locked (a
  ready-made mode, 22:00–06:00, added on the Rules screen). Like every mode, it
  cannot be unlocked by anything. The general "Night block" that locked everything
  under a rule was removed on 11 Nov 2026 (it last applied that night until 06:00).
- **Overall daily limit**: sum of time in items with their own rule (not those that
  are only in a mode, see 1.; nor apps and sites marked "Outside the overall daily
  limit"). When it is used up, those items are locked until midnight, with no
  unlock. It can be raised once a day and only while not used up (up to 1 h by
  30 %, 2 h by 20 %, from 4 h by 10 %); the raise stays on the following days.
  Lowering applies immediately, turning it off from tomorrow. "Outside the overall
  daily limit" (in an app's or site's rules) applies immediately, even when the
  limit is already used up, but for only one item per day (day by the trusted
  clock, from midnight); other exempted items wait until tomorrow 06:00. Putting an
  item back under the limit applies immediately.
- **Focus**: a manually started period in which only chosen apps and sites work
  (plus phone/SMS). It cannot be unlocked with the code, but can be stopped in Čuvar.
- **Uninstall protection** (optional): closes the settings screens where Čuvar
  could be turned off in Accessibility, force-stopped or uninstalled. It never
  closes network, VPN or battery screens.
- **Mode: full or light** (Settings → Mode). Full (default) works through
  Accessibility. Light works without it, with only "Usage access" and "Display over
  other apps", so there is no Android "full control" warning. In light mode all app
  rules, modes, focus, the overall limit, the daily code and statistics work, but
  **not** website blocking, uninstall protection, or apps in split screen and
  picture-in-picture; blocking takes up to ~2 s, uses
  a bit more battery and a permanent notification is shown. Switching full → light
  is a loosening (06–10, effective tomorrow at 06:00); light → full applies
  immediately. While Accessibility is off but these two permissions are granted,
  Čuvar runs the light way in full mode too (after Čuvar is opened or the phone
  restarts).

### 4. Order (what is shown when several rules apply)

Overall limit → focus → opens → continuous-use break → mode → app daily limit /
lock → browser without a readable address and cloning apps → site rules. The first
block that applies is shown; unlocking one item does not unlock others.

### 5. Unlocking

- The only key is the **daily code** (4 digits). It is shown and accepted only
  **17:00–22:00**. Outside that time the block screen has neither the game nor
  the code input.
- Before the code comes a challenge (Settings → "Before unlocking"): quiz
  questions, a game (numbers in order, arrows, colors, odd one out, memory,
  sequence) or random.
- The cost rises with the number of unlocks that day: 1st easier, 2nd harder,
  3rd hardest. **At most 3 unlocks per day**, after that nothing until tomorrow.
  A new code is needed after each unlock.
- An unlock lasts **5 min** for that item, then for **60 min** nothing can be
  unlocked (the cooldown starts immediately for other items).
- 5 wrong codes in a row: 30 s wait.
- **Emergency unlock**: once a day, 10 min, skips the 60 min cooldown, but also
  only 17–22 and with the code and the hardest game.
- Hard blocks (overall limit, used-up limit, opens, continuous-use break, mode,
  focus) cannot be unlocked with the code or the emergency unlock.

### 6. Tightening applies immediately, loosening only in the morning and from the next day

A stricter rule applies immediately. Any loosening (larger limit, removed lock,
site removed from the list, mode turned off, deleted or shortened, fewer days, app
or site removed from a mode, protection turned off, exemption from the night lock
or the overall limit, app–site pair split) can **only be requested between 06:00
and 10:00** (trusted clock); outside that window it is refused with a message and
nothing changes. An accepted loosening takes effect **at 06:00 the next day**
(~24 h): until then the setting from before the request applies (`ModeHolds`,
pref `holds`). The "Scheduled changes" card shows what is waiting and when, and it
can be cancelled.

A mode running across 06:00 (e.g. 23–08) does not release a removed item at 06:00:
the part that is currently running stays until the end of its period. Adding to a
mode applies immediately, at any time. Not affected: the overall daily limit,
focus, the daily code, calls going through (they have their own rules). The
exemption from the overall limit still applies immediately for one item per day,
but it too can only be requested in the morning window.

### 7. "Remove from rules"

Removes an app or site from all rules and modes in the settings. Like every
loosening, it can only be requested 06:00–10:00 (otherwise a message and nothing
changes), and the rule keeps applying until **06:00 the next day**. Until then the
item is visible in "Scheduled changes" and can be restored ("Cancel these
changes"). Requests from an older version (which waited for confirmation) become
this kind of removal on first start (without checking the window).

### 8. Days and clocks

- Measured time, limits, number of opens, emergency unlock and number of unlocks
  reset at **midnight**.
- Scheduled loosening (and removal from rules) counts the day from **06:00**.
- The daily code changes at **17:00**.
- A time zone change (including automatic) applies only from the next **06:00**
  in the previous zone.
- Automatic time is followed only while the phone clock agrees with network time
  (Android 13+).
- Everything uses the "trusted clock": moving the clock or zone does not shorten a
  mode, reset limits or bring a new code.

### 9. Bypasses Čuvar closes

- App copies (Parallel Space and similar) fall under the original's rules, and
  cloning apps are locked while there are app rules.
- Replacement apps (NewPipe, ReVanced, Lite versions, other Reddit clients...) get
  the original's rules; a browser without rules shares the rules of a browser that
  has them, and an unknown browser is locked as long as a known one has a rule.
- Large floating windows (chat heads) over a locked app are covered by the block.
- "Calls go through" applies only to the app that is calling, not to everything
  during a call.
- A browser in which Čuvar cannot see the address (including in-app browsers in
  Instagram etc.) is locked while there are site rules; while some site is hard
  blocked, it cannot be unlocked either.
- Translators, AMP and site "mirrors" (e.g. yewtu.be) count as the real site.
- Split screen and floating windows are checked too.
- "Čuvar was not running" log: when the service was turned off or the phone was
  started in Safe Mode.

### 10. Open questions (proposals, not decided yet)

1. An app that is only in a mode also gets the 6 s pause outside the mode. Should
   the pause apply only to items with their own rule?
2. A raise of the overall limit stays forever, so the limit can grow day by day.
   Proposal: raise for today only, and turning the limit off from 06:00 instead of
   midnight (like other loosening).
3. "Emergency unlock" works only 17–22 and with the code, so it is not really an
   emergency: it only skips the 60 min cooldown. Proposal: rename it to "Unlock
   without waiting" or remove it.
4. While one internet call is running, all apps with "Calls always go through"
   pass, not only the calling one.
5. Number of opens and "max continuous use" do not apply to the same app's site in
   a browser.

### Statistics and language

Screens Today, Rules and Statistics. Statistics compares with earlier days, has a
per-app ring chart, columns for the last 7 days and a streak of days without
unlocking. Settings → Language: Serbian Latin, Serbian Cyrillic, English or German.

## How to install

1. On the [Releases](https://github.com/stefanciriic/cuvar/releases) page,
   download `cuvar.apk` from the latest release and install it.
2. Open Čuvar and enable it in Settings → Accessibility. Or, without
   Accessibility: "Without Accessibility (light)", then grant "Usage access" and
   "Display over other apps" (light mode, see 3.).
3. Recommended: grant "Usage access". Then Čuvar follows only apps with a rule and
   browsers, so it uses less battery.
4. Turn off battery optimization for Čuvar so the phone does not kill it.

A new version installs over the old one without losing settings.

## How it works (in short)

The heart of the app is `GuardService`, an Android AccessibilityService. It:

1. finds out which app is in the foreground (from Android via "Usage access", or
   from Accessibility events),
2. in a browser reads only the address bar (by field ID, never the page),
3. measures time per app and site,
4. checks all rules currently in effect, strictest first,
5. if needed, shows the block screen over everything (`TYPE_ACCESSIBILITY_OVERLAY`).

All settings and measured time are stored by `Store` in SharedPreferences, as
JSON. `Store` keeps two versions of the rules: what you configured and what is
actually in effect now. When you change something, the effective version becomes
stricter than both the old and the new; at 06:00 the effective version catches up
with the configured one. That is how loosening is delayed.

Time is measured with a "trusted clock": within one boot it is measured from boot
(cannot be moved), and manual clock changes are remembered and subtracted.

## Code structure

All code is plain Java, without extra libraries, in
`app/src/main/java/com/cuvar/app/`, split into packages by role. Screens are
built in code, without XML layouts. Why it is done this way and where a new class
goes: [ARCHITECTURE.en.md](ARCHITECTURE.en.md).

| Package / file | What it does |
|------|----------|
| `GuardService.java` | Accessibility service: foreground app, measuring, block decision, uninstall protection |
| `CuvarApp.java` | Process start: error log and crash recording (see "Phone load") |
| `MainActivity.java` | Home screen with the Today / Rules / Statistics tabs |
| `RulesActivity.java` | "Apps and sites": rule list and the editor for one item |
| `ScheduleActivity.java`, `FocusActivity.java` | Time modes, manual focus |
| `StatsActivity.java` | Statistics |
| `data/` | `Store` (all settings, delayed loosening) and its parts: `Clock` (trusted clock), `Unlocks`, `Modes` (modes), `DayLimits`, `UsageLog`, `Sessions`, `Focus`, `GuardLog`, `AppLabels` (app name) |
| `rules/` | Pure rules without Android, with tests: `BlockKind` (block types), `UnlockTimer`, `UnlockCost`, `CodeAttempts`, `TrustedClock`, `SessionLimit`, `RuleMerge`, `LimitWarnings`, `DailySchedule` (modes), `DayPlan` ("Today"/"Now" line), `CodeRegimeMigration`, `DailyCode` (daily code), `DayLimit`, `DomainRules`, `UsageTracker`, `UsageCalendar`, `UnlockStreak`, `ForegroundTime` |
| `guard/` | Service parts: `BlockScreen` (showing the block), `AppKinds` (kind of foreground app), `UsageAccounting` (recording time), `BlockOverlayContent` (block screen and unlocking), `PauseOverlayContent` (6 s pause), `GuardOverlayWindow` (window over everything, music), `AndroidUsage` (stats from Android's records), `ForegroundApp`, `SystemScreens`, `Clones`, `MusicOwner`, `LoadMeter`, `GuardDiagnostics` and `ErrorLog` (caught errors, no personal data) |
| `web/` | Browsers: `BrowserMonitor`, `BrowserWindows`, `BrowserAddressState`, `WebAddress` (real site), `Hosts`, `Links` (app ↔ site) |
| `unlock/` | `Quiz`, `QuizBank`, `Jokes`, `NumberGame`, `NumberGameView`, `PinPad` |
| `ui/` | `Ui`, `Sheet`, `CheckRow`, `AppPicker` (app list with search), `Charts`, `SettingsSheets` (settings dialogs) |
| `i18n/` | `Lang`, `Translit`, plus `app/src/main/assets/en.tsv` and `de.tsv` |

## Translation

UI text is written in code in Serbian (Latin script). The display translates it:

- to Cyrillic automatically (`Translit`), except brand names, domains and similar,
- to English from `app/src/main/assets/en.tsv`: one line per text or sentence
  fragment, `serbian<TAB>english`. Every new text in the app needs a new line
  here. `LangTest` prints texts that are missing a translation.
- to German from `app/src/main/assets/de.tsv` (same format, `serbian<TAB>german`;
  a text without a German line falls back to English).

## Build and releases

Every push to the `main` branch starts GitHub Actions
(`.github/workflows/apk.yml`), which:

1. runs the automated tests (`tests/`),
2. builds the APK,
3. signs it (keys are in GitHub Secrets, see [SIGNING.en.md](SIGNING.en.md)),
4. publishes a new release `v<number>` with `cuvar.apk`.

Changing only `.md` files does not start a build.

Locally, from Android Studio or the command line:

```
gradlew assembleDebug
```

The debug build has the package `com.cuvar.app.debug` and can be installed next
to the release one.

## Tests

The automated tests are plain Java (no Android SDK) and check the logic: modes,
daily limit, code, quiz, domains, time measurement, translation.

```
tools/check.sh                 # Linux/macOS: tests as in CI + debug build
powershell ./tools/check.ps1   # Windows
powershell ./tests/run.ps1     # tests only
```

The list of classes the tests use is in `tests/sources.txt`. Manual scenarios for
checking on a phone are in [tests/README.en.md](tests/README.en.md).

## Known limitations

- Without locked phone Settings, Čuvar can be turned off in Accessibility or
  uninstalled. Uninstall protection makes this harder but does not fully prevent
  it (e.g. Safe Mode).
- The address is read only in known browsers (Chrome, Brave, Edge, Vivaldi,
  Samsung Internet, Firefox, Kiwi; Opera, Opera Mini, DuckDuckGo and Firefox Focus
  not yet tested on a phone). Other browsers are locked while there are site rules.
- Rules apply to the whole site, not to part of it (e.g. only youtube.com/shorts).
- While a call is running in an app with "Calls always go through", that whole app
  is open.
- Site blocking via DNS/VPN was removed on 10 Oct 2026 because it cut off the
  internet.
