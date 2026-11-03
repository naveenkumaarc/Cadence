# Cadence 2.4.1 verification

Verified on 3 November 2026. Package `app.cadence.player`, version code `11`, version name `2.4.1`; minimum API 26, target API 35.

## Icon restoration

The launcher foreground, standalone app icon and adaptive-icon XML exactly match the previous main-branch resources, byte for byte. The compiled APK was inspected with aapt: the launcher uses the restored green circle (`#1ED760`) and black equalizer paths. Unused Cadence Loop Android resources were removed. The earlier SVG concept remains labelled as superseded reference material.

## Build checks

The following Gradle tasks completed successfully:

```text
:core:test
:app:assembleDebug
:app:assembleDebugAndroidTest
:app:assembleRelease
:app:lintRelease
```

The 40 core tests remain passing; unchanged core inputs use Gradle's valid cached results. Release lint reports 0 errors and 44 non-blocking warnings. The prior 2.4 validation includes 10 Android download/playback/UI tests; those are recorded in VERIFICATION-2.4.md. This icon and version update does not change their functional code paths, and no new UI tests were added for the icon restoration.

## Signing and download

The APK signature verifies with the retained release certificate, SHA-256:

```text
26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f
```

APK size: 8,474,022 bytes. APK SHA-256:

```text
bab9e6198b04b82cb51bb064a4ce0e2962b6b16a759a75826ca7fa8eb5d7e38f
```

The signed APK is published as `downloads/Cadence-2.4.1.apk`, with its checksum alongside it and a direct link near the top of the main-branch README. Version code 11 permits an in-place update over 2.4 (code 10). The package, signing identity, library and backup formats are retained. Private signing material stays outside the repository.

Physical Wi-Fi/mobile switching, newer-phone gesture navigation and OEM-specific background restrictions retain the device-check limits documented for 2.4.
