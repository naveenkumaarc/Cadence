# Cadence 2.4 verification

Verified on 3 November 2026. Package `app.cadence.player`, version code `10`, version name `2.4`.

## Build and static checks

JDK 18, Java 17 source compatibility, Android SDK 35/build-tools 35.0.0, bundled Gradle 8.11.1, Windows.

```text
:core:test
:app:assembleDebug
:app:assembleDebugAndroidTest
:app:assembleRelease
:app:lintRelease
```

All completed successfully. Debug lint also passed. Release lint reports zero errors and 44 non-blocking warnings. The retained release key remains outside the repository and source archive.

## Core tests

40 tests passed. Eight new resumable-transfer tests exercise partial-file pause/resume with Range and If-Range, servers ignoring Range, missing validators, invalid ranges, completed ranges, changed validators, cancellation before a request, and expired-source HTTP failures. MockWebServer verifies the actual requested byte offset and the completed file's bytes.

## Android tests

Android 11/API 30 emulator, native views and Media3 service:

- Six requested-change tests passed: album Back restores Search query/filter/results and hides obsolete loading status; Home chips stay on Home and the avatar precedes the brand; All includes podcast rows and both search controls clear; subscriptions sort by latest activity and exclude previews; per-song pause state persists and the queue toggles controls; custom palettes meet contrast checks in dark/light modes and load after reopening the activity.
- Existing `SongDownloadsIntegrationTest` passed. A real HTTPS audio fixture is transferred by the new WorkManager queue, verified by SHA-256, reloaded from the index, shown across playlists and decoded through the local file. Removing the download preserves playlist metadata.
- Three existing `UiPolishIntegrationTest` tests passed: light-theme readability/empty states, actual mini-player seek progress, and refresh gestures preserving horizontal scrolling.

Screenshots were reviewed for Home, Search return, the download queue and custom-colour Settings. The launcher icon has an editable SVG master and native adaptive/monochrome vector resources.

## Signing and compatibility

The signed APK verifies with APK Signature Scheme v2. Its certificate SHA-256 matches the retained Cadence 2.0.1 release:

```text
26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f
```

This supports in-place updates from releases using that key. No private signing material is included in delivered artifacts. Legacy completed downloads remain readable through their existing DownloadManager index; libraries, podcast subscriptions/progress and cloud backup formats are unchanged.

## Limits

The automated UI tests use synthetic libraries and podcast results. They do not validate live Google sign-in, physical Android Auto, or a particular publisher's feed availability. The live transfer test uses public synthetic audio. Physical Wi-Fi-to-mobile switching, OEM battery restrictions and gesture navigation on newer phones still need device checks.

HTTP resume depends on the audio server's support and validators. Unsupported/changed representations restart safely. The first pause of an unfinished legacy DownloadManager song transfer migrates that request and restarts its bytes; completed files are retained. Widget progress tint follows the selected scheme on Android 12 and newer; widget labels and playback icons are recoloured on all supported versions.
