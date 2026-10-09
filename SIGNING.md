# Potpisivanje izdanja

Debug build koristi Android razvojni ključ i paket `com.cuvar.app.debug`, pa se
može instalirati pored postojeće aplikacije.

Release APK se prvo pravi unsigned, a zatim ga CI potpisuje rotacijom starog
ključa ka novom ključu. Build bez privatnih ključeva prekida se i ne objavljuje
fallback sa javnim ključem.

GitHub Actions secrets za APK:

- `LEGACY_KEYSTORE_B64`, `LEGACY_STORE_PASSWORD`, `LEGACY_KEY_PASSWORD`,
  `LEGACY_KEY_ALIAS` — stari ključ samo za pravljenje certificate lineage-a;
- `RELEASE_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`,
  `RELEASE_KEY_ALIAS` — novi privatni ključ.

Za Google Play AAB, opciono se dodaju `UPLOAD_KEYSTORE_B64`,
`UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_PASSWORD` i `UPLOAD_KEY_ALIAS`.

Keystore fajlovi ne treba da budu u repozitorijumu. `app/cuvar.keystore` je
istorijski kompromitovan ključ i workflow ga više ne koristi; ostaje samo kao
referenca dok se legacy sadržaj ne prebaci u GitHub secret i dok ne proveriš
rotaciju na testnom telefonu.

Pre objave proveri rotirani APK na uređaju sa postojećom instalacijom. Android
certificate lineage omogućava prelaz na novi ključ, ali se posle rotacije više
ne treba vraćati na APK potpisan samo starim ključem.
