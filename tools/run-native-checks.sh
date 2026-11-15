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
for theme in light dark; do
  for screen in home library settings player; do
    picture="cadence25-$theme-$screen"
    adb pull "/data/local/tmp/$picture.png" "artifacts/$picture.png" || true
  done
done
for theme in light dark; do
  for screen in search library player equalizer; do
    picture="cadence26-$theme-$screen"
    adb pull "/data/local/tmp/$picture.png" "artifacts/$picture.png" || true
  done
  for scale in 100 130 200; do
    picture="cadence-album-$theme-$scale"
    adb pull "/data/local/tmp/$picture.png" "artifacts/$picture.png" || true
  done
done
exit "$status"
