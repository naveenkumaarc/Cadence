# Cadence 2.1 verification

Checked code commit: `3cc4dee805f92da2cde1571fc0b1133393df973c`.

GitHub Actions: https://github.com/naveenkumaarc/Cadence/actions/runs/39974978283

## Build and automated checks

- Android 11 / API 30 and Android 15 / API 35: **32 core tests and 31 native Android tests on each matrix job**, zero failures, errors or skips.
- Debug APK, Android test APK and unsigned release assembled successfully. Release lint completed with zero errors and 35 warnings, including existing layout/accessibility and dependency advisories.
- New checks cover deterministic two-device merges/deletions, chosen CSV versions, download requests without false local completion, separate episode progress, invalid cloud data rollback, profile defaults, bulk-download cancellation/deduplication, Google-account library isolation, interrupted-apply recovery and legacy podcast download-list migration.
- A native UI test presses the episode's circular play button, verifies actual local-audio playback and pause/resume, and checks that the episode list remains visible. The test also checks the profile/header entry.
- Existing native playback, service restart, other-app audio focus, download/offline playback, backup, search, duration, scrubber and Android Auto browser checks remain in the suite.

## Release and update

- Package app.cadence.player; version name 2.1; versionCode 7; minSdk 26; targetSdk 35; non-debuggable release.
- The APK was signed locally with the retained Cadence 2.0 / 2.0.1 key. APK v2/v3 signatures and alignment verified.
- Certificate SHA-256: `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`.
- Final APK SHA-256: `ee707acbc1cd7ebe5fe7ec3990456f181fc1bd7bb7785485d1b2b62ad423c9ba`.
- Signed 2.1 installed directly over signed 2.0.1 on the disposable API 30 test emulator without uninstalling. The synthetic existing playlist/song metadata, podcast subscriptions and episode positions (20 and 45 minutes) were byte-for-byte unchanged after package replacement. The upgraded app launched and migrated the prior podcast download request into Saved downloads.
- Artifact ZIP digests verified: API 30 `ba9e56e08b226e27dded780386dd3cd844f59de3a6b657faa65a567c634918d9`; API 35 `826b5621c5509b76047ba611db26e600005bcf33356ea5d5f8b5e9e7f303fc45`.

## Google configuration

Owner-approved project cadence-512513: Drive API enabled; Android release and Web OAuth clients created; basic identity and drive.appdata configured as non-sensitive scopes; homepage/privacy/terms published on GitHub Pages; publishing status verified as **In production**. Owner test account was added with explicit approval before publication. No Web client secret is used by the app or included in source or APK.

## Practical limits

Real Google sign-in, Drive upload/download and two physical phones have not been exercised with a personal account in this automated environment. Confirm these after installing the release: sign in, approve hidden Drive access, Sync now on the first phone, then sign in and Sync now on a second phone. Check a playlist edit, two different episode positions, custom profile and saved download requests in both directions. Audio must download separately. Revoked permission and offline retries also need a live Google-account check.

No physical Bluetooth headset, car display, manufacturer-specific battery policy or Google Play Protect verdict is claimed. Android background timing and content-source availability depend on the device and provider. This is not Google brand or Play Protect certification. The original 0.3.1 key remains unavailable; its backup migration requirement is unchanged.
