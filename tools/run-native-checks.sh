#!/usr/bin/env bash
set -u
test_status=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || test_status=$?
mkdir -p artifacts/screenshots
for screen in search settings player podcast-player; do
  adb pull "/data/local/tmp/cadence-$screen.png" "artifacts/screenshots/$screen.png" || true
done
exit "$test_status"
