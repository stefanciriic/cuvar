# Čuvar — instructions for Claude (English translation)

[Srpski](CLAUDE.md) · **English**

> Claude Code reads [CLAUDE.md](CLAUDE.md) (Serbian); this file is a translation for
> people. If you change the rules, change CLAUDE.md first. The workflow parts about
> pushing to `main` and the agent pipeline apply to the maintainer's sessions;
> external contributors follow [CONTRIBUTING.md](CONTRIBUTING.md).

CLAUDE.md is loaded into every session and every agent, so it holds only rules that always
apply. Details are read on demand: [ARCHITECTURE.en.md](ARCHITECTURE.en.md) (packages, build,
tests, agents), [README.en.md](README.en.md) (features), skills in `.claude/skills/`.

An Android app (Java) that limits phone use: rules per app and site, daily limits, night
lock, a daily code 17–22, quiz questions. The main logic is in `GuardService` (an
Accessibility service: tracks what is open, measures time, shows the block).

## Working rules

- Replies and code comments are in Serbian, short.
- Before changes, `git pull --rebase` (cloud sessions also work on the repo). Once per clone
  `git config core.hooksPath tools/githooks` (in the cloud the hook from `.claude/settings.json` does not
  run: the git hook guards the review gate, the author and the message).
- Check: `tools/check.sh` (Windows: `powershell ./tools/check.ps1`): tests as in CI +
  `assembleDebug`, prints only errors. Don't run Gradle by hand without a filter. Local
  `javac` accepts hidden APIs that CI rejects; for newer system intents use a string.
- Finished task: commit (English, short) and push straight to `main`, no PR, then check
  CI (`izdanje` skill). Every push to `main` is a new release, so don't push half-done work;
  a push touching only `.md`, `.claude/**` or `tools/**` doesn't build.
- A change in `app/`, `tests/` or gradle files follows the `rad-sa-agentima` skill
  (implementer + reviewers; the `tools/guard-hook.sh` hook blocks a commit without review and check).
- Large files (`data/Store.java`, `GuardService.java`, `unlock/QuizBank.java`,
  `assets/en.tsv`): never whole; `grep -n`, then only a line range.
- Don't touch `app/cuvar.keystore` or CI signing without asking (wrong key = the new
  version can't install over the old one).
- When you say something is done, say whether it was checked on a phone (most newer things weren't).

## User safety (most important)

- Never a change that can cut the internet or lock the phone with no way out.
- Phone (dialer), SMS and the home screen are never blocked (`exemptApps`); emergency
  calls always work.
- Site blocking via a local VPN/DNS was removed after the 2026-10-10 incident (it cut the
  internet, and self-protection closed the screen for turning the VPN off). Don't bring it
  back without explicit agreement and a test on a phone.
- Self-protection (`guardsSelf`) must not close screens needed to get out of trouble
  (VPN, network, battery...).

## App rules to respect in code

- Tightening applies at once. Loosening can only be requested 06:00–10:00 and takes effect
  at 06:00 the next day (`Store.change()` refuses outside the window and creates a hold,
  `rules/ModeHolds`; a mode spanning 06:00 lasts until its end, `carry`). The service reads
  only `...Now()` (state in effect: `eApps`, `eSites`, `eSchedules`, `eNight`, `eProtect`).
  Every new setting that loosens goes through `change()`, `enforce()`, `pendingChanges()`,
  `cancelPending()`.
- Spent limit, night, mode, focus, open count and pause are "hard" blocks
  (`GuardService.hard`): not even the code unlocks them.
- Time only from `store.now()` / `store.day()`, never `System.currentTimeMillis()`.
- Privacy: no internet permission (don't add it). In browsers only the address bar by
  field ID. `GuardDiagnostics` doesn't log addresses, app names or on-screen text.
- Errors must not crash the process: every entry from Android goes through
  `Safe.click/run/screen` or `GuardService.guarded`; rescheduling in `finally`; empty
  `catch` only with a comment ([PLAN-GRESKE.md](PLAN-GRESKE.md)).
- Battery: don't read the whole window without need; apps without rules ("quiet") aren't read.
- New logic (time, rules, recognition) = a small class without Android in `rules/` with a
  test (+ a line in `tests/sources.txt`); `Store`/`GuardService` only read, store and call it.
- Manifest components (`GuardService`, activities) stay in `com.cuvar.app` (renaming turns
  Accessibility off). No Kotlin, no external libraries; screens built in code (`Ui`,
  `Sheet`), not XML.

## UI text

- Code text is Serbian Latin; Cyrillic (`Translit`) and English (`Lang`) are automatic.
- Every new text gets a line in `assets/en.tsv` and `assets/de.tsv` (`novi-tekst` skill).
- Text outside `Ui.Label` (Toast, hint, chart) is wrapped in `Ui.cyr(...)`; an app name
  in `Translit.keep(...)`.
