#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
glidvio_test_type=debug
glidvio_test_task=connectedDebugAndroidTest
glidvio_allow_physical=false
for argument in "$@"; do
  case "$argument" in
    --release) glidvio_test_type=release; glidvio_test_task=connectedReleaseAndroidTest ;;
    --allow-physical-device) glidvio_allow_physical=true ;;
    *) echo "Usage: scripts/live-test.sh [--release] [--allow-physical-device]"; exit 2 ;;
  esac
done
if [[ "$ANDROID_SERIAL" != emulator-* && "$glidvio_allow_physical" != true ]]; then
  echo "Select an emulator with ANDROID_SERIAL, or explicitly pass --allow-physical-device."
  exit 2
fi
if [[ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
./gradlew "-Pglidvio.testBuildType=$glidvio_test_type" ":app:$glidvio_test_task"
