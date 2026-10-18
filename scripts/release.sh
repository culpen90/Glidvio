#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
export GLIDVIO_SIGNING_PROPERTIES="${GLIDVIO_SIGNING_PROPERTIES:-$HOME/.config/glidvio/release-signing.properties}"
if [[ ! -f "$GLIDVIO_SIGNING_PROPERTIES" ]]; then
  echo "Configure the existing release signing identity first; see docs/RELEASING.md."
  exit 2
fi
case "${1:-}" in
  "") ./gradlew :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease ;;
  --no-build) ;; # Preserve the exact APK already exercised by release instrumentation.
  *) echo "Usage: scripts/release.sh [--no-build]"; exit 2 ;;
esac
python3 scripts/verify-release.py
