# Release signing

[Srpski](SIGNING.md) · **English**

The debug build uses the Android development key and the package
`com.cuvar.app.debug`, so it can be installed next to the existing app.

The release APK is first built unsigned, then CI signs it with a rotation from the
old key to the new key. A build without the private keys stops and does not
publish a fallback signed with the public key.

GitHub Actions secrets for the APK:

- `LEGACY_KEYSTORE_B64`, `LEGACY_STORE_PASSWORD`, `LEGACY_KEY_PASSWORD`,
  `LEGACY_KEY_ALIAS` — the old key, only for building the certificate lineage;
- `RELEASE_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`,
  `RELEASE_KEY_ALIAS` — the new private key.

CI currently does not use the `UPLOAD_*` secrets (Google Play AAB); Play release is postponed.

Keystore files should not be in the repository. `app/cuvar.keystore` is a
historically compromised key and the workflow no longer uses it; it stays only as
a reference until the legacy content is moved into a GitHub secret and the
rotation is checked on a test phone.

Before publishing, check the rotated APK on a device with an existing install.
Android certificate lineage allows moving to the new key, but after the rotation
you should not go back to an APK signed only with the old key.

Contributors: you never need these keys. Build and test with `gradlew assembleDebug`
(debug key), and do not change `app/cuvar.keystore` or the signing setup in CI.
