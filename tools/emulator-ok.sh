#!/usr/bin/env bash
# Tester upisuje da je izmena prošla u emulatoru. Pokreće ga samo agent tester-emulator.
# Traži .review/emulator.md sa redom "Rezultat: prošlo" i pokrenut uređaj.
set -uo pipefail
cd "$(dirname "$0")/.."
. tools/oznake.sh
ima_uredjaj || { echo "ODBIJENO: adb ne vidi pokrenut emulator ni telefon."; exit 1; }
grep -qi '^Rezultat: *prošlo' .review/emulator.md 2>/dev/null ||
  { echo "ODBIJENO: .review/emulator.md nema red 'Rezultat: prošlo'."; exit 1; }
radno_stablo > "$(oznaka emulator)"
echo "Emulator: oznaka upisana za ovo stanje koda."
