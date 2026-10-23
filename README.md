# Cadence 2.0

Native Android music and podcast player, Android 8 or newer. Charcoal Material-style surfaces, soft mint accents, rounded cards and native Media3 playback.

## Download APK

[**Download Cadence 2.0 for Android**](https://raw.githubusercontent.com/naveenkumaarc/MusicApp/main/downloads/Cadence-2.0.apk)

Signed release APK · 6.14 MB · Android 8 or newer. The APK is also in [downloads/](downloads/). [SHA-256 checksum](downloads/SHA256SUMS.txt).

**Using 0.3.1? Save and verify a backup in the old app before uninstalling.** This release uses a new signing key and cannot install over 0.3.1. Follow the migration steps below to restore your library. Google may still request an unknown-app scan.

## Read before installing

The original 0.3.1 private signing key was not supplied and is not in the source ZIP. Its public certificate is in the APK, but the private key cannot be recovered from it. A new key cannot replace an installed app signed with the missing old key.

The delivered 2.0 APK uses a **new retained signing key**. Package `app.cadence.player`, version name `2.0`, version code `5`, and library/backup formats are retained. It **cannot install directly over 0.3.1** because the signing certificates differ.

1. In the **old app**, choose **Settings → Save backup** and save the JSON outside the app's private storage, such as Documents. Check that it exists and contains your playlists before removing anything.
2. Keep a second copy of the JSON. Uninstalling removes private app data and offline downloads.
3. After confirming the backup, uninstall the old app and install the new-key 2.0 APK.
4. In **2.0**, choose **Settings → Restore backup** and select that JSON. Check playlists, liked songs, subscriptions, progress and queue.
5. Download offline episodes again and reselect local audio files. Audio files and Android file permissions are not included in the JSON.

The private signing kit is separate from this source repository. Keep it private and backed up. Reuse it for every future version and increase `versionCode`; those versions can update the 2.0 installation directly. If the original 0.3.1 key is ever found, this source can instead be signed with it for a direct update.

Original 0.3.1 certificate SHA-256:
`ad40247b98452fd8712c7b8b2282f33ae7a09fc71b51978b239cc3ce0d6f1e77`

## Design changes

- Search: pill-shaped field, cover-art rows, persistent local recents and save/remove actions. Songs, Albums and Artists filters remain alongside All and Playlists. Explicit loading, empty and retry states.
- Player: larger rounded artwork, white play button, a 4 dp high-contrast scrubber and separate elapsed/remaining labels. Dragging previews time; releasing performs one seek. Related songs or more podcast episodes appear underneath, with play/queue actions. Podcast skip intervals and speed controls remain.
- Header: vertically aligned title, modern queue/adjustment icons and 48 dp touch targets.
- Settings: grouped rounded cards for Discovery, Playback, Your library, Personalise and About, with icons, subtitles and a working autoplay switch.
- Existing playback, playlists, backups, podcasts, downloads, widget and Android Auto support remain. No device permission was added.

## Play Protect

The reported message asks to scan an **unknown app**. It is not caused by a missing signing file in the source package. Allow Google's **Scan app** check and follow its result. A signing key, release build or source audit cannot guarantee Google's verdict. No Google approval is claimed; do not disable Play Protect to suppress a warning.

The release is non-debuggable, uses standard APK signing, targets API 35, disables cleartext traffic and requests only internet/network status, wake lock, media-playback foreground service and notifications. No accessibility, SMS, contacts, overlays or app-install permissions.

Guidance: https://developers.google.com/android/play-protect/warning-dev-guidance
Signing: https://developer.android.com/studio/publish/app-signing

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

GPL-3.0-or-later. See COPYING and THIRD_PARTY_NOTICES.md. The downloadable source archive includes corresponding NewPipeExtractor source in vendor/sources. Live streaming and device-specific behaviour require phone checks. VERIFICATION.md records only checks actually run on 2.0.
