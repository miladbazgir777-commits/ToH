#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f keystore.properties ]]; then
  echo "Missing keystore.properties. Copy keystore.properties.example and configure your signing key." >&2
  exit 2
fi

if [[ -x ./gradlew ]]; then
  GRADLE=./gradlew
elif command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
else
  echo "Gradle wrapper/local Gradle not found. Open the project in Android Studio or generate a wrapper first." >&2
  exit 3
fi

$GRADLE --no-daemon clean lintRelease assembleRelease

echo
find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print
