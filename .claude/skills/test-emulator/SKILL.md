---
name: test-emulator
description: Instaliraj Čuvar na emulator ili telefon preko adb-a, uključi Pristupačnost i proveri blokadu. Koristi samo lokalno (u oblaku nema emulatora).
---

# Provera u emulatoru

Radi samo na računaru sa Android SDK-om i pokrenutim emulatorom (API 34/35)
ili telefonom sa uključenim USB debagovanjem.

1. `tools/check.sh` (napravi `app/build/outputs/apk/debug/app-debug.apk`).
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk` (paket `com.cuvar.app.debug`).
3. Pristupačnost i pristup korišćenju bez kuckanja po podešavanjima:
   ```
   adb shell settings put secure enabled_accessibility_services com.cuvar.app.debug/com.cuvar.app.GuardService
   adb shell settings put secure accessibility_enabled 1
   adb shell appops set com.cuvar.app.debug GET_USAGE_STATS allow
   ```
4. Šta je na ekranu, jeftino (tekst umesto slike):
   `adb shell uiautomator dump /sdcard/u.xml >/dev/null && adb shell cat /sdcard/u.xml | grep -o 'text="[^"]*"' | grep -v 'text=""'`
   Ekran blokade je prozor Pristupačnosti i ne vidi se uvek u dump-u: tada
   `adb exec-out screencap -p > /tmp/x.png` (slika košta ~1.500 tokena, koristi retko).
5. Tap: `adb shell input tap X Y`, nazad: `adb shell input keyevent BACK`,
   početni ekran: `adb shell input keyevent HOME`.
6. Padovi: `adb logcat -d | grep -E "AndroidRuntime|CuvarGuard" | tail -30`.
7. Reset: `adb shell pm clear com.cuvar.app.debug`. Popuštanje pravila važi tek
   od 06:00 sutra, to je namerno.

Kad radi agent `tester-emulator`: ne pomera sat da bi prošao scenarije sa vremenom (takve
upisuje kao neproverene), rezultat piše u `.review/emulator.md` sa poslednjim redom
`Rezultat: prošlo` ili `Rezultat: nije prošlo`, i samo posle prolaska pokreće `tools/emulator-ok.sh`.

Detaljni scenariji (limiti, noć, režimi, pitalice): `tests/README.md`, čitaj samo
odeljak koji ti treba (`grep -n '^## ' tests/README.md`).
