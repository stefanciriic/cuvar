# Review procedure (for all pregled-* agents)

This file is not an agent; the reviewers read it. The whole workflow: skill `rad-sa-agentima`.

Agent names stay Serbian because `tools/` scripts and the commit hook check them:
`orkestrator` = orchestrator, `istrazivac` = researcher, `izvodjac` = implementer,
`pregled-pravila` = rules review, `pregled-bezbednosti` = safety review,
`pregled-stabilnosti` = stability review, `pregled-prikaza` = UI/text review,
`tester-emulator` = emulator tester.
Write findings and replies in Serbian (Stefan reads them). Keep the status words exactly
as below (`otvoreno` = open, `ispravljeno` = fixed, `odbijeno` = rejected): scripts grep for them.

## Phase 1: findings (reviewers run in parallel)

1. Read `.review/zadatak.md` (the task) and all `.review/nalazi/*.md` (other reviewers' too).
2. Look at the change: `git diff HEAD` and new files (`git status --short`). Read large files by line range.
3. Write only to your own file `.review/nalazi/<your-name>.md`. In this phase you **do not change code** (other reviewers read the same diff).
4. Reply to others' findings in your area in your own file (`Odgovor na N3 (pregled-pravila): slažem se / ne slažem se, jer ...`).
   Never edit someone else's file.

Finding format (ID = agent prefix + number, e.g. `PR1`, `BZ2`, `ST1`, `PK1`):

```
## PR1 · pregled-pravila · kritično
Fajl: app/src/main/java/com/cuvar/app/data/Store.java:412
Problem: popuštanje dnevnog limita važi odmah, ne od 06:00.
Posledica za korisnika: podigne limit u 23:00 i odmah nastavi.
Predlog: kroz pendingChanges().
Status: otvoreno
Promenio: pregled-pravila
```

(Fields: `Fajl` = file:line, `Problem`, `Posledica za korisnika` = effect on the user,
`Predlog` = suggestion, `Status`, `Promenio` = changed by.)

Severity: `kritično` = critical (locked out with no way out, hole in the rules, process crash,
privacy leak), `važno` = important, `sitno` = minor.
Do not report matters of taste. A finding without file, line and effect on the user is not written.
If you have no findings, the file has one line: `Nema nalaza.`

## Phase 2: fixes (the main agent runs one agent at a time)

- You may fix your own finding: change the code, then in the finding set `Status: ispravljeno`,
  `Promenio: <you>` and what you did. Another agent then reviews that fix; you do not approve it
  first (`tools/review-ok.sh` refuses that).
- A finding you reject: `Status: odbijeno: <reason>`. A finding is never deleted.
- When you disagree with another reviewer, write both positions in your file; the main agent
  decides or asks Stefan.

## Approval

When there are no open findings in your area and the code state is acceptable to you:
`tools/review-ok.sh <your-name>`. The script refuses while any finding has `Status: otvoreno`.
Every later code change invalidates the approval.

Return to the main agent: number of findings per severity, whether you approved, and disagreements.
