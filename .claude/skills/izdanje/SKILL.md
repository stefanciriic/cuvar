---
name: izdanje
description: Release. Finish a change in Čuvar and publish it - check, commit, push to main and follow the CI build. Use when a task is done and needs to be pushed.
---

# Release (commit + push to main)

Every push to `main` creates a new app release (except when only `.md`, `.claude/**` or
`tools/**` changed). Do not push half-done changes.

1. `git pull --rebase origin main`
2. A code change (`app/`, `tests/`, gradle) must go through the flow from the `rad-sa-agentima`
   skill: review by the required reviewers, then the emulator tester if one is running.
   `tools/review-stanje.sh` must say "Sve je spremno" (all ready); otherwise the hook blocks the
   commit and says what is missing.
3. `tools/check.sh` (Windows: `powershell ./tools/check.ps1`). It prints only errors.
   - It must pass before the push (the hook blocks `git push` otherwise).
   - If it says "Build preskočen: nema Android SDK-a" (build skipped: no Android SDK), CI will
     check the build. Do not use new system constants (see CLAUDE.md, hidden APIs).
4. `git add` all code changes (the commit must contain exactly the reviewed state). Commit message
   in English, briefly what changed.
5. **Cloud only** (`check.sh` said "Build preskočen") and the change touches code: first push the
   thread's own branch (`git push -u origin HEAD:<thread branch>`). `check.yml` builds it (tests,
   debug build, release lint) without a release. Do not sleep in a turn: schedule a check-back in
   ~6 min, then GitHub MCP `actions_list` (workflow `check.yml`, that branch, perPage 1). Red: fix,
   do not push `main`. Green: go on. Locally and for `.md`/`tools/`/`.claude/` only, skip this step.
6. `git pull --rebase origin main` once more, then `git push origin HEAD:main`.
7. Check CI: locally `gh run watch $(gh run list -L1 --json databaseId -q '.[0].databaseId') --exit-status`,
   and only if it fails `gh run view --log-failed | tail -60` (not the whole log).
   In the cloud: GitHub MCP `actions_list` (perPage 1), then `get_job_logs` with `failed_only`.
8. Report (in Serbian): the release number (`v<run_number+100>`), whether CI is green, who
   reviewed, and whether it was checked in the emulator and on a phone.
