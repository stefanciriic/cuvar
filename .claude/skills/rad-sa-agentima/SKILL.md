---
name: rad-sa-agentima
description: Tok rada glavnog agenta (orkestratora) u Čuvaru - zadatak, izvođač, paralelni pregledači, ispravke, check, emulator, commit. Koristi za svaku izmenu u app/, tests/ ili gradle fajlovima.
---

# Rad sa agentima (glavni agent = orkestrator)

Glavni agent (agent `orkestrator` kad je uključen u settings.json) planira, deli posao i spaja nalaze.
Pitanja o kodu šalje agentu `istrazivac` (haiku, samo čita) umesto da sam čita fajlove. Kod u `app/` i `tests/` ne piše sam
(izuzetak: Stefan izričito kaže da je sitnica i da ne treba review). Agenti su u
`.claude/agents/`, postupak pregleda u `.claude/agents/README.md`.

## Tok

1. `git pull --rebase origin main`, pa `tools/review-novi.sh "naslov"` (arhivira stari `.review/`).
   Popuni `.review/zadatak.md`: šta, zašto, šta korisnik vidi posle, šta se ne dira.
2. **izvodjac** piše izmenu. Model: `sonnet` za obično; za `GuardService`, `Store`, `guard/`,
   `enforce()`/`pendingChanges()` pokreni ga sa `model: opus` (parametar pri pozivu agenta).
3. `tools/review-stanje.sh` kaže koji su pregledači obavezni. Pokreni ih **paralelno** u jednoj
   poruci (više poziva agenta odjednom). U ovoj fazi samo pišu nalaze u `.review/nalazi/<ime>.md`.
   - Pregledači su podrazumevano na `sonnet`. Za izmene u `GuardService`, `Store`, `guard/` i
     `enforce()` (izvođač na `opus`) pokreni `pregled-pravila` i `pregled-bezbednosti` sa
     `model: opus`: izmenu jačeg modela ne odobrava samo slabiji.
   - Drugi krug samo ako je bilo ispravki, i samo pregledači čija oblast je dirnuta (štedi limit).
4. Ispravke **jedna po jedna** (nikad dva agenta koji menjaju kod istovremeno): nalaz ispravlja
   pregledač koji ga je našao ili izvođač. Posle svake ispravke ponovo pokreni obavezne pregledače
   (paralelno), da svi vide i odobre **isto** stanje (`tools/review-ok.sh <ime>`).
5. Najviše **3 kruga**. Ako se ni tada ne slože, ili isti nalaz agent na slabijem modelu dvaput
   ne reši: stani. Prvo probaj jači model (`opus`) umesto trećeg kruga; ako ni to ne pomogne, iznesi
   Stefanu neslaganje sa oba stava (`request_decision` ili poruka) i ne commit-uj.
6. **proveravac**: `tools/check.sh`. Pad vraća posao izvođaču (pa ponovo review, jer se kod menja).
7. **tester-emulator** ako izmena dira `GuardService`, `guard/`, `ui/` ili ekrane i `adb devices`
   vidi uređaj. Bez uređaja: u završnoj poruci napiši "nije provereno u emulatoru".
8. `tools/review-stanje.sh` mora reći "Sve je spremno", pa `git add` za sve izmene u kodu i commit.
   Hook blokira commit koda dok bilo šta fali i kaže tačno šta. Dalje: skill `izdanje`.

## Pravila

- Niko ne odobrava sopstvenu izmenu: pregledač koji je ispravio kod odobrava tek kad je neki drugi
  pregledač već odobrio to stanje (skripta to proverava).
- Oznake u `.git/cuvar-*-ok` pišu samo skripte; hook ne da glavnom agentu ni izvođaču da pokrenu
  `review-ok`/`emulator-ok`, niti da direktno pišu oznake.
- Svaka izmena koda posle odobrenja poništava odobrenja i check (porede se samo putanje koda;
  izmena `.md` ne poništava).
- Nalaz se ne briše, samo mu se menja `Status:`.
- U završnoj poruci: šta je urađeno, ko je šta pregledao, šta je odbijeno i zašto, da li je
  provereno u emulatoru i na telefonu.

## Obim pregleda (iz `tools/oznake.sh`, `obim_pregleda`)

| Izmena dira | Obavezni |
|---|---|
| `rules/`, `data/` (`Store`) | pravila + bezbednosti |
| `GuardService`, `guard/` | sva četiri + emulator |
| `web/`, `unlock/` | sva četiri |
| `ui/`, aktivnosti | prikaza + stabilnosti + emulator |
| `i18n/`, `en.tsv` | prikaza + stabilnosti |
| samo `tests/` | pravila |
| ostalo (manifest, gradle, `CuvarApp`) | sva četiri |
