# Zajedničko za check, review i emulator (učitava se sa "source", ne pokreće se samo).
# Oznaka = stablo radnog direktorijuma koje je prošlo korak. Porede se samo putanje koda,
# pa izmena .md fajla ne poništava oznake, a izmena jednog reda koda poništava.
KOD="app tests build.gradle settings.gradle gradle.properties gradle"

# Stablo radnog direktorijuma, sa novim fajlovima (pravi index se ne dira).
radno_stablo() {
  local idx; idx=$(mktemp)
  cp "$(git rev-parse --git-path index)" "$idx" 2>/dev/null
  GIT_INDEX_FILE="$idx" git add -A . >/dev/null 2>&1
  GIT_INDEX_FILE="$idx" git write-tree 2>/dev/null
  rm -f "$idx"
}
oznaka() { git rev-parse --git-path "cuvar-$1-ok"; }
# Da li dva stabla imaju isti kod.
isti_kod() { [ -n "$1" ] && [ -n "$2" ] && git diff --quiet "$1" "$2" -- $KOD 2>/dev/null; }

# Ko mora da pregleda trenutnu izmenu koda (u odnosu na HEAD). Ispis: imena, jedno po redu;
# "tester-emulator" znači da izmena traži i proveru u emulatoru (ako je uređaj pokrenut).
obim_pregleda() {
  { git diff --name-only HEAD -- $KOD; git ls-files -o --exclude-standard -- $KOD; } 2>/dev/null | sort -u |
  while read -r f; do
    j=app/src/main/java/com/cuvar/app
    case "$f" in
      $j/GuardService.java|$j/guard/*)
        printf '%s\n' pregled-pravila pregled-bezbednosti pregled-stabilnosti pregled-prikaza tester-emulator ;;
      $j/web/*|$j/unlock/*)
        printf '%s\n' pregled-pravila pregled-bezbednosti pregled-stabilnosti pregled-prikaza ;;
      $j/rules/*|$j/data/*)
        printf '%s\n' pregled-pravila pregled-bezbednosti ;;
      $j/ui/*|$j/*Activity.java)
        printf '%s\n' pregled-prikaza pregled-stabilnosti tester-emulator ;;
      $j/i18n/*|app/src/main/assets/en.tsv)
        printf '%s\n' pregled-prikaza pregled-stabilnosti ;;
      tests/*)
        printf '%s\n' pregled-pravila ;;
      *)  # manifest, gradle, ostalo: nismo sigurni, pa sva četiri
        printf '%s\n' pregled-pravila pregled-bezbednosti pregled-stabilnosti pregled-prikaza ;;
    esac
  done | sort -u
}

# Pokrenut emulator ili telefon (adb može da ne bude u PATH-u u Git Bash-u).
ima_uredjaj() {
  local adb
  for adb in adb "${ANDROID_HOME:-}/platform-tools/adb" "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
             "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"; do
    if command -v "$adb" >/dev/null 2>&1 || [ -x "$adb" ]; then
      "$adb" devices 2>/dev/null | grep -q $'\tdevice$' && return 0
      return 1
    fi
  done
  return 1
}

# Nalazi koji još nisu rešeni (bilo koji pregledač).
otvoreni_nalazi() { grep -il '^Status: *otvoreno' .review/nalazi/*.md 2>/dev/null; }

# Šta još fali da bi trenutno stanje koda smelo u commit. Ispis: jedan red po koraku koji fali,
# prazno = sve je u redu. Red "upozorenje: ..." ne blokira (nema pokrenutog emulatora).
sta_fali() {
  local tree t ko p ok obim
  tree=$(radno_stablo)
  obim=$(obim_pregleda)
  [ -z "$obim" ] && return 0
  [ -n "$(otvoreni_nalazi)" ] && echo "nalazi: ima otvorenih nalaza u .review/nalazi/ (ispraviti ili odbiti uz razlog)"
  for p in $(echo "$obim" | grep -v tester-emulator); do
    ok=0
    while read -r t ko; do [ "$ko" = "$p" ] && isti_kod "$t" "$tree" && ok=1; done < <(cat "$(oznaka review)" 2>/dev/null)
    [ $ok = 1 ] || echo "review: $p nije odobrio ovo stanje koda"
  done
  isti_kod "$(cat "$(oznaka check)" 2>/dev/null)" "$tree" || echo "check: tools/check.sh nije prošao na ovom stanju koda"
  if echo "$obim" | grep -q tester-emulator; then
    if ima_uredjaj; then
      isti_kod "$(cat "$(oznaka emulator)" 2>/dev/null)" "$tree" || echo "emulator: tester-emulator nije potvrdio ovo stanje (tools/emulator-ok.sh)"
    else
      echo "upozorenje: izmena dira GuardService/guard/ui/ekrane, a emulator nije pokrenut. U završnoj poruci napiši: nije provereno u emulatoru."
    fi
  fi
}
