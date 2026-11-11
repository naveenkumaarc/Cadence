# Cadence 2.0.1 verification — 23 October 2026

## Release identity and update preservation

- Package `app.cadence.player`, version name `2.0.1`, version code `6`, minimum Android API 26, target/compile API 35. Non-debuggable release APK.
- APK SHA-256: `ba8c26bad889514932f5c0e45fe96429d45b173a22f9cea4922562e9fdcc682d`.
- APK signature verification passed with the same certificate as the supplied 2.0 release: `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`. Standard v2/v3 signatures and ZIP alignment verified.
- A release installation over 2.0 succeeded without uninstalling on a disposable API 30 emulator. A synthetic existing library remained byte-for-byte identical before and after installation and launch. No actual user library was used or published.
- No added device permissions compared with 2.0. Private signing material stays outside the APK, source archive and public repository.

## Automated checks

Code commit: `c56f54e3f8d639592c60906c8dd00c681ff7a4fb`.
Successful workflow: https://github.com/naveenkumaarc/Cadence/actions/runs/39949778536

- Core: 27 tests passed, no failures/errors/skips. New checks cover retaining imported identity while saving the chosen version's duration and preserving a known length when the candidate has none.
- Native Android: all 21 tests passed on both API 30 (Android 11) and API 35 (Android 15). This includes real local decoding, crossfade, queue/restore, podcast position and speed, backup merging, widgets, Android Auto library selection, search history and native player views.
- Playback checks include a real 35-second paused podcast, a real 35-second permanent audio-focus interruption, manual media-key resume, service shutdown followed by Android-routed headset-style Play, queue/index/position/repeat/speed restoration and empty-history handling. Actual Bluetooth radio hardware was not used.
- The song test used Android's DownloadManager to fetch a public synthetic three-second WAV over HTTPS, verified its exact SHA-256, reopened the stored download index, checked the Downloaded collection and green indicator in another playlist, decoded the downloaded bytes through a synthetic remote source ID, saved a missing duration, and removed the download without removing its playlist entry.
- The packaged extractor URL codec round trip and video-link parser passed on both OS versions, including the API 26–32 compatibility adapter. This addresses a runtime overload absent before API 33.
- Release lint: zero errors and 26 warnings. Debug, native test and unsigned release APKs built successfully. The tested unsigned release was signed locally with the retained private key.

## Behavior and limits

- Media3 1.8.0 provides the bounded paused foreground grace (up to ten minutes). Cadence registers playback resumption, retains stable source IDs and saved playback settings, and delays Android 15 audio-focus acquisition until foreground status is established while keeping the renderer silent.
- Completed music downloads live in app-specific Music storage. Playlists keep their original source IDs and automatically use local copies through the data-source resolver. Download removal does not remove playlist entries. The virtual Downloaded collection is included in the car library.
- Metadata lookup can require internet access. Existing confirmed versions, imported names and order remain intact when filling a missing duration. The tests exercise actual synthetic media transfer and decoding; they do not establish the availability of every live remote song or signed stream URL.
- A physical Bluetooth headset, the user's phone battery policy, live source-specific downloads and a physical Android Auto head unit remain device checks. Another active media app can own headset controls. Android Force stop blocks background restart until the app is opened.
- No Google Play Protect verdict or certification was obtained. An unknown-app scan prompt can still appear.
- The old 0.3.1 key is still missing; its users need the documented backup/restore migration. Users of the supplied 2.0 release can install this update directly. JSON backups do not contain downloaded audio.
- VERIFICATION-2.0.md and VERIFICATION-0.3.1.md describe historical releases.
