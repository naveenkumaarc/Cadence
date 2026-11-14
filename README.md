# Cadence

Cadence is a native Android music and podcast player for Android 8 and newer. It combines Media3 playback, discovery, offline downloads, podcasts, Android Auto, a home-screen widget and optional Google Drive continuity in a focused light and dark interface.

## Screens

Cadence provides matching Light, Dark and Follow device themes across Home, Search, Library, Profile, detail pages, the full player, queue, sheets and settings. The instrumented UI suite captures Home, Player, Library and Settings in both themes; see [UI verification](docs/VERIFICATION-2.5.0.md) for the capture names and device checks.

## Features

- Music discovery, search, playlists, likes and local file import
- Podcast subscriptions, downloads, progress, resume modes and playback speed
- Media3 playback with queue editing, crossfade, sleep timer, equalizer access and audio-focus handling
- Resumable song downloads with Wi-Fi controls and saved download requests
- Android Auto browse and playback controls
- Home-screen widget with artwork, progress and transport controls
- Optional Google Drive sync, named backups and compatible local backup/restore
- Light, Dark and Follow device themes, custom accent colors, motion and touch-feedback controls

Cadence 2.5.1 is available as a signed update: [download APK](https://github.com/naveenkumaarc/Cadence/releases/download/v2.5.1/Cadence-2.5.1.apk) · [SHA-256](downloads/SHA256SUMS-2.5.1.txt) · [install notes](downloads/READ-BEFORE-INSTALLING-2.5.1.txt). It uses the retained release certificate and can update earlier release-signed Cadence installations without uninstalling.

Version history and verification reports live in [docs](docs/). Future signed binary artifacts should move to GitHub Releases so the source checkout stays small; historical APKs and checksums are retained unchanged.

## Build

Use JDK 17, Android SDK platform 35, build-tools 35.0.0 and the included Gradle 8.11.1 wrapper.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

Build an unsigned release without private signing material:

```sh
./gradlew -PcadenceUnsigned :app:assembleRelease :app:lintRelease
```

Release signing remains external to the repository. Set `CADENCE_KEYSTORE`, `CADENCE_STORE_PASSWORD` and, when different, `CADENCE_KEY_PASSWORD`, then run `./gradlew :app:assembleRelease`. Never commit a keystore or password.

Connected Android tests require an emulator or Android 8+ device:

```sh
./gradlew :app:assembleDebugAndroidTest :carprobe:assembleDebug
bash tools/run-native-checks.sh
```

## Repository

- `app/` — phone UI, playback service, downloads, sync, widget and Android Auto integration.
- `core/` — platform-independent catalog, matching, import and networking logic.
- `probe/` — a small JVM probe for extractor and catalog diagnostics.
- `carprobe/` — an Android media-browser client used to verify the car browse tree.
- `examples/` — sample import files.
- `tools/` — repeatable transfer and connected-device verification helpers.

Maintainer notes: [Google configuration](GOOGLE-CONFIG.md) · [privacy](PRIVACY.md) · [project structure](docs/PROJECT-STRUCTURE.md) · [2.5.1 release notes](docs/RELEASE-NOTES-2.5.1.md).

## License

Cadence is GPL-3.0-or-later. See [COPYING](COPYING) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Corresponding NewPipeExtractor source remains in `vendor/sources`.
