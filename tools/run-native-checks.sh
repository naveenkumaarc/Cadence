#!/usr/bin/env bash
set -euo pipefail
exec ./gradlew --no-daemon :app:connectedDebugAndroidTest
