#!/usr/bin/env bash
# GitHub-hosted Linux PR runner only; reuse APKs built in the same job.
set -euo pipefail
image='system-images;android-29;default;x86_64'
results=app/build/room-migration-results
mkdir -p "$results"
test -c /dev/kvm
# Make KVM accessible to the runner group on this ephemeral VM.
sudo chgrp "$(id -gn)" /dev/kvm
sudo chmod g+rw /dev/kvm
sdkmanager emulator "$image"
avd=track-at-room-ci
avdmanager create avd --force -n "$avd" -k "$image" <<< 'no'
"$ANDROID_HOME/emulator/emulator" -avd "$avd" -port 5554 -accel on -no-window -no-audio \
    -no-boot-anim -no-snapshot -gpu swiftshader_indirect > "$results/emulator.log" 2>&1 &
emulator_pid=$!
export ANDROID_SERIAL=emulator-5554
cleanup() {
    timeout 10 adb logcat -d -t 500 > "$results/logcat.txt" 2>&1 || true
    timeout 5 adb emu kill >/dev/null 2>&1 || true
    kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
timeout 180 adb wait-for-device
for attempt in {1..75}; do
    if [[ "$(timeout 5 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == 1 ]]; then break; fi
    kill -0 "$emulator_pid"
    sleep 2
done
[[ "$(timeout 5 adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
timeout 45 adb install app/build/outputs/apk/debug/app-debug.apk
timeout 45 adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout 90 adb shell am instrument -w -r \
    -e class com.jaguar.attendancetracker.backend.migrations.LegacyAttendanceMigrationTest \
    com.jaguar.attendancetracker.b.test/androidx.test.runner.AndroidJUnitRunner \
    | tr -d '\r' | tee "$results/instrumentation.txt"
# adb can return zero on assertion failure or a runner startup error.
grep -Eq '^OK \(2 tests\)$' "$results/instrumentation.txt"
grep -Fxq 'INSTRUMENTATION_CODE: -1' "$results/instrumentation.txt"
