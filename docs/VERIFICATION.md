# Cadence 2.3 verification

Application code checked: `1e46e17a9af55ab9b2a0fa97919bb85f9198461a`.

GitHub Actions: https://github.com/naveenkumaarc/Cadence/actions/runs/40629087421

## Build and automated checks

- Android 11 / API 30 and Android 15 / API 35: **32 core tests and 54 native Android tests on each matrix job**, with zero failures, errors or skips.
- Debug, Android test and unsigned release APKs assembled. Release lint passed with zero errors and 43 warnings; existing and new layout/accessibility, deprecated API and dependency advisories remain visible in the reports.
- Four new car catalogue checks cover unpaged root requests, shared cached recommendations, selection/queue order, album browsing from the cache, podcast progress metadata, offline fallback and paging arithmetic.
- A separate installed test app uses a different UID and the platform legacy MediaBrowser. It subscribes without paging options, reads Home, recommendations, Library and a playlist, then selects the second song. A Media3 observer verifies the full three-song queue and selected index. The helper APK is excluded from the public Cadence release.
- Three native UI checks cover light-theme readability and long-title layout, duration/overflow controls and empty-state actions; actual local-audio seeking updates the mini-player progress without starting playback; a horizontal gesture remains separate from top-edge pull-to-refresh.
- Existing playback/service recovery, audio focus, offline downloads, library/backup, Google account isolation, repeated simulated HTTPS Drive sync, named restores and per-device podcast position tests remain in the suite.

## Reproduced car browse failure

The unpaged-root regression was also run against the previously signed 2.2 APK on the disposable API 30 emulator. The request returned ERROR_BAD_VALUE (-3), failing the expected-success assertion. The same request passes in 2.3, together with the separate legacy-client browse/selection check on both API levels. This reproduces a concrete browse failure; a physical car's exact failure can still have other contributing factors.

## Signed update

- Package app.cadence.player; version 2.3; versionCode 9; minimum API 26; target API 35; release remains non-debuggable.
- Retained Cadence release certificate SHA-256: `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`.
- APK v2/v3 signatures, alignment, package/version and the presence of the new UI/car classes verified. External test-helper classes are excluded.
- APK SHA-256: `f12831bd9b03c47c86018cf1b0de98a9fc40c5e176c85e02c2924545008859c3`.
- Signed 2.3 installed directly over signed 2.2 without uninstalling. Five populated synthetic data files were byte-for-byte unchanged before first launch: playlists, subscriptions, legacy episode positions, saved download requests and account/device episode records. The fixtures include 20/45-minute positions and separate phone/tablet records. The upgraded app launched successfully.
- Downloaded artifact ZIP SHA-256: API 30 `28f4bcf8fd48305f123390e3af6c322ccee6de05372640892c655bb289c66c62`; API 35 `8718f74c98e5c6a030a1749ea764d0f02a904a54054a1eaf3553cd40509d4fa6`.
- The three UI checks also passed against the final signed release on the disposable API 30 emulator at 1600×2560 pixels / 240dpi. Native screenshots cover light-theme rows/settings/empty states and the dark mini player. This is an emulator screen-size check, not a physical-tablet claim.

## Practical limits

Android Auto supplies the car's presentation; the phone screen is not mirrored. These checks exercise both Media3 and the legacy platform browser, including an external UID, but do not verify a physical car/head unit or Bluetooth headset. Reconnect Android Auto after updating and check the actual car's browse and playback behaviour.

New Google scopes/permissions were not introduced. Live Google sign-in and two-phone sync were not exercised with a personal Google account during this update; the existing simulated HTTPS checks passed. Content availability, artwork loading, OEM battery management and network timing still need real-device checks. No Google Play Protect or Google brand certification is claimed. The original 0.3.1 key remains unavailable and its documented migration requirement is unchanged. Earlier reports are retained in VERIFICATION-2.2.md and older versioned reports.
