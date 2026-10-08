# Vremenski režim

Automatska provera svih minuta u danu za noćni i dnevni period, prazni period i granice u ponoć, kao i za više režima (svaki sa svojim periodom, aplikacijama i sajtovima, uključujući period preko ponoći i isključen režim):

```powershell
javac -encoding UTF-8 -d .review/schedule app/src/main/java/com/cuvar/app/DailySchedule.java app/src/main/java/com/cuvar/app/DailyCode.java tests/com/cuvar/app/DailyScheduleTest.java
java -cp .review/schedule com.cuvar.app.DailyScheduleTest
```

Provera na Android telefonu:

1. Na telefonu sa starom verzijom (jedan režim) podesi period, aplikaciju i sajt, pa instaliraj novu verziju: u Vremenskim režimima postoji „Režim 1“ sa istim periodom, stanjem (uključen/isključen), aplikacijama i sajtovima. Aplikacija koja je bila samo u režimu ne pojavljuje se kao pravilo u ekranu Aplikacije.
2. Uključi Čuvara u Pristupačnosti. Otvori Vremenski režimi na početnom ekranu i dodaj dva režima, npr. jedan koji uključuje trenutno vreme i jedan koji ga ne uključuje, svaki sa drugom aplikacijom i drugim sajtom.
3. Proveri da su aplikacija i sajt (uključujući poddomen) aktivnog režima blokirani, a da su aplikacija i sajt drugog režima dostupni. Ekran za blokadu navodi naziv i period režima koji blokira.
4. Proveri da prethodno PIN otključavanje aplikacije ne zaobilazi režim.
5. Dok je blokada na ekranu, sačekaj kraj perioda: blokada nestaje pri sledećoj proveri servisa (najkasnije približno 5 sekundi). Ostala PIN i dnevna pravila i dalje važe.
6. Promeni naziv i period režima, isključi ga i ponovo uključi: izbor aplikacija, domena i perioda je sačuvan. Proveri i nakon ponovnog pokretanja aplikacije.
7. Ukloni aplikaciju ili sajt iz režima i proveri da se na njih više ne primenjuje taj režim. Obriši režim i proveri da njegove aplikacije i sajtovi više nisu blokirani (osim ako su u drugom aktivnom režimu).
8. Proveri noćni period 21:00–09:00 i odbijanje jednakog početka i kraja.

Automatske provere perioda pokreću se i u APK workflow-u. Provere na telefonu zahtevaju uređaj ili emulator sa aktivnim servisom.

# Pitanja pre otključavanja

Automatska provera banke pitanja (svaki red ispravno zapisan, bez duplikata, tačan odgovor ponuđen) i 20.000 nasumičnih pitanja (računica tačna, rimski brojevi, bez ponavljanja među poslednjih 40):

```powershell
javac -encoding UTF-8 -d .review/quiz app/src/main/java/com/cuvar/app/Quiz.java app/src/main/java/com/cuvar/app/QuizBank.java tests/com/cuvar/app/QuizTest.java
java -cp .review/quiz com.cuvar.app.QuizTest
```

Nova pitanja se dodaju u `QuizBank.java`, jedan red po pitanju: `Pitanje|tačan|netačan|netačan|netačan` ili `Pitanje|broj` kad se odgovor kuca.

Provera na Android telefonu:

1. Zaključaj aplikaciju PIN-om i otvori je: prvo se pojavljuje pitanje, pa tek posle tačnog odgovora PIN.
2. Kod isteklog limita ili blokiranog sajta: „Ipak želim da otključam“, pa „Jesi li siguran?“, pa pitanje, pa PIN.
3. Tokom pauze posle otključavanja: „Hitno otključavanje“ prvo traži odgovor, pa PIN.
4. Pogrešan odgovor pokazuje tačan odgovor i novo pitanje; isto pitanje se ne vraća odmah.
5. Pitanja sa brojem se kucaju na tastaturi (vide se cifre), ostala imaju četiri ponuđena odgovora.
6. Vremenski režim i dalje nema otključavanja.

# Radno vreme i dnevna šifra

Automatska provera (u istom testu kao vremenski režimi): dani u nedelji, period preko ponoći koji pripada danu u kome počinje, i dnevna šifra (6 cifara, menja se u 17:00, drugačija za svaki dan i svaki ključ).

Provera na Android telefonu:

1. U Vremenskim režimima dodaj „Radno vreme“ i izaberi aplikaciju. Svakog dana, i vikendom, od 09:00 do 17:00 aplikacija je blokirana bez ikakvog otključavanja.
2. Dok režim traje, pokušaj da ga isključiš, skratiš, promeniš dane, ukloniš aplikaciju ili obrišeš režim: Čuvar to odbija. Dodavanje aplikacija radi.
3. Dok režim traje, na početnom ekranu kartica „Dnevna šifra“ piše „Skrivena“. Posle 17:00 prikazuje šifru od 6 cifara.
4. Posle 17:00 i pre 09:00 aplikacija traži pitanje pa dnevnu šifru; stalni PIN je ne otključava. Posle otključavanja važe 5 minuta i pauza od sat vremena.
5. Pomeri sat telefona napred na 17:01 tokom režima: blokada ostaje, a šifra se ne menja. Posle restarta telefona sat se ponovo čita, ali vraćanje sata unazad nema efekta.
