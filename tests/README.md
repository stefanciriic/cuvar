# Vremenski režim

Automatska provera svih minuta u danu za noćni i dnevni period, prazni period i granice u ponoć:

```powershell
javac -encoding UTF-8 -d .review/schedule app/src/main/java/com/cuvar/app/DailySchedule.java tests/com/cuvar/app/DailyScheduleTest.java
java -cp .review/schedule com.cuvar.app.DailyScheduleTest
```

Provera na Android telefonu:

1. Uključi Čuvara u Pristupačnosti. Otvori Vremenski režim na početnom ekranu.
2. Postavi period koji uključuje trenutno vreme, dodaj aplikaciju i sajt, pa uključi režim.
3. Proveri da su aplikacija i sajt (uključujući poddomen) blokirani; druge aplikacije i sajtovi ostaju dostupni.
4. Proveri da prethodno PIN otključavanje aplikacije ne zaobilazi režim.
5. Dok je blokada na ekranu, sačekaj kraj perioda: blokada nestaje pri sledećoj proveri servisa (najkasnije približno 5 sekundi). Ostala PIN i dnevna pravila i dalje važe.
6. Isključi režim, zatim ga ponovo uključi: izbor aplikacija, domena i perioda je sačuvan. Proveri i nakon ponovnog pokretanja aplikacije.
7. Ukloni aplikaciju ili sajt iz režima i proveri da se na njih više ne primenjuje režim. Dodavanje sajta u režim ne sme da ga blokira van perioda.
8. Proveri noćni period 21:00–09:00 i odbijanje jednakog početka i kraja.

Automatske provere perioda pokreću se i u APK workflow-u. Provere na telefonu zahtevaju uređaj ili emulator sa aktivnim servisom.
