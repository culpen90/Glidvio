#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
if [[ "$ANDROID_SERIAL" != emulator-* && "${1:-}" != --allow-physical-device ]]; then
  echo "Select an emulator with ANDROID_SERIAL, or explicitly pass --allow-physical-device."
  exit 2
fi
if [[ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
./gradlew :app:connectedDebugAndroidTest
