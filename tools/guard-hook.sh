#!/usr/bin/env bash
# Claude Code PreToolUse hook: mehanički čuva pravila iz CLAUDE.md.
# Izlaz 2 = alat se ne pokreće, a poruka ide Claude-u. Uključuje se u .claude/settings.json.
input=$(cat)
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
tool=$(printf '%s' "$input" | grep -o '"tool_name" *: *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')

case "$tool" in
  Edit|Write|MultiEdit|NotebookEdit)
    if printf '%s' "$input" | grep -q 'cuvar\.keystore'; then
      echo "Blokirano: app/cuvar.keystore se ne menja bez Stefanovog izričitog dogovora (pogrešan ključ = nova verzija ne može preko stare)." >&2; exit 2
    fi
    if printf '%s' "$input" | grep -q 'AndroidManifest\.xml' && printf '%s' "$input" | grep -q 'android\.permission\.INTERNET'; then
      echo "Blokirano: Čuvar nema dozvolu za internet i ne sme da je dobije (privatnost, CLAUDE.md)." >&2; exit 2
    fi
    ;;
  Bash)
    if printf '%s' "$input" | grep -q 'cuvar\.keystore' && printf '%s' "$input" | grep -Eq '(rm|mv|git rm|keytool|>) '; then
      echo "Blokirano: app/cuvar.keystore se ne briše i ne menja bez Stefanovog izričitog dogovora." >&2; exit 2
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
