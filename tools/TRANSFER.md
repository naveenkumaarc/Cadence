# Transfer from Cadence 0.2.0

The old test APK's private signing key was not preserved. Android therefore cannot accept this release as an update to that installed app. Cadence 0.3.0 uses package `app.cadence.player` and installs alongside `app.cadence.music`. **Do not uninstall the old app before exporting your library.**

1. Pause playback in the old app. Install the new APK; its green disc icon identifies the new version. Settings says 0.3.0.
2. On a computer, install Python 3 and Google's [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools).
3. Enable Developer options and USB debugging on the phone, connect it by USB, unlock it and approve that computer's debugging prompt.
4. Extract this folder and run:

   Windows: `py transfer_from_02.py --adb C:\platform-tools\adb.exe`

   macOS/Linux: `python3 transfer_from_02.py --adb /path/to/platform-tools/adb`

5. The script reads the old app's saved files and creates `Cadence-transfer.json` on the computer and in the phone's Downloads folder. It does not uninstall either app or change the old library.
6. In the new app, choose **Settings → Restore backup → Downloads → Cadence-transfer.json**, then Restore. Check your playlists and listening progress before removing the old app. You can turn USB debugging off afterwards.

The transfer includes playlists, favourites, podcast subscriptions, progress, preferences and the last queue. Saved duplicates and playlist order are kept. Downloaded audio and Android file-access grants cannot transfer between app packages: reselect MP3/FLAC files through Import from file, and download podcast episodes again. A downloaded podcast in the old queue is converted to its public streaming enclosure when known.

No computer available: keep the old app installed and import your original playlist CSVs into the new app. There is no phone-only method here to access another app's private data.

If several devices are connected, supply `--serial DEVICE_ID`. To keep another export, supply `--output Cadence-transfer-2.json`. The utility requires the original debuggable 0.2.0 build. A non-debuggable build from another source cannot be read this way.
