# Cadence 2.6.0 verification

## Build and static checks

The release was built from the 2.6.0 release commit with JDK 17 and Android SDK 35. The following checks completed successfully:

```text
./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

The debug Android test APK also compiles with `./gradlew :app:assembleDebugAndroidTest`.

## Signed APK

`downloads/Cadence-2.6.0.apk` is package `app.cadence.player`, version name 2.6.0, version code 16, minimum API 26 and target API 35. It is non-debuggable, zip-aligned and verifies with APK Signature Scheme v2.

The signer certificate SHA-256 is `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`, matching Cadence 2.5.3 and the supplied public certificate. The APK SHA-256 is `a5e0214e6ea4720618e8f52874a40519778f0dbc2806a49e19f5ed928080e070`.

No signing key, password or private signing configuration is stored in the repository.
