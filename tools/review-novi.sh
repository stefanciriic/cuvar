#!/usr/bin/env bash
# Početak novog zadatka: stari .review fajlovi idu u .review/arhiva/<vreme>/, pravi se prazan zadatak.md.
# Upotreba: tools/review-novi.sh "Kratak naslov zadatka"
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p .review/nalazi
if ls .review/*.md .review/nalazi/*.md >/dev/null 2>&1; then
  a=".review/arhiva/$(date +%Y-%m-%d-%H%M%S)"; mkdir -p "$a"
  mv .review/*.md "$a"/ 2>/dev/null; [ -n "$(ls .review/nalazi)" ] && mv .review/nalazi "$a"/ && mkdir -p .review/nalazi
  echo "Arhivirano u $a"
fi
cat > .review/zadatak.md <<Z
# ${1:-Zadatak}

Šta se radi i zašto:

Šta korisnik treba da vidi posle izmene:

Granice (šta se ne dira):
Z
echo "Napravljen .review/zadatak.md. Obavezni pregledači se vide posle izmene: tools/review-ok.sh ih ispisuje."
