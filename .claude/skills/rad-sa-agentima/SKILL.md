---
name: rad-sa-agentima
description: Working with agents. The main agent's (orchestrator's) workflow in Čuvar - task, implementer, parallel reviewers, fixes, check, emulator, commit. Use for every change in app/, tests/ or gradle files.
---

# Working with agents (main agent = orchestrator)

The main agent (agent `orkestrator` when enabled in settings.json) plans, splits the work and merges
findings. Questions about the code go to the `istrazivac` agent (researcher; haiku, read-only)
instead of reading files itself. It does not write code in `app/` and `tests/` itself (exception:
Stefan explicitly says it is a trivial change and needs no review). The agents are in
`.claude/agents/` (name glossary and review procedure in `.claude/agents/README.md`).

## Flow

1. `git pull --rebase origin main`, then `tools/review-novi.sh "title"` (archives the old `.review/`).
   Fill in `.review/zadatak.md` (the task, in Serbian): what, why, what the user sees afterwards,
   what is not touched.
2. **izvodjac** (implementer) writes the change. Model: `sonnet` for ordinary work; for
   `GuardService`, `Store`, `guard/`, `enforce()`/`pendingChanges()` run it with `model: opus`
   (a parameter of the agent call).
3. `tools/review-stanje.sh` says which reviewers are required. Run them **in parallel** in one
   message (several agent calls at once). In this phase they only write findings to
   `.review/nalazi/<name>.md`.
   - Reviewers default to `sonnet`. For changes in `GuardService`, `Store`, `guard/` and
     `enforce()` (implementer on `opus`) run `pregled-pravila` and `pregled-bezbednosti` with
     `model: opus`: a change by a stronger model is not approved only by a weaker one.
   - A second round only if there were fixes, and only the reviewers whose area was touched
     (saves the limit).
4. Fixes **one at a time** (never two agents changing code at the same time): a finding is fixed
   by the reviewer who found it or by the implementer. After every fix run the required reviewers
   again (in parallel), so all of them see and approve the **same** state (`tools/review-ok.sh <name>`).
5. At most **3 rounds**. If they still do not agree, or an agent on a weaker model fails to solve
   the same finding twice: stop. First try a stronger model (`opus`) instead of a third round; if
   that does not help either, put the disagreement with both positions to Stefan
   (`request_decision` or a message) and do not commit.
6. Run `tools/check.sh` yourself (no agent: it takes seconds and prints only errors). A failure
   sends the work back to the implementer (then review again, because the code changes).
7. **tester-emulator** if the change touches `GuardService`, `guard/`, `ui/` or screens and
   `adb devices` sees a device. Without a device: write "nije provereno u emulatoru" (not checked
   in the emulator) in the final message.
8. `tools/review-stanje.sh` must say "Sve je spremno" (all ready), then `git add` all code changes
   and commit. The hook blocks a code commit while anything is missing and says exactly what.
   Next: skill `izdanje`.

## Rules

- Nobody approves their own change: a reviewer who fixed code approves only after another
  reviewer has already approved that state (the script checks this).
- Only scripts write the markers in `.git/cuvar-*-ok`; the hook does not let the main agent or the
  implementer run `review-ok`/`emulator-ok`, or write markers directly.
- Every code change after approval invalidates the approvals and the check (only code paths are
  compared; a `.md` change does not invalidate).
- A finding is never deleted, only its `Status:` changes.
- In the final message: what was done, who reviewed what, what was rejected and why, whether it
  was checked in the emulator and on a phone.

## Review scope (from `tools/oznake.sh`, `obim_pregleda`)

| Change touches | Required |
|---|---|
| `rules/`, `data/` (`Store`) | rules + safety |
| `GuardService`, `guard/` | all four + emulator |
| `web/`, `unlock/` | all four |
| `ui/`, activities | UI/text + stability + emulator |
| `i18n/`, `en.tsv` | UI/text + stability |
| only `tests/` | rules |
| other (manifest, gradle, `CuvarApp`) | all four |
