# Cadence 2.2 verification

Checked application code commit: `7c138ee93a9af7574e1c1179b491748ddb381790`.

GitHub Actions: https://github.com/naveenkumaarc/Cadence/actions/runs/40071187450

## Build and automated checks

- Android 11 / API 30 and Android 15 / API 35: **32 core tests and 47 native Android tests on each matrix job**, with zero failures, errors or skips.
- Debug, Android test and unsigned release APKs assembled successfully. Release lint completed with zero errors and 35 warnings, including existing layout/accessibility and dependency advisories.
- Device continuity tests cover independent episodes, account latest versus this device, intentional rewinds, paused checkpoints, reinstall identity, legacy progress, older-client activity metadata, invalid-data rollback, and preserving newer device histories when restoring an older library.
- Native UI tests check readable device names, full dates and played/remaining labels in the listening-position and backup pickers. Episode controls exercise actual local-audio playback, pause and resume from a newer selected position while retaining the episode list.
- Six Drive transport/scheduling tests use an HTTPS simulated Drive server with a certificate trusted only by the test client. They cover repeated create/update uploads and device metadata, failed optional archives, immediate manual sync and duplicate taps, safe error classification, a selected older restore with local undo, and suppressing uploads while initial backup selection is pending. Production continues to use fixed Google HTTPS endpoints and normal certificate validation.
- Existing playback, service restart, other-app audio focus, offline downloads, backup, search, duration, scrubber and Android Auto browser-client checks remain in the suite.

## Release and update

- Package app.cadence.player; version name 2.2; versionCode 8; minSdk 26; targetSdk 35; non-debuggable release.
- Signed with the retained Cadence 2.0 / 2.0.1 / 2.1 key; APK v2/v3 signatures and alignment verified.
- Certificate SHA-256: `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`.
- Final APK SHA-256: `a8911f70c8811f514491aaa1616800aad33bede64685f4125a5b6e816b52cb3b`.
- Signed 2.2 installed directly over signed 2.1 on the disposable API 30 emulator without uninstalling. Before first launch, the synthetic existing library, podcast subscriptions, 20/45-minute episode positions and saved download requests were byte-for-byte unchanged after package replacement. The upgraded app launched successfully.
- Verified artifact ZIP digests: API 30 `b2c346b54077e76fcc7157413062fba09ce969e6e7f8fb525fcf91e8d965548c`; API 35 `f1b2939a7686a6fd3f420b9ee86c46128da1c5f04bf7fb1777bf58ecf3b6fe3f`.

## Google configuration and practical limits

Existing owner-approved Google project cadence-512513, production OAuth audience, Android release certificate, Web client and drive.appdata scope are unchanged. No Web client secret or private signing key is included in the APK or source archive.

The simulated HTTPS checks verify repeat-sync and restore paths; they do not establish the cause of the previously reported nine-hour-old backup. This release has not been exercised against a personal Google account on two physical phones. After installing, check repeated Sync now uploads and restoring a chosen named backup on Google-enabled phones, including two independent episode positions. Audio must download separately. Revoked permission, offline retries and manufacturer-specific background timing still require real-device checks.

No physical Bluetooth headset, car display or Google Play Protect verdict is claimed. This is not Google brand or Play Protect certification. The original 0.3.1 key remains unavailable and its documented backup migration requirement is unchanged. VERIFICATION-2.1.md retains the prior release report.
