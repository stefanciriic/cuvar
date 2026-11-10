#!/usr/bin/env bash
# Potrošnja Claude Code sesije po agentu i modelu: tokeni, cena po API cenovniku, kontekst.
# Čita transkripte iz ~/.claude/projects/<projekat>/ (glavna sesija + subagents/).
# Ne treba jq; radi u Git Bash-u (Windows) i na Linuxu.
#
#   bash tools/potrosnja.sh            trenutna (najnovija) sesija ovog projekta
#   bash tools/potrosnja.sh -w [sek]   isto, uživo (osvežava na 3 s ili zadato)
#   bash tools/potrosnja.sh -d         sve današnje sesije projekta, zbirno
#   bash tools/potrosnja.sh -s <id>    određena sesija (početak id-a)
#   bash tools/potrosnja.sh --kratko <transcript.jsonl>   jedan red za status line
#
# Cena je procena po javnom API cenovniku. Na pretplati (Pro/Max) se ne plaća po tokenu,
# ali odnos među agentima i modelima je isti. Procenat limita pretplate dolazi samo iz
# status line-a (tools/statusline.sh ga upisuje u ~/.claude/potrosnja-limit.txt).

CFG="${CLAUDE_CONFIG_DIR:-$HOME/.claude}"
LIMIT_FAJL="$CFG/potrosnja-limit.txt"

projekat_dir() {
  local p
  p=$(pwd -W 2>/dev/null || pwd)
  printf '%s/projects/%s' "$CFG" "$(printf '%s' "$p" | sed 's/[^A-Za-z0-9]/-/g')"
}

# Sabira usage iz transkripata. Argumenti: parovi "lab=<oznaka>" "<fajl>".
# Izlaz: redovi "R<TAB>oznaka<TAB>model<TAB>poziva<TAB>ulaz<TAB>kes_cit<TAB>kes_upis<TAB>izlaz<TAB>usd<TAB>kontekst<TAB>pocetak<TAB>kraj"
saberi() {
  awk '
  function br(s, k,   r) {  # prvi broj iza "k": u stringu s
    if (match(s, "\"" k "\":[0-9]+")) { r = substr(s, RSTART, RLENGTH); sub(/.*:/, "", r); return r + 0 }
    return 0
  }
  function cena(m, prompt) {  # USD po milion tokena: PI ulaz, PO izlaz, PR čitanje keša
    PI = 0; PO = 0; PR = 0
    if (m ~ /fable-5-2|mythos-5-2/)      { PI = 8;  PO = 40; PR = 0.20 }
    else if (m ~ /fable-5-1|mythos-5-1/) { PI = 10; PO = 50; PR = 0.25 }
    else if (m ~ /fable-5|mythos-5/)     { PI = 10; PO = 50; PR = 1 }
    else if (m ~ /opus-5-5/)             { PI = 4;  PO = 20; PR = 0.20 }
    else if (m ~ /opus-4-1|opus-4-0|opus-4-2/) { PI = 15; PO = 75; PR = 1.5 }
    else if (m ~ /opus/)                 { PI = 5;  PO = 25; PR = 0.50 }
    else if (m ~ /sonnet-5-5/)           { PI = 2;  PO = 10; PR = 0.10 }
    else if (m ~ /sonnet-5/)             { PI = 2;  PO = 10; PR = 0.20 }
    else if (m ~ /sonnet/)               { PI = 3;  PO = 15; PR = 0.30 }
    else if (m ~ /haiku-5-5/)            { if (prompt > 100000) { PI = 0.5; PO = 2.5; PR = 0.05 } else { PI = 0.10; PO = 0.50; PR = 0.01 } }
    else if (m ~ /haiku/)                { PI = 1;  PO = 5;  PR = 0.10 }
  }
  /"usage":\{/ && /"model":"/ {
    s = $0
    if (!match(s, "\"model\":\"[^\"]*\"")) next
    m = substr(s, RSTART + 9, RLENGTH - 10); sub(/^claude-/, "", m)
    if (m == "<synthetic>") next
    id = ""
    if (match(s, "\"id\":\"msg_[^\"]*\"")) id = substr(s, RSTART + 6, RLENGTH - 7)
    else id = FILENAME ":" FNR
    ts = ""
    if (match(s, "\"timestamp\":\"[^\"]*\"")) ts = substr(s, RSTART + 13, RLENGTH - 14)
    match(s, "\"usage\":\\{"); u = substr(s, RSTART)
    k = lab SUBSEP id
    if (!(k in M)) { red[++n] = k }
    M[k] = m; IN[k] = br(u, "input_tokens"); OUT[k] = br(u, "output_tokens")
    CR[k] = br(u, "cache_read_input_tokens"); CW[k] = br(u, "cache_creation_input_tokens")
    C1[k] = br(u, "ephemeral_1h_input_tokens")
    LAB[k] = lab
    if (ts != "") { if (!(lab in T0) || ts < T0[lab]) T0[lab] = ts; if (ts > T1[lab]) T1[lab] = ts }
    POSL[lab] = k
  }
  END {
    for (i = 1; i <= n; i++) {
      k = red[i]; l = LAB[k]; m = M[k]
      prompt = IN[k] + CR[k] + CW[k]
      cena(m, prompt)
      c1 = C1[k]; if (c1 > CW[k]) c1 = CW[k]
      usd = (IN[k] * PI + CR[k] * PR + (CW[k] - c1) * PI * 1.25 + c1 * PI * 2 + OUT[k] * PO) / 1000000
      g = l SUBSEP m
      if (!(g in P)) { gr[++ng] = g; GL[g] = l; GM[g] = m }
      P[g]++; SI[g] += IN[k]; SR[g] += CR[k]; SW[g] += CW[k]; SO[g] += OUT[k]; SU[g] += usd
      if (PI == 0) NEP[m] = 1
    }
    for (j = 1; j <= ng; j++) {
      g = gr[j]; l = GL[g]; k = POSL[l]
      ctx = (M[k] == GM[g]) ? IN[k] + CR[k] + CW[k] : 0
      printf "R\t%s\t%s\t%d\t%d\t%d\t%d\t%d\t%.4f\t%d\t%s\t%s\n", l, GM[g], P[g], SI[g], SR[g], SW[g], SO[g], SU[g], ctx, T0[l], T1[l]
    }
    for (m in NEP) printf "N\t%s\n", m
  }' "$@"
}

# Parovi "lab=..." "fajl" za jednu sesiju: glavna + subagenti (ime iz .meta.json).
fajlovi_sesije() {
  local glavni="$1" dir id meta tip st sad mt
  dir="${glavni%.jsonl}"
  ARGS=("lab=glavna" "$glavni")
  STANJE=()
  sad=$(date +%s)
  for f in "$dir"/subagents/agent-*.jsonl; do
    [ -f "$f" ] || continue
    id=${f##*/agent-}; id=${id%.jsonl}
    meta="${f%.jsonl}.meta.json"; tip=agent
    if [ -f "$meta" ]; then
      tip=$(grep -o '"agentType":"[^"]*"' "$meta" | head -1 | sed 's/.*:"//; s/"$//')
      [ -n "$tip" ] || tip=agent
    fi
    mt=$(stat -c %Y "$f" 2>/dev/null || echo 0)
    if [ -f "$meta" ] && grep -q '"finished":true' "$meta"; then st=gotov
    elif [ $((sad - mt)) -lt 120 ]; then st=radi
    else st=stao; fi
    ARGS+=("lab=$tip#${id:0:6}" "$f")
    STANJE+=("$tip#${id:0:6}=$st")
  done
}

k() { awk -v n="$1" 'BEGIN { if (n >= 1000000) printf "%.1fM", n / 1000000; else if (n >= 1000) printf "%.0fK", n / 1000; else printf "%d", n }'; }

limit_red() {
  [ -f "$LIMIT_FAJL" ] || { echo "Limit pretplate: nema podatka (uključi tools/statusline.sh, vidi tools/README-potrosnja.md)"; return; }
  # shellcheck disable=SC1090
  local five="" five_reset="" seven="" seven_reset="" kad="" r1="" r2=""
  . "$LIMIT_FAJL"
  [ -n "$five" ] || { echo "Limit pretplate: status line ga ne dobija (samo Pro/Max, posle prvog odgovora)"; return; }
  [ -n "$five_reset" ] && r1=" (reset $(date -d "@$five_reset" +%H:%M 2>/dev/null))"
  [ -n "$seven_reset" ] && r2=" (reset $(date -d "@$seven_reset" '+%a %H:%M' 2>/dev/null))"
  echo "Limit pretplate: 5 sati ${five}%${r1} · 7 dana ${seven:-?}%${r2} · stanje od $(date -d "@$kad" +%H:%M:%S 2>/dev/null)"
}

prikaz_sesije() {
  local glavni="$1" sid izlaz prozor="" cw=""
  sid=$(basename "$glavni" .jsonl)
  fajlovi_sesije "$glavni"
  izlaz=$(saberi "${ARGS[@]}")
  if [ -f "$LIMIT_FAJL" ]; then
    # shellcheck disable=SC1090
    . "$LIMIT_FAJL"; [ "$session" = "$sid" ] && prozor="$ctx_size"
  fi
  echo "Sesija ${sid:0:8} · $(date +%H:%M:%S)"
  limit_red
  echo
  printf '%s\n' "$izlaz" | awk -F'\t' -v prozor="$prozor" -v stanja="${STANJE[*]}" '
  function k(n) { if (n >= 1000000) return sprintf("%.1fM", n / 1e6); if (n >= 1000) return sprintf("%.0fK", n / 1e3); return n "" }
  function hm(t) { return substr(t, 12, 5) }
  BEGIN { ns = split(stanja, a, " "); for (i = 1; i <= ns; i++) { split(a[i], b, "="); ST[b[1]] = b[2] } }
  $1 == "N" { nep = nep " " $2; next }
  $1 == "R" {
    r++; L[r] = $2; Mo[r] = $3; P[r] = $4; I[r] = $5; CR[r] = $6; CW[r] = $7; O[r] = $8; U[r] = $9; C[r] = $10; T0[r] = $11; T1[r] = $12
    tot += $9; MU[$3] += $9; MT[$3] += $5 + $6 + $7 + $8; if (!($3 in MP)) mo[++nm] = $3; MP[$3] += $4
  }
  END {
    printf "%-26s %-11s %5s %7s %7s %7s %6s %8s %5s %8s %s\n", "AGENT", "MODEL", "POZIV", "ULAZ", "KES-CIT", "KES-UPS", "IZLAZ", "$ API", "UDEO", "KONTEKST", "VREME"
    for (i = 1; i <= r; i++) {
      ctx = (C[i] > 0) ? k(C[i]) : "-"
      if (L[i] == "glavna" && prozor > 0 && C[i] > 0) ctx = ctx " " int(C[i] * 100 / prozor) "%"
      st = (L[i] in ST) ? ST[L[i]] : ""
      printf "%-26s %-11s %5d %7s %7s %7s %6s %8.2f %4.0f%% %8s %s-%s %s\n", substr(L[i], 1, 26), substr(Mo[i], 1, 11), P[i], k(I[i]), k(CR[i]), k(CW[i]), k(O[i]), U[i], (tot > 0 ? U[i] * 100 / tot : 0), ctx, hm(T0[i]), hm(T1[i]), st
    }
    print ""
    print "PO MODELU"
    for (j = 1; j <= nm; j++) printf "  %-12s %5d poziva  %8s tokena  $%7.2f  %3.0f%%\n", mo[j], MP[mo[j]], k(MT[mo[j]]), MU[mo[j]], (tot > 0 ? MU[mo[j]] * 100 / tot : 0)
    printf "UKUPNO $%.2f po API cenovniku (na pretplati se ne plaća po tokenu; gleda se limit gore)\n", tot
    if (nep != "") print "Nepoznata cena za:" nep " (dodaj u tools/potrosnja.sh)"
    print "KONTEKST = veličina poslednjeg zahteva tog agenta (ulaz + keš)."
  }'
}

kratko() {  # jedan red za status line: ukupno, deo agenata, koliko agenata radi
  local glavni="$1"
  fajlovi_sesije "$glavni"
  saberi "${ARGS[@]}" | awk -F'\t' -v stanja="${STANJE[*]}" '
    $1 == "R" { t += $9; if ($2 != "glavna") a += $9 }
    END { n = gsub(/=radi/, "", stanja); printf "%.2f\t%.2f\t%d\n", t, a, n }'
}

dan() {
  local dir danas f sid
  dir=$(projekat_dir); danas=$(date +%Y-%m-%d)
  echo "Današnje sesije ($danas) · $dir"
  limit_red; echo
  printf '%-10s %-6s %8s %8s %s\n' SESIJA POCELA '$ API' AGENTI 'PO MODELU'
  for f in $(ls -t "$dir"/*.jsonl 2>/dev/null); do
    [ "$(date -r "$f" +%Y-%m-%d)" = "$danas" ] || continue
    sid=$(basename "$f" .jsonl)
    fajlovi_sesije "$f"
    saberi "${ARGS[@]}" | awk -F'\t' -v sid="${sid:0:8}" '
      $1 == "R" { t += $9; if ($2 != "glavna") a += $9; MU[$3] += $9; if ($11 != "" && (p == "" || $11 < p)) p = $11 }
      END { s = ""; for (m in MU) s = s sprintf("%s $%.2f  ", m, MU[m]); printf "%-10s %-6s %8.2f %8.2f %s\n", sid, substr(p, 12, 5), t, a, s; }'
  done
}

nadji_sesiju() {
  local dir="$1" pref="$2" f
  if [ -n "$pref" ]; then
    f=$(ls -t "$dir"/"$pref"*.jsonl "$CFG"/projects/*/"$pref"*.jsonl 2>/dev/null | head -1)
  else
    f=$(ls -t "$dir"/*.jsonl 2>/dev/null | head -1)
    # Rezerva: najnovija sesija bilo kog projekta čiji naziv sadrži "cuvar".
    [ -n "$f" ] || f=$(ls -t "$CFG"/projects/*cuvar*/*.jsonl 2>/dev/null | head -1)
  fi
  printf '%s' "$f"
}

case "$1" in
  --kratko) [ -f "$2" ] && kratko "$2"; exit 0 ;;
  -d) dan; exit 0 ;;
esac

uzivo=0; pauza=3; pref=""
while [ $# -gt 0 ]; do
  case "$1" in
    -w) uzivo=1; case "$2" in [0-9]*) pauza=$2; shift ;; esac ;;
    -s) pref=$2; shift ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
  esac
  shift
done

dir=$(projekat_dir)
if [ "$uzivo" = 1 ]; then
  while :; do
    f=$(nadji_sesiju "$dir" "$pref")
    if [ -n "$f" ]; then out=$(prikaz_sesije "$f"); else out="Nema transkripta u $dir"; fi
    printf '\033[H\033[2J%s\n\n(Ctrl+C za kraj, osvežava na %s s)\n' "$out" "$pauza"
    sleep "$pauza"
  done
fi
f=$(nadji_sesiju "$dir" "$pref")
[ -n "$f" ] || { echo "Nema transkripta u $dir (pokreni iz foldera projekta ili dodaj -s <id>)"; exit 1; }
prikaz_sesije "$f"
