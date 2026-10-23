# Cadence 2.1

Android music and podcast player, Android 8 or newer. Dark Material-style surfaces, rounded cards, native Media3 playback and optional Google Drive continuity.

## Download

[**Download Cadence 2.1 APK**](https://github.com/naveenkumaarc/Cadence/releases/download/v2.1/Cadence-2.1.apk) · [Release, source archive and checksums](https://github.com/naveenkumaarc/Cadence/releases/tag/v2.1) · [Homepage](https://naveenkumaarc.github.io/Cadence/)

**Using Cadence 2.0 or 2.0.1? Install this update over it without uninstalling.** The package and release signing key are retained. An in-place update from 2.0.1 was checked with a synthetic library, subscriptions and two episode positions.

## New in 2.1

- Optional Google sign-in, automatic hidden Drive sync and **Sync now**. Profile, playlists, likes, selected CSV song versions and durations, podcast subscriptions, episode progress, playback preferences and the saved queue can continue on another phone.
- The download request list syncs; actual audio files, device paths, file permissions and completed-download flags stay on each phone. **Downloaded** shows songs available on this phone. **Saved downloads** lets another phone download the requested songs and episodes.
- Google name and photo appear by default. Change the Cadence name/photo from the small profile photo beside **Cadence**. These changes do not change the Google account.
- **Download all** in playlists skips existing downloads and offers preparation status, retry and cancellation. CSV rows still needing a song match are identified before downloading.
- Downloaded songs use a green circle with a white tick, including when the same version appears in another playlist.
- Each podcast episode shows its own played/remaining minutes. Its round ▶ / Ⅱ button controls playback while keeping the episode list visible.

Sign-in stays optional. Tap the profile photo → **Sign in with Google**, choose whether to merge the current phone library, then approve hidden Cadence Drive storage. Use the same Google account on another phone. Changes sync while connected when the app opens and when Android schedules background work; **Sync now** requests an immediate check. Sync does not start remote playback automatically: **Continue listening** explicitly restores the saved queue.

Cloud settings include automatic-sync control, sign-out and recent recovery copies. Local JSON backup/restore remains available. [Privacy policy](https://naveenkumaarc.github.io/Cadence/privacy.html) · [Google configuration for maintainers/forks](GOOGLE-CONFIG.md) · [Release notes](RELEASE-NOTES-2.1.md) · [Verification and limits](VERIFICATION.md).

Google Cloud is configured for an External audience with publishing status **In production**, basic profile permissions and the non-sensitive `drive.appdata` scope. Android and Web OAuth clients use the retained release certificate. This configuration is distinct from testing a real account's sign-in and two-phone sync; that live account check still needs to be performed on Google-enabled phones.

## Playback and compatibility

The 2.0.1 fixes for paused-service recovery, Bluetooth media buttons, audio focus, CSV song lengths, offline downloads, search filters and the player scrubber remain. Android Auto's MediaLibraryService exposes the library and playback controls. Automated browser-client and playback tests pass; a physical car and headset have not been tested for this release. A sideloaded app may need Android Auto's developer **Unknown sources** option before appearing in the car app list.

Downloads use Android's DownloadManager and app-specific storage. Removing a download keeps playlist entries. Uninstalling removes private app data and app-specific audio downloads. Export a local backup before removing the app.

## Original 0.3.1 migration

The original 0.3.1 private signing key was not supplied. It cannot be recovered from its APK certificate. The retained key introduced with 2.0 cannot directly replace an installed 0.3.1 app.

1. In 0.3.1, save a JSON backup outside app-private storage and verify that it contains your playlists. Keep a second copy.
2. Only after checking the backup, uninstall 0.3.1 and install the new APK.
3. In Cadence, choose **Settings → Restore backup** and select the JSON. Check your playlists, subscriptions and progress.
4. Download offline audio again and reselect local files; audio and Android file permissions are not in the backup.

The private signing kit stays outside the public source. Future updates must retain that key and increase `versionCode`. Version 2.1 uses package `app.cadence.player`, version code `7`.

## Play Protect

Google may ask to scan an unknown sideloaded app. A signing key or release build cannot guarantee Google's verdict. Allow Google's scan and follow its result. No Play Protect approval is claimed.

The release is non-debuggable, targets API 35, uses APK v2/v3 signing and disables cleartext traffic. WorkManager adds boot recovery and Google's credential libraries declare system biometric permissions; Cadence does not request contacts, SMS, overlays, accessibility or app-install permissions.

## Build

JDK 17, Android SDK platform 35/build-tools 35.0.0 and the included Gradle 8.11.1 wrapper. Configure the SDK with `ANDROID_HOME` or local.properties.

```sh
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Keep release signing outside source control:

```sh
export CADENCE_KEYSTORE=/private/path/cadence-release.jks
export CADENCE_STORE_PASSWORD='your-private-password'
# Alias: cadence; key password defaults to store password.
./gradlew :app:assembleRelease :app:lintRelease
```

CI can build an unsigned release without a private key:

```sh
./gradlew -PcadenceUnsigned :core:test :app:assembleRelease :app:lintRelease
```

An unsigned APK must be signed before installing. A debug APK cannot update a release-signed installation.

GPL-3.0-or-later. See COPYING and THIRD_PARTY_NOTICES.md. The downloadable source archive includes corresponding NewPipeExtractor source in vendor/sources. Live streaming and device-specific behaviour require phone checks. VERIFICATION.md records the 2.1 checks; VERIFICATION-2.0.1.md and VERIFICATION-2.0.md retain the earlier reports.
