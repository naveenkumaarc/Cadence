#!/usr/bin/env bash
set -uo pipefail
./gradlew --no-daemon :app:connectedDebugAndroidTest
status=$?
mkdir -p artifacts
adb logcat -d -v threadtime > artifacts/media-logcat.txt
exit "$status"
