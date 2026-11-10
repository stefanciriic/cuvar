#!/usr/bin/env bash
# Pregled za glavnog agenta: ko mora da pregleda izmenu i šta još fali do commita.
cd "$(dirname "$0")/.."
. tools/oznake.sh
obim=$(obim_pregleda)
[ -z "$obim" ] && { echo "Nema izmena u kodu: commit ne traži review."; exit 0; }
echo "Obavezno za ovu izmenu: $(echo $obim)"
sve=$(sta_fali)
echo "$sve" | grep '^upozorenje:'
fali=$(echo "$sve" | grep -v '^upozorenje:' | grep .)
[ -z "$fali" ] && { echo "Sve je spremno za commit."; exit 0; }
echo "Fali:"; echo "$fali" | sed 's/^/  - /'; exit 1
