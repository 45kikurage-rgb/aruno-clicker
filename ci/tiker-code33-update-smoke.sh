#!/usr/bin/env bash
set -euo pipefail
api=$1
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "$api"
mapfile -t app_apks < <(find candidate/android-app/app/build/outputs/apk/atlas/debug -name '*.apk' -type f)
mapfile -t test_apks < <(find candidate/android-app/app/build/outputs/apk/androidTest/atlas/debug -name '*.apk' -type f)
test "${#app_apks[@]}" = 1
test "${#test_apks[@]}" = 1
adb install "${app_apks[0]}"
adb install "${test_apks[0]}"
adb shell am instrument -w -r \
  -e class jp.aruno.clicker.ui.TikerUpdateButtonTest \
  jp.aruno.clicker.test/androidx.test.runner.AndroidJUnitRunner | tr -d '\r' | tee "tiker-ui-api${api}.txt"
grep -Eq 'OK \(1 test\)' "tiker-ui-api${api}.txt"
