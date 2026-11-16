#!/usr/bin/env bash
# Installs dist/go-data.apk on the USB-connected phone (USB debugging on) and opens it.
# The adb key lives in the go-data-android-keys volume, so the phone asks to authorize only once.
set -euo pipefail
cd "$(dirname "$0")/.."
docker run --rm --privileged \
  -v /dev/bus/usb:/dev/bus/usb \
  -v "$PWD/dist":/dist:ro \
  -v go-data-android-keys:/root/.android \
  go-data-android-kt \
  sh -c 'adb start-server >/dev/null && adb wait-for-usb-device && adb install -r /dist/go-data.apk && adb shell am start -n ar.godata.monitor/.MainActivity'
