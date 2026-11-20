#!/bin/sh
# Local build (needs JDK 17+ and the Android SDK; Android Studio installs both).
# Output: SyncSound.apk in this folder.
set -e
cd "$(dirname "$0")"
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk SyncSound.apk
echo "Done -> $(pwd)/SyncSound.apk"
