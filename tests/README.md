# Pokretanje svih automatskih provera

Iz korena projekta, uz instaliranu Javu 11 ili noviju i PowerShell:

```powershell
./tests/run.ps1
```

Android SDK nije potreban za ove testove. Spisak čistih Java klasa je u
`tests/sources.txt`; skripta automatski pokreće sve `*Test.java` klase.
GitHub pri svakom push-u na `main` pokreće iste testove (`tests/sources.txt`),
pa tek onda pravi APK.

Regresione provere pokrivaju roditeljske domene, granice intervala korišćenja,
ponoć, letnje/zimsko računanje vremena, nedeljne unije rasporeda i nečitljivu
adresnu traku. Merenje zasnovano na događajima i njegovo povezivanje sa Androidom
treba dodatno proveriti na telefonu:

1. Koristi A četiri sekunde pa B jednu: vreme treba da pripadne odgovarajućoj
   aplikaciji, i da se sačuva ako odmah ugasiš ekran ili otvoriš blokiranu stavku.
2. Blokiraj `facebook.com`, a dodaj `m.facebook.com` sa 60 minuta: roditeljska
   blokada i dalje važi. Kada roditelj ima vremenski limit, korišćenje poddomena
   troši i roditeljski limit.
3. Bez globalne noćne blokade, izmeni neaktivan ponedeljni režim `00:00–01:00`
   u `23:00–02:00`: do sledećih 06:00 važe oba perioda. Proveri čuvanje posle
   ponovnog pokretanja i „Otkaži odložene promene“.
4. Posle brisanja/povećanja pravila lista pokazuje sadašnje ograničenje i
   posebno promenu od 06:00; obrisana stavka ostaje vidljiva dok blokada važi.
5. Ostavi početni ekran otvoren preko 17:00, 22:00 i ponoći: šifra i datum se
   osvežavaju bez zatvaranja otvorenog dijaloga.
6. Broj `2147483648`, negativan ili prazan unos ne sme sačuvati novo pravilo.
   Proveri dijaloge sa tastaturom/velikim fontom i stanje kvačice uz TalkBack.
7. U podeljenom ekranu promeni sajt u nefokusiranom pregledaču: njegova pravila
   i vreme sajta i dalje važe. Ukupno vreme telefona računa fokusiranu aplikaciju,
   tako da istovremeno prikazane aplikacije ne dupliraju ukupan zbir. Ukupni dnevni
   limit se troši jednom, i kad su aplikacija i sajt sa pravilom vidljivi istovremeno.
8. Podržan pregledač bez prethodno pročitane adrese prikazuje objašnjenje blokade
   kada postoje pravila za sajtove. Sakrivena traka tokom skrolovanja dugačke strane
   ili videa zadržava poslednji potvrđen domen istog prozora (čitanje članka duže od
   3 sekunde ne sme da zaključa); ovo je heuristika, ne prepoznavanje sadržaja.
9. Potroši otvaranja A, pređi u B pa brzo nazad u A: blokada ostaje. Ponovi sa
   nedovršenom pauzom pre otvaranja i sa već dozvoljenom sesijom.

Lokalna dijagnostika koristi tag `CuvarGuard` i beleži samo naziv operacije i
klasu greške (bez URL-a, naziva aplikacije, poruke izuzetka ili sadržaja ekrana).

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

Automatska provera (u istom testu kao vremenski režimi): dani u nedelji, period preko ponoći koji pripada danu u kome počinje, i dnevna šifra (4 cifre, menja se u 17:00, drugačija za svaki dan i svaki ključ).

Provera na Android telefonu:

1. U Vremenskim režimima dodaj „Radno vreme“ i izaberi aplikaciju. Svakog dana, i vikendom, od 09:00 do 17:00 aplikacija je blokirana bez ikakvog otključavanja.
2. Dok režim traje, pokušaj da ga isključiš, skratiš, promeniš dane, ukloniš aplikaciju ili obrišeš režim: Čuvar to odbija. Dodavanje aplikacija radi.
3. Dok režim traje, na početnom ekranu kartica „Dnevna šifra“ piše „Skrivena“. Posle 17:00 prikazuje šifru od 4 cifre.
4. Posle 17:00 i pre 09:00 aplikacija traži pitanje pa dnevnu šifru; stalni PIN je ne otključava. Posle otključavanja važe 5 minuta i pauza od sat vremena.
5. Pomeri sat telefona napred na 17:01 tokom režima: blokada ostaje, a šifra se ne menja. Posle restarta telefona sat se ponovo čita, ali vraćanje sata unazad nema efekta.

# Ukupni dnevni limit

Limit troši samo vreme u aplikacijama i na sajtovima koji imaju pravilo; mape, pozivi, poruke i ostalo bez pravila ga ne troše.

Automatska provera pravila (strožiji limit odmah, blaži ili isključen tek od sutra, potrošen limit, upozorenje 15 min ranije, vreme pod pravilima bez aplikacija bez pravila):

```powershell
javac -encoding UTF-8 -d .review/daylimit app/src/main/java/com/cuvar/app/DayLimit.java tests/com/cuvar/app/DayLimitTest.java
java -cp .review/daylimit com.cuvar.app.DayLimitTest
```

Provera na telefonu:

1. Koristi aplikaciju bez pravila (npr. mape) nekoliko minuta: na kartici Danas ukupno vreme raste, a red „Dnevni limit: … od …“ ostaje isti. Zatim koristi aplikaciju sa pravilom, pa sajt sa pravilom u pregledaču bez pravila: red raste za to vreme. Vreme na ekranu blokade se ne računa.
2. U „Ukupni dnevni limit“ izaberi vrednost manju od vremena iz tog reda: zaključane i ograničene aplikacije i sajtovi odmah prikazuju „Dnevni limit je potrošen“, bez PIN-a, šifre i hitnog otključavanja. Aplikacije bez pravila, pozivi i poruke rade.
3. Povećaj limit: dodaje se najviše 30 % (do 1 h), 20 % (2 h) ili 10 % (od 4 h), i drugi put istog dana dugme je sivo. Isključivanje piše da važi od sutra.
4. Postavi limit 15 min iznad vremena iz tog reda: stiže upozorenje (jednom dnevno).
5. Posle ponoći (po pouzdanom vremenu) blokada nestaje, a zakazana vrednost važi.
6. Nadogradnja sa verzije koja je brojala sve vreme na telefonu, na dan kad je limit potrošen uglavnom aplikacijama bez pravila: posle instalacije red pokazuje samo vreme aplikacija i sajtova sa pravilom, blokada nestaje i povećanje je opet moguće. Ako je i to vreme preko limita, blokada ostaje.

# Broj otvaranja i dugme „Zatvori“

1. U Aplikacijama izaberi aplikaciju i upiši „Najviše otvaranja dnevno“, npr. 2. Otvori je, izađi na početni ekran, otvori ponovo: radi. Treći put posle izlaska prikazuje „Otvaranja za danas su potrošena“, bez otključavanja do ponoći.
2. Izlazak na kratko (deljenje, izbor slike, manje od 15 s) i povratak u istu aplikaciju ne troši novo otvaranje.
3. Ekran blokade ima veliko narandžasto dugme „Zatvori“ (vodi na početni ekran), a od drugog pokušaja istog dana piše „Ovo ti je danas N. pokušaj.“
4. Povećanje broja otvaranja ili brisanje važi tek sutra od 06:00, smanjenje odmah.

# Rupe: podeljen ekran i Čuvar ugašen

1. Otvori zaključanu aplikaciju u podeljenom ekranu ili kao plutajući prozor, dok je druga aplikacija u fokusu: ekran blokade se pojavljuje.
2. Isključi Čuvara u Pristupačnosti na par minuta pa ga uključi: na kartici „Sada“ piše „Čuvar nije radio“ sa vremenom i razlogom.
3. Pokreni telefon u Safe Mode, pa normalno: piše da je telefon paljen bez Čuvara.

## Raspored u tri kartice i sistemske aplikacije

1. Dole se vide kartice Danas, Pravila i Statistika; tap menja ekran bez animacije.
2. Danas: kartice Sada, vreme danas i dnevna šifra. Zupčanik gore desno otvara Temu, Privatnost i verziju.
3. Pravila: „Za ceo telefon“ (ukupni limit, noćna blokada) i „Po aplikaciji i sajtu“.
4. Statistika kao kartica nema strelicu nazad; tap na Danas ili Pravila vraća na glavni ekran.
5. Otvori ekran biometrije ili drugu sistemsku aplikaciju bez ikonice: ne sme da se pojavi u vremenu danas ni u statistici, i ne ulazi u ukupni dnevni limit.

## Pauza od 6 sekundi

1. Otvori aplikaciju koja ima pravilo (limit, broj otvaranja ili režim van perioda): pojavi se „Zastani na trenutak“ sa vremenom i otvaranjima za danas.
2. „Nastavi“ se može tapnuti tek posle 6 s; tada se aplikacija vidi i tek tada se broji otvaranje.
3. „Zatvori“ vraća na početni ekran i ne broji otvaranje.
4. Kratak izlazak (deljenje, izbor fajla) i povratak u roku od 15 s ne traži novu pauzu.
5. Aplikacija bez pravila, telefon i poruke otvaraju se bez pauze. Posle otključavanja šifrom nema pauze.

## Najduže u komadu

1. U aplikaciji (Aplikacije ili Sva pravila) upiši „Najduže u komadu“ 2 min.
2. Koristi je bez prekida: posle 1 min stiže poruka „još minut u komadu“, a posle 2 min ekran „Vreme je za pauzu“ koji se ne otključava ni šifrom.
3. Izlazak na početni ekran i povratak pre isteka pauze ne prekida komad ni pauzu.
4. Posle 15 min pauze aplikacija se opet otvara i komad kreće od nule. Kartica Sada pokazuje koliko je pauze ostalo.
5. Smanjenje ili brisanje ovog ograničenja važi tek sutra od 06:00 (kartica „Od sutra“).

## Pregledač unutar aplikacije i kopije aplikacija

1. Stavi neki sajt na listu blokiranih. U Instagramu, Facebooku ili Messengeru otvori link ka tom sajtu: pojavi se blokada sajta sa dugmetom „Nazad“.
2. Otvori link ka sajtu koji nije na listi: otvara se normalno (vreme se računa sajtu).
3. Ako Čuvar u tom pregledaču ne vidi adresu, a postoje pravila za sajtove, pojavi se „Link je zaključan“ sa savetom da se link otvori u Chrome-u.
4. Aplikacija za kloniranje (Parallel Space, Dual Space, App Cloner...) zaključana je dok postoje pravila za aplikacije.
5. Kopija aplikacije sa drugim paketom (npr. „Instagram 2“) dobija ista pravila i isto vreme kao original.

## Brzi povratak ne skida blokadu

1. Aplikaciji A postavi 1 otvaranje dnevno, otvori je i izađi, pa je otvori ponovo: blokada „Otvaranja su potrošena“.
2. Pređi u drugu aplikaciju i za manje od 15 s vrati se u A: blokada ostaje.
3. Otvori aplikaciju sa pravilom, na pauzi od 6 s pređi u drugu aplikaciju i brzo se vrati: pauza se ponovo pojavljuje, a otvaranje se broji tek posle „Nastavi“.

## Aplikacije bez pravila se ne čitaju

1. Otvori Google mape (ili kalkulator, kalendar) bez pravila i pomeraj mapu, otvaraj donje panele: mape rade glatko.
2. Vreme u mapama se i dalje vidi u statistici i ne troši ukupni dnevni limit.
3. Iz mapa pređi u aplikaciju sa pravilom: blokada ili pauza se pojavi odmah.
4. Podeljen ekran mape + aplikacija sa pravilom: blokada se i dalje pojavljuje.

## Pristup korišćenju (koja je aplikacija napred, od Androida)

1. Na kartici Danas pojavi se „Dozvoli pristup korišćenju“; tapni „Otvori podešavanje“ i uključi Čuvar (radi i dok je zaštita od isključivanja uključena, dva minuta posle tapa).
2. Posle dozvole kartica nestaje. Mape, kamera i pozivi rade glatko; vreme u njima se i dalje vidi u statistici.
3. Iz mapa pređi u aplikaciju sa pravilom: blokada ili pauza se pojavi odmah.
4. Sa blokade pređi preko nedavnih aplikacija u mape: blokada nestane za najviše 2 s.
5. Novo pravilo za aplikaciju počne da važi za najviše 5 s.
6. Fokus režim: sve aplikacije se prate kao ranije.

## Pregledač ne zastaje

1. U Chrome-u skroluj dugačku stranicu: skrolovanje je glatko kao bez Čuvara.
2. Otvori blokiran sajt: blokada se pojavi za oko jednu sekundu.

## Blokada sajtova u svim aplikacijama (lokalni VPN samo za DNS)

1. Pravila → Za ceo telefon → „Blokada sajtova u svim aplikacijama“ → Uključi; prihvati zahtev za VPN. Gore se pojavi ključić.
2. Uvek blokiran sajt: ne otvara se ni u Chrome-u, ni u Firefox-u, ni preko linka u Instagramu/Messengeru („sajt nije dostupan“ ili Čuvarova blokada).
3. Ostali sajtovi, mape, video i pozivi rade normalnom brzinom.
4. Sajt sa limitom u minutima radi kao do sada (meri se i blokira posle limita).
5. Uz zaštitu od isključivanja: prozorčić „Prekini vezu“ iz obaveštenja o VPN-u se zatvara; „Otvori VPN podešavanja“ iz Čuvara dozvoljava ekran VPN-a dva minuta.
6. Uključi drugi VPN: u kartici Sada se pojavi „Čuvar nije radio · Blokada sajtova (VPN) nije radila“, a red u Pravilima kaže „Ne radi“.
7. Isključivanje važi tek od sledećih 06:00.
