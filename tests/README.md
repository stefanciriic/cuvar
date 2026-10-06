# Vremenski režim

Automatska provera svih minuta u danu za noćni i dnevni period, prazni period i granice u ponoć, kao i za više režima (svaki sa svojim periodom, aplikacijama i sajtovima, uključujući period preko ponoći i isključen režim):

```powershell
javac -encoding UTF-8 -d .review/schedule app/src/main/java/com/cuvar/app/DailySchedule.java tests/com/cuvar/app/DailyScheduleTest.java
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
