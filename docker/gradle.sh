#!/usr/bin/env bash
# Runs Gradle tasks inside Docker: docker/gradle.sh testDebugUnitTest assembleDebug
# The source is copied into the container, so build output never lands in the repo
# as root; only the debug APK is copied out to dist/ (owned by you).
# Named volumes keep the Gradle cache and the debug keystore between runs; a new
# keystore each run would force uninstalling the app before every update.
# GODATA_URL/USER/PASS, if set, also run the live test against a real server.
set -euo pipefail
cd "$(dirname "$0")/.."
docker build -q -t go-data-android-kt -f docker/Dockerfile.android docker >/dev/null
mkdir -p dist
docker run --rm \
  -v "$PWD":/src:ro \
  -v "$PWD/dist":/out \
  -v go-data-android-gradle:/gradle-cache \
  -v go-data-android-keys:/root/.android \
  -e GRADLE_USER_HOME=/gradle-cache \
  -e GODATA_URL -e GODATA_USER -e GODATA_PASS \
  go-data-android-kt \
  sh -c "mkdir /work \
    && tar -C /src --exclude=./dist --exclude=./.git --exclude=./.gradle \
         --exclude=./build --exclude=./app/build -cf - . | tar -C /work -xf - \
    && cd /work && ./gradlew --no-daemon --console=plain $* \
    && if [ -f app/build/outputs/apk/debug/app-debug.apk ]; then \
         install -m644 -o $(id -u) -g $(id -g) app/build/outputs/apk/debug/app-debug.apk /out/go-data.apk \
         && echo 'APK: dist/go-data.apk'; fi"
