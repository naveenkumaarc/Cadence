#!/usr/bin/env bash
set -uo pipefail
./gradlew --no-daemon :app:connectedDebugAndroidTest
status=$?
mkdir -p artifacts
adb logcat -d -v threadtime > artifacts/media-logcat.txt
for picture in continuity-positions continuity-backups; do
  adb exec-out run-as app.cadence.player cat "files/$picture.png" > "artifacts/$picture.png" || true
done
exit "$status"
