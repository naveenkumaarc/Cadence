# Cadence 2.6.1 verification

## Build and static checks

The release was built from the 2.6.1 release commit with JDK 17 and Android SDK 35. The following checks completed successfully:

```text
./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
./gradlew :core:test :app:assembleDebug :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
```

The isolated emulator Search suite covers stable focus while typing a multi-word query, state restoration across tabs, suggestion submission, IME-safe scrolling, and Light/Dark screenshots for Idle, Typing, Loading, Results, Empty and Error.

## Signed APK

`downloads/Cadence-2.6.1.apk` is package `app.cadence.player`, version name 2.6.1, version code 17, minimum API 26 and target API 35. It is non-debuggable, zip-aligned and verifies with APK Signature Scheme v2.

The signer certificate SHA-256 is `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`, matching Cadence 2.6.0 and the supplied public certificate. The APK SHA-256 is `e6e0303d06d500e6f439d73ed2ed7a5755b070ddb6762d855205379a869fe81c`.

No signing key, password or private signing configuration is stored in the repository.
