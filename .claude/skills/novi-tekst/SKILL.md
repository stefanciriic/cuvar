---
name: novi-tekst
description: Dodaj ili izmeni tekst u interfejsu Čuvara uz engleski prevod u en.tsv, bez čitanja celog fajla. Koristi za svaki novi natpis, dugme, poruku ili Toast.
---

# Novi tekst u interfejsu

Tekst u kodu je srpski latinicom. Ćirilica (`Translit`) i engleski (`Lang`) se
prave pri prikazu.

1. Tekst kroz `Ui.text`/`Ui.Label` ide sam. Toast, hint i tekst na grafikonu
   obavij sa `Ui.cyr(...)`. Naziv aplikacije prijavi sa `Translit.keep(...)`.
2. **Ne čitaj ceo `app/src/main/assets/en.tsv`** (44 KB). Proveri samo da li red postoji:
   `grep -n -F "Tekst koji dodaješ" app/src/main/assets/en.tsv`
3. Dodaj red na kraj: srpski, TAB, engleski.
   `printf '%s\t%s\n' 'Srpski tekst' 'English text' >> app/src/main/assets/en.tsv`
   - Rečenica sklopljena od delova i vrednosti: svaki deo je poseban red, sa
     razmacima na krajevima tačno kao u kodu (`"Danas "` + broj).
   - Množina preko `Ui.count(n, "dan", "dana", "dana")`: svaki oblik ima svoj red
     ako već ne postoji.
4. `tools/check.sh --no-build`: `LangTest` pada ako bilo koji tekst nema red u en.tsv. Kad je
   engleski isti (`" min"`), red je i dalje potreban (srpski, TAB, isti tekst). Tekst koji se ne prikazuje
   (reči za prepoznavanje sistemskih ekrana, pitalice samo na srpskom) označi na kraju reda sa
   `// bez prevoda: razlog`. Spisak: `java -cp "${TMPDIR:-/tmp}/cuvar-check" com.cuvar.app.LangTest | grep 'Bez engleskog'`.
