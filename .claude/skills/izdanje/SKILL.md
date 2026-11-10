---
name: izdanje
description: Završi izmenu u Čuvaru i objavi je - provera, commit, push na main i praćenje CI build-a. Koristi kad je zadatak gotov i treba ga pushovati.
---

# Izdanje (commit + push na main)

Svaki push na `main` pravi novo izdanje aplikacije (osim kad su menjani samo `.md`,
`.claude/**` ili `tools/**`). Ne pushuj polovične izmene.

1. `git pull --rebase origin main`
2. Izmena u kodu (`app/`, `tests/`, gradle) mora da prođe tok iz skill-a `rad-sa-agentima`:
   review obaveznih pregledača, pa tester u emulatoru ako je pokrenut. `tools/review-stanje.sh`
   mora reći "Sve je spremno"; hook inače blokira commit i kaže šta fali.
3. `tools/check.sh` (Windows: `powershell ./tools/check.ps1`). Ispisuje samo greške.
   - Mora da prođe pre push-a (hook blokira `git push` ako nije).
   - Ako piše "Build preskočen: nema Android SDK-a", build će proveriti CI. Ne
     koristi nove sistemske konstante (vidi CLAUDE.md, skriveni API-ji).
4. `git add` za sve izmene u kodu (commit mora da sadrži tačno pregledano stanje). Commit poruka na engleskom, kratko šta se promenilo.
5. `git pull --rebase origin main` još jednom, pa `git push origin HEAD:main`.
6. Proveri CI: lokalno `gh run watch $(gh run list -L1 --json databaseId -q '.[0].databaseId') --exit-status`,
   a samo ako padne `gh run view --log-failed | tail -60` (ne ceo log).
   U oblaku: GitHub MCP `actions_list` (perPage 1), pa `get_job_logs` sa `failed_only`.
7. Javi: broj izdanja (`v<run_number+100>`), da li je CI zelen, ko je pregledao, i da li je
   provereno u emulatoru i na telefonu.
