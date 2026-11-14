# Cadence 2.5.3 verification

## Automated checks

The requested `./gradlew :core:test :app:assembleDebug :app:lintDebug` gate passed after each of the three focused tasks. The final release was rebuilt with the retained private signing key using `:core:test :app:assembleRelease :app:lintRelease`; release lint completed without errors. `:app:assembleDebugAndroidTest` also passed, confirming that the accent-picker, theme screenshot, TalkBack and integration test sources compile.

Search JVM tests cover intent ordering, Rajini and Harini examples, podcast and album intent, artist Top result, spelling aliases, Tamil-script equivalence, empty and one-character queries, and deterministic ordering. Accent JVM tests cover AA contrast for every preset in both appearances, custom-colour adjustment/rejection and all three migration paths.

No Android device or emulator was attached to this build environment, so device screenshot tests and a live media-notification visual check remain compiled but not executed. Static mask previews for circle, squircle, rounded-square and monochrome treatments are in `design/cadence-icon-previews.png`; the safe-zone artwork is not clipped.

## Compatibility

No database schema, backup document, Google Drive sync payload, NewPipeExtractor integration, streaming source or storage format changed. No permission was added. Existing explicit theme and custom-colour choices remain compatible.

## Release artifact

`Cadence-2.5.3.apk` is package `app.cadence.player`, version name 2.5.3, version code 15, minimum API 26 and target API 35. It is non-debuggable, zip-aligned and verifies with APK Signature Scheme v2. Its certificate SHA-256 is `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`, matching the retained Cadence release certificate used by 2.5.2. The APK SHA-256 is `b306e4a3af54462fefa8bb36a07b346c91b1a96424870bee0564bb1d83736566`.
