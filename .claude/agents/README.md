# Postupak pregleda (za sve pregled-* agente)

Ovaj fajl nije agent; čitaju ga pregledači. Ceo tok rada: skill `rad-sa-agentima`.

## Faza 1: nalazi (pregledači rade paralelno)

1. Pročitaj `.review/zadatak.md` i sve `.review/nalazi/*.md` (i tuđe).
2. Pogledaj izmenu: `git diff HEAD` i nove fajlove (`git status --short`). Velike fajlove čitaj po opsegu redova.
3. Upisuj samo u svoj fajl `.review/nalazi/<tvoje-ime>.md`. U ovoj fazi **ne menjaš kod** (drugi pregledači čitaju isti diff).
4. Na tuđe nalaze iz svoje oblasti odgovori u svom fajlu (`Odgovor na N3 (pregled-pravila): slažem se / ne slažem se, jer ...`).
   Tuđi fajl ne menjaš.

Format nalaza (redni broj = prefiks agenta + broj, npr. `PR1`, `BZ2`, `ST1`, `PK1`):

```
## PR1 · pregled-pravila · kritično
Fajl: app/src/main/java/com/cuvar/app/data/Store.java:412
Problem: popuštanje dnevnog limita važi odmah, ne od 06:00.
Posledica za korisnika: podigne limit u 23:00 i odmah nastavi.
Predlog: kroz pendingChanges().
Status: otvoreno
Promenio: pregled-pravila
```

Ozbiljnost: `kritično` (zaključan bez izlaza, rupa u pravilima, pad procesa, curenje privatnosti), `važno`, `sitno`.
Ne prijavljuješ ukus. Nalaz bez fajla, reda i posledice za korisnika se ne piše.
Ako nemaš nalaza, fajl ima jedan red: `Nema nalaza.`

## Faza 2: ispravka (glavni agent pokreće jednog po jednog)

- Smeš sam da ispraviš svoj nalaz: promeni kod, pa u nalazu `Status: ispravljeno`, `Promenio: <ti>` i šta si uradio.
  Tu ispravku zatim pregleda drugi agent; ti je ne odobravaš prvi (`tools/review-ok.sh` to odbija).
- Nalaz koji odbijaš: `Status: odbijeno: <razlog>`. Nalaz se nikad ne briše.
- Kad se ne slažeš sa drugim pregledačem, napiši oba stava u svom fajlu; glavni agent odlučuje ili pita Stefana.

## Odobrenje

Kad u tvojoj oblasti nema otvorenih nalaza i stanje koda ti je prihvatljivo: `tools/review-ok.sh <tvoje-ime>`.
Skripta odbija dok bilo koji nalaz ima `Status: otvoreno`. Svaka kasnija izmena koda poništava odobrenje.

Vrati glavnom agentu: broj nalaza po ozbiljnosti, da li si odobrio, i neslaganja.
