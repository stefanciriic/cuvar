# Plan: zaštita od padova i grešaka

Cilj: nijedna neočekivana greška ne sme da ugasi zaštitu (`GuardService`) niti da ostavi korisnika
zaključanog bez izlaza. Plan je napravljen 31.10.2026 posle pregleda koda i preporuka sa interneta.

## 1. Zašto je ovo važno baš kod Čuvara

- Aplikacija radi u **jednom procesu**: ekrani, prozor blokade i `GuardService` dele isti proces.
  Neuhvaćena greška bilo gde (i na ekranu podešavanja) obara i servis.
- Kad servis Pristupačnosti padne više puta, Android ga označi kao "Crashed services" i ne pokreće ga
  ponovo dok korisnik ne uđe u podešavanja. Videli smo to na emulatoru. Tada zaštita ne radi, a
  korisnik to ne zna.
- Greška koja ne obori proces, ali zaustavi `Runnable` koji sam sebe ponavlja, je još gora: servis
  izgleda živ, a merenje vremena i limiti tiho prestaju.
- `GuardDiagnostics` danas piše samo u logcat. Na telefonu bez kabla ne vidimo ništa.

## 2. Pravila (best practice) koja usvajamo

Izvori su na dnu. Prilagođeno Čuvaru:

1. **Zaštitni zid samo na ulazima iz Androida.** `catch (Throwable)` je u Javi opasan jer guta i
   `Error`. AOSP stil ga dozvoljava samo kad se "održava živ" UI ili posao koji se ponavlja, uz
   komentar zašto. Kod nas su to: `onAccessibilityEvent`, `onServiceConnected`, `onReceive`,
   `Runnable` na `Handler`-u, klikovi u prozoru blokade i pozadinske niti. Unutar logike se hvataju
   samo konkretni izuzeci (`JSONException`, `NumberFormatException`, `IllegalStateException`...).
2. **Ponavljanje uvek u `finally`.** Svaki `Runnable` koji se sam ponovo zakazuje radi
   `h.postDelayed(this, ...)` u `finally`, da ga greška ne prekine zauvek.
3. **Bez praznog `catch`.** Svaki `catch` ili prijavi grešku (`GuardDiagnostics.report`), ili ima
   komentar zašto je ignorisanje bezbedno (na primer: "ikona nije obavezna").
4. **Provera na granici.** Sve što stiže od Androida (`event.getPackageName()`, `AccessibilityNodeInfo`,
   `Intent`, `UsageEvents`) i iz sačuvanog JSON-a proverava se čim uđe. Dalje se prosleđuju sigurne
   vrednosti (kao `BlockRules.Front` i `Window`).
5. **Prazan objekat umesto null.** Prazne liste i skupovi, `AppRule.NONE`, `""` gde ima smisla. `null`
   ostaje samo gde znači "nema" i tada se označi (vidi tačku 6).
6. **Označavanje null-a.** `androidx.annotation` je spoljna biblioteka, a Lint po dokumentaciji ne
   proverava nullness na CI-ju (samo Android Studio). Zato: javadoc "ili null" na svakoj metodi koja
   vraća null, a nova logika ide u `rules` sa testovima za null ulaze. Prava provera pri build-u dolazi
   tek sa Kotlinom (faza 6).
7. **Bezbedno stanje kad nešto pukne.** Ako `check()` padne, ne ostavljamo pogrešnu blokadu preko
   ekrana, a ne skidamo ni ispravnu tvrdu blokadu. Telefon, SMS i početni ekran uvek prolaze.
8. **Rad sa čvorovima Pristupačnosti.** Čvorovi i događaji se koriste samo na niti na kojoj su stigli.
   `refresh()` i `getChild()` mogu da bace grešku iz samog Androida, pa se čitanje stabla obavija
   lokalno, a ne samo u `check()`.
9. **Privatnost u dnevniku grešaka.** Beleži se samo mesto (naša klasa i red), vrsta izuzetka i broj
   ponavljanja. Nikad poruka izuzetka, adresa, paket ni tekst sa ekrana (pravilo iz CLAUDE.md).
10. **Ne gutati padove u razvoju.** U debug build-u `StrictMode` prijavljuje rad sa diskom na glavnoj
    niti, a dnevnik grešaka se vidi na ekranu.

## 3. Pronađene rupe

Prioritet: **P1** može da ugasi zaštitu, **P2** može da obori proces u retkim slučajevima, **P3** kvalitet.

| # | Prio | Mesto | Problem |
|---|---|---|---|
| R1 | P1 | `GuardService.java:150` `tick` | `updateWatched()` i `noteMusic()` nisu u `try`, a `postDelayed(this)` nije u `finally`. Jedna greška zaustavlja otkucaj zauvek: limiti i merenje vremena staju, servis izgleda živ. |
| R2 | P1 | `GuardService.java:409` `onAccessibilityEvent` | Bez zaštite. Greška u `kinds.quiet()`, `wantsContent()`, `activityOf.put()` ide pravo u Android i ruši proces. |
| R3 | P1 | `GuardService.java:507` `safeCheck` | `accounting.accountUntilNow(null)` i `noteMusic()` su pre `try` bloka. `safeCheck` zovu svi `Runnable`-i, pa greška tu ruši proces. |
| R4 | P1 | `GuardService.java:281` `onServiceConnected` | Početak (`Store.get`, `BrowserMonitor`, `GuardOverlayWindow`, overlay sadržaji) i kraj (`ForegroundApp`, `updateWatched`, prvo zakazivanje `tick`-a) nisu zaštićeni. Pad pri pokretanju dovodi do petlje padova i "Crashed services". |
| R5 | P1 | ceo proces | Nema `Thread.setDefaultUncaughtExceptionHandler`. Pad u bilo kom ekranu obara i servis, a ne ostaje nikakav trag. |
| R6 | P2 | `GuardService.java:218` `screenReceiver` | Bez `try`: `accountUntilNow`, `hideOverlay`, `store.flush`, `guardBeat`. (`packageReceiver` je zaštićen.) |
| R7 | P2 | `GuardService.java:474` `shutdown` | `accountUntilNow` i `hideOverlay` nisu zaštićeni. Pad u gašenju sprečava `store.flush()`, pa se gubi poslednje izmereno vreme. |
| R8 | P2 | `throttled`, `recheckSoon`, `recheckLater`, `finishSiteExit` (`GuardService.java:185-215`) | Oslanjaju se na `safeCheck`. Postaju sigurni tek kad se popravi R3. |
| R9 | P2 | `guard/BlockOverlayContent`, `guard/PauseOverlayContent`, `unlock/PinPad`, `unlock/NumberGameView` | Klikovi u prozoru blokade rade u procesu servisa i nemaju zaštitu. Greška u pitalici ili PIN-u ruši servis, a korisnik ostaje bez izlaza dok se servis ne vrati. |
| R10 | P2 | `ui/Ui.java:418`, `unlock/Quiz.java:40,284` | `Integer.parseInt`/`Long.parseLong` nad unosom ili podacima pitalica: proveriti da je `NumberFormatException` uhvaćen. |
| R11 | P2 | `web/BrowserMonitor`, `web/BrowserWindows` | Čitanje stabla prozora; nemaju testove. Proveriti da je svaki `getChild`/`refresh`/`findAccessibilityNodeInfosByViewId` obavijen lokalno. |
| R12 | P2 | `guard/ForegroundApp.java:59` | `queryEvents` je u `try`. Proveriti da se `queryPending` vraća na `false` u `finally`, inače posle jedne greške više nikad ne pita. |
| R13 | P3 | `guard/GuardDiagnostics` | Samo logcat i bez brojanja. Treba trajni dnevnik (vidi faza 2). |
| R14 | P3 | 86 `catch` blokova sa `ignored`/praznim telom | Pregledati: dodati prijavu ili komentar zašto je bezbedno. |
| R15 | P3 | aktivnosti `onCreate` (`MainActivity`, `StatsActivity`, `RulesActivity`, `ScheduleActivity`, `FocusActivity`, `SubActivity`) | Pad pri crtanju ekrana obara i servis (isti proces). Ekran treba da pokaže poruku umesto pada. |
| R16 | P3 | `data/Store.java:498,858` i ostali `new JSONArray/JSONObject` | Većina je već odbrambena (`parse()` vraća prazan objekat). Proveriti preostala mesta i dodati test za oštećene podatke. |

Šta je već dobro: `check()` ide kroz `safeCheck`, `foregroundPoll` i `packageReceiver` su zaštićeni,
registracija receivera i `setServiceInfo` su u `try`, a `Store.parse()` vraća prazan objekat kad je JSON
oštećen.

## 4. Faze rada

Svaka faza je posebno izdanje: `tools/check.sh`, emulator, push, CI.

### Faza 1: zaštitni zid u servisu (R1–R4, R6–R8) — urađeno 31.10.2026

Provereno na emulatoru (API 37) sa namerno ubačenom greškom u svaki događaj i svaki otkucaj:
servis nije pao, otkucaj je nastavio, blokada se prikazivala, dialer je radio. Nije probano na telefonu.

- Pomoćna metoda u `GuardService`, na primer `guarded(String where, Runnable work)`: izvrši posao,
  uhvati `Throwable`, prijavi u `GuardDiagnostics`.
- `tick`: telo u `try`, `postDelayed` u `finally`.
- `onAccessibilityEvent`: celo telo u `try`.
- `safeCheck`: `accountUntilNow(null)` i `noteMusic` u zaštitu.
- `onServiceConnected`: svaki korak posebno zaštićen, tako da `tick` i `foregroundPoll` budu zakazani i
  kad neki korak pukne. Ako `Store.get` padne, servis ne sme da blokira ništa, ali mora da ostane živ.
- `screenReceiver` i `shutdown`: zaštita, `store.flush()` u `finally`.
- Provera: emulator (blokada radi, dialer prolazi), logcat bez padova.

### Faza 2: dnevnik grešaka koji ostaje na telefonu (R5, R13) — urađeno 31.10.2026

Urađeno: `guard/ErrorLog` (+ `ErrorLogTest`), `GuardDiagnostics` čuva u `cuvar_errors`, `CuvarApp` beleži
padove, prikaz i brisanje u "Opterećenje telefona". Na emulatoru: namerni pad je zapisan, servis se sam
vratio (nije "Crashed services"), brisanje radi. Nije probano na telefonu.

- `GuardDiagnostics` broji greške po mestu i čuva ih u `SharedPreferences`: mesto, vrsta izuzetka, prvi
  red steka iz `com.cuvar.app` (klasa:red), broj i poslednje vreme (`store.now()`). Najviše ~30 stavki.
  Bez poruke izuzetka, adresa i paketa.
- Logika brojanja i ograničenja ide u `rules` (na primer `ErrorLog`) sa testom.
- `Thread.setDefaultUncaughtExceptionHandler` u `GuardService.onCreate` (proces nema `Application`
  klasu): zapiše pad, pa prosledi prethodnom handler-u. Pad se ne guta, Android i dalje ugasi proces.
- Prikaz u podešavanjima (postojeća dijagnostika ili novi red "Greške"), uz dugme za brisanje. Novi
  tekst ide u `en.tsv`.

### Faza 3: prozor blokade i pitalice (R9, R10, R12)

- Zaštitni omotač za `OnClickListener` (na primer `Ui.safeClick`) i upotreba u overlay sadržajima,
  `PinPad` i `NumberGameView`.
- Ako omotač uhvati grešku u prozoru blokade, prozor ostaje, a dugme "Nazad/Početni ekran" mora uvek
  da radi.
- Unos brojeva preko jedne metode koja vraća podrazumevanu vrednost umesto izuzetka, sa testom.
- `ForegroundApp`: `queryPending.set(false)` u `finally`.

### Faza 4: čitanje pregledača i podataka (R11, R16)

- Pregled `BrowserMonitor` i `BrowserWindows`: lokalni `try` oko čitanja čvorova; ako čitanje ne
  uspe, adresa je "nepoznata" (postojeći put `addressUnresolved`), a ne pad.
- Izvući proverljive delove u `rules`/`web` sa testovima, uključujući null i prazne ulaze.
- Test za oštećen JSON: pomoćne funkcije parsiranja iz `Store`-a izvući u čistu klasu i testirati
  `"{"`, `"null"`, `"[]"` umesto objekta i slično.

### Faza 5: ekrani i čišćenje (R14, R15)

- `SubActivity`/aktivnosti: zaštita pri crtanju. Kad pukne, kratka poruka i povratak, ne pad procesa.
- Prolaz kroz svih 86 `catch` blokova: prijava ili komentar.
- Javadoc "ili null" na svakoj javnoj metodi koja vraća null.
- `StrictMode` samo u debug build-u.

### Faza 6 (po želji): Kotlin

Posle faza 1–5 aplikacija je zaštićena i u Javi. Kotlin bi dodao proveru null-a pri build-u.
Redosled je u ranijem dogovoru: build i testovi, pa `rules`, ekrani, `data` i `GuardService` na kraju.
Pre toga promeniti pravilo "bez Kotlina" u CLAUDE.md.

## 5. Testovi i provera

- Za svaku novu klasu u `rules`: test sa null i praznim ulazima, upisan u `tests/sources.txt`.
- Emulator posle svake faze (skill `test-emulator`): zaključana aplikacija pokazuje blokadu, dialer
  prolazi, `adb logcat | grep -E "AndroidRuntime|CuvarGuard"` je prazan.
- Namerno izazvana greška u debug build-u (privremeno, ne ide u commit): servis mora da preživi i
  greška mora da se vidi u dnevniku.
- Na pravom telefonu: bar faze 1 i 3, jer se tiču servisa i prozora blokade.

## Izvori

- [Android: Improve code inspection with annotations](https://developer.android.com/studio/write/annotations.html) (nullness, Lint ne proverava na CI-ju)
- [AOSP Java code style](https://source.android.com/setup/contribute/code-style) (ne hvatati generički `Exception`/`Throwable`, bez praznog `catch`)
- [Android vitals: ANR](https://developer.android.com/games/optimize/vitals/anr) i [StrictMode](https://developer.android.com/reference/android/os/StrictMode)
- [Effective Java, poglavlja 10–12 (sažetak)](https://dev.to/ashkanent/effective-java-chapters-10-to-12-41hk)
- [Uncaught exception handler na Androidu](https://gurubase.io/g/android/uncaughtexceptionhandler-catch-uncaught-exceptions-android) i [izuzeci u pozadinskim servisima](https://gurubase.io/g/android/safely-handle-log-exceptions-android-background-services)
- [B4X forum: rizik gutanja padova](https://www.b4x.com/android/forum/threads/uncaught-exceptions.59805/page-3?amp=1)
- [Google issue tracker: greška u AccessibilityNodeInfo.refresh](https://issuetracker.google.com/issues/68299168)
- [AccessibilityLib: događaji na drugoj niti](https://github.com/dandaner/AccessibilityLib)
- [Odbrambeno čitanje JSON-a iz SharedPreferences](https://codemia.io/knowledge-hub/path/save_arraylist_to_sharedpreferences)
