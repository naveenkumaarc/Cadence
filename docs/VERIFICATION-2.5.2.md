# Cadence 2.5.2 verification

## Automated checks

The requested `./gradlew :core:test :app:assembleDebug :app:lintDebug` gate passed. The release was then rebuilt with the retained private signing key using `:core:test :app:assembleRelease :app:lintRelease`; release lint completed without errors. Android instrumentation sources for the mood autoplay, full shuffle, local-first playback, appearance, contrast, Home spacing and light/dark screenshot checks compile successfully. No emulator was attached in this build environment, so those device tests remain compiled rather than executed here.

The shuffle unit tests cover collections of 3, 50, 500 and 2,000 items, duplicate entries, partial traversal and saved-order restoration. Mood-autoplay tests cover a Tamil melody seed, language and era ranking, recent-song exclusion, no repeats and close/adjacent interleaving. Local-playback integration coverage verifies valid downloaded episode selection, missing-file stream fallback and an offline local fixture.

## Compatibility

No database schema, backup document, Google Drive sync payload, NewPipeExtractor integration or storage format changed. No permission was added. Existing explicit appearance choices are imported once into DataStore; established installs that used the earlier implicit dark default remain dark, while new installs follow the system.

## Release artifact

`Cadence-2.5.2.apk` is package `app.cadence.player`, version name 2.5.2, version code 14, minimum API 26 and target API 35. It is non-debuggable, zip-aligned and verifies with APK Signature Scheme v2. Its certificate SHA-256 is `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`, matching the retained Cadence release certificate used by 2.5.1. The APK SHA-256 is `93a650ad7a4cad7fe3b1af7adc4b333b70b6c492d5acb8b2e05fe9e495bcf5fe`.
