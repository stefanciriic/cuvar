# Potrošnja agenata (tokeni, cena, kontekst, limit)

Dve skripte, bez jq, rade u Git Bash-u i na Linuxu:

- `tools/statusline.sh` — red na dnu Claude Code-a: model, popunjenost konteksta,
  limit pretplate (5 sati i 7 dana, sa vremenom reseta), procena cene sesije i koliko od toga
  su subagenti, koliko subagenata trenutno radi.
- `tools/potrosnja.sh` — tabela po agentu i modelu: broj poziva, ulaz, čitanje i upis keša,
  izlaz, cena po API cenovniku, udeo u ukupnom, kontekst poslednjeg zahteva, vreme rada, stanje
  (`radi` / `gotov` / `stao`).

## Uključivanje (jednom, na laptopu)

U `C:\Users\Stefan\.claude\settings.json` (lični config, ne repo) dodaj ključ `statusLine`
pored onoga što već postoji:

```json
"statusLine": {
  "type": "command",
  "command": "bash C:/Users/Stefan/Desktop/cuvar/tools/statusline.sh",
  "refreshInterval": 5
}
```

`refreshInterval` osvežava red i dok glavna sesija čeka subagente. Promena važi odmah ili posle
ponovnog pokretanja `claude`.

## Praćenje uživo sa strane

U drugom terminalu (Git Bash), iz foldera projekta:

```bash
bash tools/potrosnja.sh -w        # trenutna sesija, osvežava na 3 s
bash tools/potrosnja.sh -w 10     # na 10 s
bash tools/potrosnja.sh -d        # sve današnje sesije, zbirno
bash tools/potrosnja.sh -s 73e5   # određena sesija (početak id-a)
```

## Šta brojevi znače

- **$ API** je procena po javnom cenovniku po milion tokena (cene u `saberi()` u
  `tools/potrosnja.sh`). Na Pro/Max pretplati se ne plaća po tokenu, ali je odnos među
  agentima i modelima isti, pa se vidi ko troši. Upis keša se računa 1,25× (5 min) ili
  2× (1 h) cene ulaza, čitanje keša prema modelu (Opus 5.5 0,20 $, Sonnet 5.5 0,10 $).
- **Limit pretplate** (`5h`, `7d`) je jedini pravi podatak o limitu. Claude Code ga daje
  samo status line-u (`rate_limits`), samo na Pro/Max i tek posle prvog odgovora u sesiji.
  Status line ga upisuje u `~/.claude/potrosnja-limit.txt`, pa ga i tabela prikazuje.
  Bez uključenog status line-a tabela ne zna limit.
- **Kontekst** u tabeli je veličina poslednjeg zahteva tog agenta (ulaz + keš). Procenat
  prozora se zna samo za glavnu sesiju (status line daje `context_window_size`).
- Ugrađene komande u sesiji: `/usage` (limit i cena sesije), `/context` (šta puni kontekst).

## Odakle podaci

Transkripti: `~/.claude/projects/<putanja projekta sa - umesto / \ :>/<sesija>.jsonl`,
subagenti u `<sesija>/subagents/agent-<id>.jsonl`, a tip agenta u `agent-<id>.meta.json`
(`agentType`). Svaki odgovor modela ima `message.model` i `message.usage`; isti odgovor se
upisuje u više redova, pa se broji jednom po `message.id`.
