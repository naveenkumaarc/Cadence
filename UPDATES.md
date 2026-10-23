# Cadence 2.0.1

This update keeps paused music and podcasts available to resume and adds offline song downloads.

- Android's media service keeps its bounded foreground grace after pausing or another app taking audio focus. A headset Play command can restart a released service from the saved queue and position. Android 15 audio-focus requests wait for foreground status while the renderer remains silent.
- Choose **Download song** in a song's options or use the download icon in the music player. Android manages the transfer in the background. Tap the download status for cancel, retry, offline playback or removal.
- **Your Library → Downloaded** contains completed song downloads. A small green check appears beside downloaded songs wherever the same version appears in a playlist. Playback uses the local copy automatically. Removing a download keeps the song in its playlists. The Downloaded collection is also exposed to Android Auto.
- Manually selecting a version for an imported CSV song now saves its duration. Missing lengths on already matched songs are fetched when their playlist is opened, or saved from playback. Imported names, artists and playlist order remain intact.

Install this APK **over Cadence 2.0**. It uses the same package and signing key, with a higher version code. **Do not uninstall 2.0.** Backing up the library before any update is still useful.

Users coming from 0.3.1 still need the backup/restore migration described in README.md because that release's original private signing key was not supplied.

Song audio is stored in Cadence's app-specific Music directory. Uninstalling the app removes these downloads. Library JSON backups do not contain audio files. Keep the private signing kit separate and private.

Physical phone battery policies, real Bluetooth hardware and a real Android Auto head unit still need device checks. Another active media app can own the headset button until Cadence becomes the active player again. Android Force stop prevents background restart until the user opens the app. A Play Protect unknown-app scan can still be requested by Google.

Package: `app.cadence.player`; version name: `2.0.1`; version code: `6`.
