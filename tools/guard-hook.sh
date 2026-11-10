#!/usr/bin/env bash
# Claude Code PreToolUse hook: mehanički čuva pravila iz CLAUDE.md.
# Izlaz 2 = alat se ne pokreće, a poruka ide Claude-u. Uključuje se u .claude/settings.json.
input=$(cat)
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
polje() { printf '%s' "$input" | grep -o "\"$1\" *: *\"[^\"]*\"" | head -1 | sed 's/.*"\([^"]*\)"$/\1/'; }
tool=$(polje tool_name)
# Ime subagenta koji je pozvao alat; prazno = glavni agent.
agent=$(polje agent_type)
blokiraj() { echo "Blokirano: $1" >&2; exit 2; }

case "$tool" in
  Edit|Write|MultiEdit|NotebookEdit)
    if printf '%s' "$input" | grep -q 'cuvar\.keystore'; then
      blokiraj "app/cuvar.keystore se ne menja bez Stefanovog izričitog dogovora (pogrešan ključ = nova verzija ne može preko stare)."
    fi
    if printf '%s' "$input" | grep -q 'AndroidManifest\.xml' && printf '%s' "$input" | grep -q 'android\.permission\.INTERNET'; then
      blokiraj "Čuvar nema dozvolu za internet i ne sme da je dobije (privatnost, CLAUDE.md)."
    fi
    fajl=$(polje file_path | sed 's#\\\\#/#g')  # Windows: C:\\x -> C:/x
    case "$fajl" in
      */.git/*|.git/*) blokiraj "fajlovi u .git/ (oznake provere i review-a) se ne pišu ručno." ;;
    esac
    # Beleži ko je menjao kod, da pregledač ne bi sam odobrio svoju ispravku (tools/review-ok.sh).
    case "$fajl" in
      */app/*|app/*|*/tests/*|tests/*|*build.gradle|*settings.gradle|*gradle.properties|*/gradle/*)
        echo "${agent:-glavni} ${fajl##*/}" >> "$(git rev-parse --git-path cuvar-izmene.log 2>/dev/null || echo /dev/null)" ;;
    esac
    ;;
  Bash)
    if printf '%s' "$input" | grep -q 'cuvar\.keystore' && printf '%s' "$input" | grep -Eq '(rm|mv|git rm|keytool|>) '; then
      blokiraj "app/cuvar.keystore se ne briše i ne menja bez Stefanovog izričitog dogovora."
    fi
    # Oznake upisuju samo skripte, nikad komanda direktno.
    if printf '%s' "$input" | grep -Eq 'cuvar-(check|review|emulator)-ok|cuvar-izmene'; then
      blokiraj "oznake u .git/ (cuvar-*-ok) upisuju samo tools/check.sh, tools/review-ok.sh i tools/emulator-ok.sh."
    fi
    if printf '%s' "$input" | grep -Eq 'git[^;&|]* (commit-tree|update-ref)'; then
      blokiraj "commit-tree/update-ref zaobilaze proveru commita. Koristi git commit."
    fi
    # Pokretanje skripti (ne pominjanje, npr. git add tools/review-ok.sh).
    pokrece='(^|"|[;&|(] *|(bash|sh|powershell)( -File)? +)(\./)?tools/'
    if printf '%s' "$input" | grep -Eq "${pokrece}review-ok\."; then
      ime=$(printf '%s' "$input" | grep -o 'review-ok\.[a-z0-9]* *[a-z-]*' | head -1 | sed 's/.* //')
      case "$agent" in
        pregled-*) [ "$ime" = "$agent" ] || blokiraj "$agent može da odobri samo u svoje ime (tools/review-ok.sh $agent)." ;;
        *) blokiraj "review odobrava samo pregledač (pregled-*), ne ${agent:-glavni agent}. Pokreni obavezne pregledače." ;;
      esac
    fi
    if printf '%s' "$input" | grep -Eq "${pokrece}emulator-ok\." && [ "$agent" != "tester-emulator" ]; then
      blokiraj "oznaku emulatora upisuje samo agent tester-emulator, posle prolaska scenarija."
    fi

    # Commit: kod ide u commit samo posle review-a, check-a i (ako je emulator pokrenut) testera.
    # Hvata i "git -C dir commit", "git -c x=y commit", -a, --amend, --no-verify.
    if printf '%s' "$input" | grep -Eq '(^|[^a-z-])git( +-[Cc] +[^ ]+| +--?[a-zA-Z][a-zA-Z-]*(=[^ ]+)?)* +commit( |"|$)'; then
      . tools/oznake.sh
      cmd=$(printf '%s' "$input" | grep -o 'commit[^;&|]*' | head -1)
      dira=0
      git diff --cached --quiet HEAD -- $KOD 2>/dev/null || dira=1
      if printf '%s' "$cmd" | grep -Eq ' (-[a-zA-Z]*a[a-zA-Z]*|--all)( |"|$)'; then svi=1; else svi=0; fi
      [ $svi = 1 ] && ! git diff --quiet -- $KOD 2>/dev/null && dira=1
      printf '%s' "$cmd" | grep -Eq ' (app/|tests/|gradle|build\.gradle|settings\.gradle)' && dira=1
      if [ $dira = 1 ]; then
        # Commit mora da sadrži tačno pregledano stanje: sve izmene koda su u commitu.
        if [ -n "$(git ls-files -o --exclude-standard -- $KOD)" ] || { [ $svi = 0 ] && ! git diff --quiet -- $KOD; }; then
          blokiraj "commit mora da sadrži sve izmene u kodu (pregledano je celo stanje). Uradi git add za app/tests/gradle ili skloni nedovršeno, pa ponovi."
        fi
        sve=$(sta_fali)
        fali=$(printf '%s\n' "$sve" | grep -v '^upozorenje:' | grep .)
        if [ -n "$fali" ]; then
          {
            echo "Blokirano: commit koda bez završenog review-a. Fali:"
            printf '%s\n' "$fali" | sed 's/^/  - /'
            echo "Redom: 1) tools/review-stanje.sh pokaže obavezne pregledače; 2) pokreni ih paralelno nad git diff HEAD (samo nalazi);"
            echo "3) ispravke jedna po jedna, pa ponovni review dok svi ne odobre isto stanje (najviše 3 kruga);"
            echo "4) agent proveravac: tools/check.sh; 5) ako je emulator pokrenut i treba: agent tester-emulator; 6) ponovi commit."
            echo "Postupak: skill rad-sa-agentima."
          } >&2
          exit 2
        fi
        upoz=$(printf '%s\n' "$sve" | grep '^upozorenje:' | sed 's/^upozorenje: //')
        if [ -n "$upoz" ]; then
          # Ne blokira; poruka ide korisniku i agentu.
          printf '{"systemMessage":"%s","hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"%s"}}\n' "$upoz" "$upoz"
        fi
      fi
    fi

    if printf '%s' "$input" | grep -Eq 'git( -C [^ ]+)? push'; then
      paths="app tests build.gradle settings.gradle gradle.properties gradle"
      # Push bez izmena u kodu (dokumentacija, .claude, tools) ne pravi izdanje: provera nije potrebna.
      if git rev-parse -q --verify origin/main >/dev/null && [ -z "$(git diff --name-only origin/main HEAD -- $paths)" ]; then exit 0; fi
      tree=$(cat "$(git rev-parse --git-path cuvar-check-ok)" 2>/dev/null)
      if [ -z "$tree" ] || ! git diff --quiet "$tree" HEAD -- $paths 2>/dev/null; then
        echo "Blokirano: pre push-a pokreni tools/check.sh (Windows: powershell ./tools/check.ps1) na ovom kodu. Svaki push na main je novo izdanje." >&2; exit 2
      fi
    fi
    ;;
esac
exit 0
