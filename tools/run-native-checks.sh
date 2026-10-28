#!/usr/bin/env bash
set -uo pipefail
adb install -r carprobe/build/outputs/apk/debug/carprobe-debug.apk
./gradlew --no-daemon :app:connectedDebugAndroidTest
status=$?
mkdir -p artifacts
adb logcat -d -v threadtime > artifacts/media-logcat.txt
for picture in polish-library polish-empty polish-settings polish-player; do
  adb pull "/data/local/tmp/$picture.png" "artifacts/$picture.png" || true
done
exit "$status"
