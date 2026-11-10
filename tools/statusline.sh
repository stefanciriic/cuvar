#!/usr/bin/env bash
# Status line za Claude Code: model, kontekst, limit pretplate (5 h / 7 dana), cena sesije i agenata.
# Uključuje se u ~/.claude/settings.json (vidi tools/README-potrosnja.md). Ne treba jq.
# Usput upisuje limit i veličinu konteksta u ~/.claude/potrosnja-limit.txt za tools/potrosnja.sh.

CFG="${CLAUDE_CONFIG_DIR:-$HOME/.claude}"
ulaz=$(cat)

# Ravna lista "putanja=vrednost" za sve skalare iz JSON-a (npr. rate_limits.five_hour.used_percentage=23.5).
polja=$(printf '%s' "$ulaz" | awk 'BEGIN { RS = "\001" } {
  s = $0; n = length(s); d = 0; i = 1
  while (i <= n) {
    c = substr(s, i, 1)
    if (c == "{") { d++; niz[d] = 0; kljuc[d] = ""; ocek[d] = 1; i++ }
    else if (c == "[") { d++; niz[d] = 1; ind[d] = 0; kljuc[d] = 0; i++ }
    else if (c == "}" || c == "]") { d--; i++ }
    else if (c == ",") { if (niz[d]) { ind[d]++; kljuc[d] = ind[d] } else ocek[d] = 1; i++ }
    else if (c == "\"") {
      j = i + 1; v = ""
      while (j <= n) { z = substr(s, j, 1); if (z == "\\") { v = v substr(s, j, 2); j += 2; continue } if (z == "\"") break; v = v z; j++ }
      i = j + 1
      if (!niz[d] && ocek[d]) { kljuc[d] = v; ocek[d] = 0 } else ispisi(v)
    }
    else if (c ~ /[-0-9tfn]/) { j = i; while (j <= n && substr(s, j, 1) !~ /[,}\] \t\r\n]/) j++; ispisi(substr(s, i, j - i)); i = j }
    else i++
  }
}
function ispisi(v,   p, k) { p = ""; for (k = 1; k <= d; k++) p = p (k > 1 ? "." : "") kljuc[k]; print p "=" v }')

uzmi() { printf '%s\n' "$polja" | grep -m1 "^$1=" | cut -d= -f2-; }

model=$(uzmi model.display_name)
sid=$(uzmi session_id)
tr_put=$(uzmi transcript_path | sed 's#\\\\#/#g')
ctx_pct=$(uzmi context_window.used_percentage)
ctx_size=$(uzmi context_window.context_window_size)
sesija_usd=$(uzmi cost.total_cost_usd)
five=$(uzmi rate_limits.five_hour.used_percentage)
five_reset=$(uzmi rate_limits.five_hour.resets_at)
seven=$(uzmi rate_limits.seven_day.used_percentage)
seven_reset=$(uzmi rate_limits.seven_day.resets_at)
agent=$(uzmi agent.name)

# Za tools/potrosnja.sh (uvek isti format: ime=vrednost, čita se sa ".").
# Kad ovaj ulaz nema limit (pre prvog odgovora), zadrži poslednji poznati.
if [ -z "$five" ] && [ -f "$CFG/potrosnja-limit.txt" ]; then
  five=$(sed -n 's/^five=//p' "$CFG/potrosnja-limit.txt"); five_reset=$(sed -n 's/^five_reset=//p' "$CFG/potrosnja-limit.txt")
  seven=$(sed -n 's/^seven=//p' "$CFG/potrosnja-limit.txt"); seven_reset=$(sed -n 's/^seven_reset=//p' "$CFG/potrosnja-limit.txt")
  # Prozor koji je prošao više ne važi.
  [ -n "$five_reset" ] && [ "$five_reset" -lt "$(date +%s)" ] && { five=""; five_reset=""; }
  [ -n "$seven_reset" ] && [ "$seven_reset" -lt "$(date +%s)" ] && { seven=""; seven_reset=""; }
fi
{
  echo "session=${sid}"; echo "ctx_size=${ctx_size}"; echo "kad=$(date +%s)"
  echo "five=${five%.*}"; echo "five_reset=${five_reset}"
  echo "seven=${seven%.*}"; echo "seven_reset=${seven_reset}"
} > "$CFG/potrosnja-limit.txt" 2>/dev/null

# Cena svih agenata iz transkripata, keširano 10 s da status line ostane brz.
agenti=""
if [ -n "$tr_put" ] && [ -f "$tr_put" ]; then
  kes="$CFG/potrosnja-kes-${sid:0:8}.txt"; sad=$(date +%s)
  mt=$(stat -c %Y "$kes" 2>/dev/null || echo 0)
  if [ $((sad - mt)) -ge 10 ]; then
    bash "$(dirname "$0")/potrosnja.sh" --kratko "$tr_put" > "$kes" 2>/dev/null
  fi
  IFS=$'\t' read -r ukupno deo_agenata rade < "$kes" 2>/dev/null
  if [ -n "$ukupno" ]; then
    agenti=" │ ~\$${ukupno} API"
    [ "$deo_agenata" != "0.00" ] && agenti="$agenti (agenti \$${deo_agenata})"
    [ "${rade:-0}" -gt 0 ] && agenti="$agenti · radi ${rade}"
  fi
fi
[ -z "$agenti" ] && [ -n "$sesija_usd" ] && agenti=$(printf ' │ ~$%.2f API' "$sesija_usd")

red="${model:-?}"
[ -n "$agent" ] && red="$red ($agent)"
[ -n "$ctx_pct" ] && [ "$ctx_pct" != "null" ] && red="$red │ kontekst ${ctx_pct%.*}%"
if [ -n "$five" ]; then
  red="$red │ 5h ${five%.*}%"
  [ -n "$five_reset" ] && red="$red do $(date -d "@$five_reset" +%H:%M 2>/dev/null)"
  [ -n "$seven" ] && red="$red · 7d ${seven%.*}%"
fi
printf '%s%s\n' "$red" "$agenti"
