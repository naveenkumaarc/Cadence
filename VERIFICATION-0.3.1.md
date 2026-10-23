# Cadence 0.3.1 verification — 23 October 2026

## Passed

- Core: 25 JUnit tests, zero failures/errors/skips. Covers CSV Unicode, quoting, order and duplicates; links and version matching; music metadata cleanup; RSS/Atom/OPML and XML external-entity rejection; radio parsing/deduplication and continuation selection; radio context across manual queue entries, shuffled traversal, near-end extension and podcast exclusion.
- Android: 13 instrumentation tests passed in 95.069 seconds on an Android 11 / API 30 AOSP x86_64 emulator using Media3 and generated local WAV audio. Covers media-browser root/playlists/podcast children; actual crossfade overlap (incoming decoder progressed before session handover) and pause; backup merge with duplicates and progress; Android RSS/OPML parsing; local decoding, podcast progress and speed; queue index/timestamp restored paused until Play; widget podcast rewind with saved interval and paused state; preview versus explicit subscription; Play next/Add queue priority, shuffle, move and remove; selected-song playlist expansion; podcast episode order/resume position; one-press widget restart of an ended song; transient audio-focus pause/resume.
- Separate-UID media-browser app: browsed the library, selected the second song and received its entire three-song playlist, verified an arbitrary foreign audio URI did not replace the server queue, and exercised Next. This tests the Android Auto catalogue/transport contract; it does not replace a physical head-unit test. The development-only carprobe APK is not bundled in Cadence.
- Release upgrade: installed retained 0.3.0, created a playlist through its native UI, installed 0.3.1 over it, and confirmed that playlist remained in Your Library. Package and signing certificate match. Release application flags are not debuggable.
- Debug/release APKs built successfully; release lint: zero errors, 30 warnings, mainly text/resource localization, editor/accessibility notices, API-qualified widget attributes and dependency/update notices. The exported playback service supports external media browsers; arbitrary foreign media URIs are rejected. Media3 unstable forwarding API is explicitly opted into.
- Live unauthenticated YouTube Music: five suggestions, 50 related-radio songs, 20 playlist search results; a 26-song public playlist loaded completely; another expanded from its initial 100 songs to 1,813 through continuation pages. Tamil and Telugu each returned three distinct discovery shelves. This establishes catalogue/radio/playlist access, not audio playback.
- Final APK signature verification passed with the retained 3,072-bit RSA release certificate, APK Signature Scheme v2. Package app.cadence.player, versionCode 4, versionName 0.3.1, min API 26, target/compile API 35. HTTPS-only application policy and playback-related permissions; no account credentials or signing secrets in the source archive.
- Playback efficiency: cached radio context refreshes on timeline/shuffle changes; periodic near-end checks inspect at most three upcoming entries instead of scanning a large queue. Artwork is cached; Android Auto library reads use a worker; seeking sends one command on release.

## Remaining phone checks and limits

- End-to-end YouTube audio is **not verified in this environment**. One search source exposed no supported audio stream. A known YouTube Music song exposed a WebM/160 kbps source, but its media-host response was a 195-byte HTML “Site Unavailable” page instead of audio. The app detects HTML responses and offers Retry/version selection. Catalogue success does not establish streaming success on the phone's network.
- Physical Android Auto head-unit discovery/voice recognition, Bluetooth audio, manufacturer battery restrictions and Android 16 screen-off playback need device checks. The emulator exercises API 30, not every supported Android release. A sideloaded app's visibility in Android Auto can also depend on the car/phone's app-source policy.
- Full remote podcast DownloadManager completion and audible output on a phone remain device checks. Existing download validation and offline handling are retained.
- Crossfade was tested with local generated music. Slow network preload can require a normal transition. Podcasts and repeat-one do not crossfade.
- No Google Play Protect scan, certification or approval verdict was obtained. Release signing cannot guarantee removal of an unfamiliar-app scan prompt or a harmful-app classification. The exact phone warning is still needed to diagnose its cause; do not disable Play Protect to suppress it.
- The software emulator showed a temporary System UI ANR under load. Native playback tests subsequently completed; this is not proof of performance on every phone.
- The original 0.2.0 debug signing key was missing from saved source. The 0.3 series uses app.cadence.player alongside that old app. Existing transfer tooling requires a computer and the original debuggable 0.2.0 installation; file grants/offline files do not transfer across app identities. **0.3.0 users can install 0.3.1 directly without uninstalling.**

## Reproduce

Use JDK 17 and Android SDK 35. See README.md for release-signing environment variables. Run:

```sh
./gradlew :app:clean :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w app.cadence.player.test/androidx.test.runner.AndroidJUnitRunner
```

A release-signed installation cannot be replaced by a debug-signed APK; use a disposable test device/profile. For the separate-UID check, build/install :carprobe:assembleDebug after instrumentation creates its car-probe playlist, then launch app.cadence.carprobe/.MainActivity. Its private files/result.json records the result. Live catalogue checks use :probe:updateProbe; remote catalogue/media results can change.

Release certificate SHA-256:

`ad40247b98452fd8712c7b8b2282f33ae7a09fc71b51978b239cc3ce0d6f1e77`

## References

- https://developer.android.com/media/media3/session/serve-content
- https://developer.android.com/media/media3/session/background-playback
- https://developer.android.com/media/optimize/audio-focus
- https://developer.android.com/develop/ui/views/appwidgets
- https://developer.android.com/studio/publish/app-signing
- https://developers.google.com/android/play-protect/warning-dev-guidance
- https://github.com/sigma67/ytmusicapi/blob/main/ytmusicapi/mixins/watch.py
- https://github.com/sigma67/ytmusicapi/blob/main/ytmusicapi/continuations.py
