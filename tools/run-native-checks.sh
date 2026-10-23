#!/usr/bin/env bash
set -u
test_status=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || test_status=$?
mkdir -p artifacts/screenshots
adb pull /sdcard/Android/data/app.cadence.player/files/ artifacts/screenshots/ || true
exit "$test_status"
