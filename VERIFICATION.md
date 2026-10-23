# Cadence 2.0 verification — 23 October 2026

## Release artifact

- Package `app.cadence.player`; version name `2.0`; version code `5`; Android 8/API 26 minimum; target and compile API 35.
- Standard APK signature verification passed with schemes v2 and v3 and a 3,072-bit RSA key. The release is non-debuggable and ZIP alignment verification passed.
- The new certificate SHA-256 is `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`.
- The old 0.3.1 APK certificate SHA-256 is `ad40247b98452fd8712c7b8b2282f33ae7a09fc71b51978b239cc3ce0d6f1e77`. Its private key was not supplied. Android rejected installation of the new-key APK over it with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, as expected. A higher version number cannot change that requirement.
- A separate private signing kit is supplied for future updates. No signing key or password is in the source ZIP or GitHub repository.
- Binary permission comparison against the supplied APK found no added device permissions. HTTPS-only policy remains. No accessibility, SMS, contacts, overlays, device administrator or package-install permission.

## Automated checks

- Core: 25 JUnit tests, zero failures/errors/skips. Includes CSV imports, music metadata, podcast feeds, Spotify matching and radio queue planning.
- Native Android: 15 instrumentation tests passed on an API 30 AOSP x86_64 emulator. Existing checks cover actual local audio decoding, queue/resume state, crossfade overlap, podcast progress/speed, Android Auto library selection, widgets, audio focus and backup merging. New checks cover history persistence/deduplication/removal, grouped settings controls, elapsed/remaining time after seeking, related content and changing the player between music and podcast modes.
- Release lint: zero errors, 34 warnings, principally text localization and existing platform/dependency notices. Debug, test and unsigned release packages built successfully on GitHub with JDK 17.
- Public CI uses a synthetic backup fixture. An optional local-only migration argument allows checking an exported backup without publishing its contents. The exported 0.3.1 test backup was restored successfully in the local native checks.
- A pre-existing checkpoint test intermittently paused around the one-second boundary and then waited for a saved position just short of that boundary. The test now lets playback advance beyond 1.5 seconds before applying the same saved-progress threshold of one second. No playback-service code was changed for this adjustment.

Final code checks: https://github.com/naveenkumaarc/MusicApp/actions/runs/39936598561

## Migration and visual checks

- Installed the supplied release-signed 0.3.1 APK on a disposable emulator, created a test playlist and exported a valid backup through its native Save backup control. The original app's private key could not be recovered from the APK.
- The exported 0.3.1 JSON restored successfully through the native 2.0 backup implementation in the local instrumentation check. Its test playlist was present afterward. Backup contents stayed local; public CI used synthetic data.
- `Backup.java`, `Library.java`, `PodcastStore.java`, `Preferences.java` and `PlaybackService.java` remain byte-for-byte unchanged from the supplied source. Library formats, preference keys and playback persistence are retained.
- Native screenshots were reviewed for Search, music player, podcast player and Settings. The final player reserves space around the scrubber thumb and uses a smaller hero image to expose more of the related-content section. Previews use sample entries and fallback artwork; no sample library is bundled into the installed release.

## Limits and phone checks

- No Google Play Protect scan or certification verdict was obtained. The user reported an unknown-app scan prompt. Signing and local checks cannot promise that Google will remove it; allow Google's scan and follow its result.
- Direct in-place migration from 0.3.1 is impossible without its original private signing key. Back up and verify the JSON before uninstalling; restore it in 2.0. Audio downloads and local-file permission grants are outside the JSON and must be restored separately.
- Full Android file-picker migration on a physical phone, live remote audio streaming, Bluetooth and physical Android Auto hardware remain phone checks. Catalogue requests were observed on the local emulator; that does not establish streaming playback or Google's security verdict.
- VERIFICATION-0.3.1.md contains historical results for the old release and is not evidence of new 2.0 testing.

