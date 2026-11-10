#!/usr/bin/env bash
# Pregledač upisuje da je odobrio trenutno stanje koda. Pokreće ga samo pregled-* agent
# (hook u .claude/settings.json ne da izvođaču ni glavnom agentu).
# Upotreba: tools/review-ok.sh <pregled-pravila|pregled-bezbednosti|pregled-stabilnosti|pregled-prikaza>
set -uo pipefail
cd "$(dirname "$0")/.."
. tools/oznake.sh
ime="${1:-}"
case "$ime" in
  pregled-pravila|pregled-bezbednosti|pregled-stabilnosti|pregled-prikaza) ;;
  *) echo "Upotreba: tools/review-ok.sh <ime pregledača>"; exit 1 ;;
esac
[ -f .review/zadatak.md ] || { echo "ODBIJENO: nema .review/zadatak.md (glavni agent ga piše pre review-a)."; exit 1; }
[ -f ".review/nalazi/$ime.md" ] || { echo "ODBIJENO: nema .review/nalazi/$ime.md. Upiši nalaze ili red 'Nema nalaza.' pa ponovi."; exit 1; }
otv=$(otvoreni_nalazi)
if [ -n "$otv" ]; then
  echo "ODBIJENO: postoje otvoreni nalazi u:"; echo "$otv" | sed 's/^/  /'
  echo "Svaki nalaz mora biti 'ispravljeno' ili 'odbijeno' (uz razlog) pre odobrenja."; exit 1
fi
tree=$(radno_stablo)
f=$(oznaka review)
# Niko ne odobrava sopstvenu izmenu: ako je poslednju izmenu koda napravio ovaj pregledač,
# prvo je mora odobriti neko drugi.
log=$(git rev-parse --git-path cuvar-izmene.log)
poslednji=$(tail -1 "$log" 2>/dev/null | cut -d' ' -f1)
if [ "$poslednji" = "$ime" ]; then
  drugi=0
  while read -r t ko; do
    [ "$ko" != "$ime" ] && isti_kod "$t" "$tree" && drugi=1
  done < <(cat "$f" 2>/dev/null)
  if [ $drugi = 0 ]; then
    echo "ODBIJENO: poslednju izmenu koda je napravio $ime. Prvo je pregleda i odobri drugi pregledač."; exit 1
  fi
fi
echo "$tree $ime" >> "$f"
echo "Odobreno: $ime za stablo ${tree:0:10}."
nedostaje=""
for p in $(obim_pregleda | grep -v tester-emulator); do
  ok=0
  while read -r t ko; do [ "$ko" = "$p" ] && isti_kod "$t" "$tree" && ok=1; done < "$f"
  [ $ok = 1 ] || nedostaje="$nedostaje $p"
done
[ -n "$nedostaje" ] && echo "Još treba odobrenje:$nedostaje" || echo "Svi obavezni pregledači su odobrili ovo stanje."
exit 0
