#!/usr/bin/env bash
# GitHub-hosted Linux PR runner only; reuse APKs built in the same job.
set -euo pipefail
image='system-images;android-29;default;x86_64'
results=app/build/room-migration-results
mkdir -p "$results"
app_apk=app/build/outputs/apk/debug/app-debug.apk
test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
aapt="$ANDROID_HOME/build-tools/36.0.0/aapt"
apk_package() {
    "$aapt" dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p"
}
app_id="$(apk_package "$app_apk")"
test_id="$(apk_package "$test_apk")"
[[ "$app_id" == com.jaguar.attendancetracker.dev ]]
[[ "$test_id" =~ ^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$ && "$test_id" != "$app_id" ]]
runner="$test_id/androidx.test.runner.AndroidJUnitRunner"
printf 'Debug APK target: %s; instrumentation: %s\n' "$app_id" "$runner"
test -c /dev/kvm
# Make KVM accessible to the runner group on this ephemeral VM.
sudo chgrp "$(id -gn)" /dev/kvm
sudo chmod g+rw /dev/kvm
sdkmanager emulator "$image"
avd=track-at-room-ci
export ANDROID_USER_HOME="$RUNNER_TEMP/track-at-android"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p "$ANDROID_AVD_HOME"
avdmanager create avd --force -n "$avd" -k "$image" --path "$ANDROID_AVD_HOME/$avd.avd" <<< 'no'
emulator="$ANDROID_HOME/emulator/emulator"
"$emulator" -list-avds | grep -Fx "$avd"
"$emulator" -avd "$avd" -port 5554 -accel on -no-window -no-audio \
    -no-boot-anim -no-snapshot -gpu swiftshader_indirect > "$results/emulator.log" 2>&1 &
emulator_pid=$!
export ANDROID_SERIAL=emulator-5554
cleanup() {
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
        tail -60 "$results/emulator.log" >&2 || true
    fi
    timeout 10 adb logcat -d -t 500 > "$results/logcat.txt" 2>&1 || true
    timeout 5 adb emu kill >/dev/null 2>&1 || true
    kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
for attempt in {1..90}; do
    if [[ "$(timeout 5 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == 1 ]]; then break; fi
    kill -0 "$emulator_pid"
    sleep 2
done
[[ "$(timeout 5 adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
timeout 45 adb install "$app_apk"
timeout 45 adb install "$test_apk"
timeout 10 adb shell pm list instrumentation "$app_id" | tr -d '\r' | tee "$results/registered-instrumentation.txt"
grep -Fxq "instrumentation:$runner (target=$app_id)" "$results/registered-instrumentation.txt"
timeout 90 adb shell am instrument -w -r \
    -e class com.jaguar.attendancetracker.backend.migrations.LegacyAttendanceMigrationTest,com.jaguar.attendancetracker.backend.migrations.PlannerRestoreApplyTest,com.jaguar.attendancetracker.backend.migrations.ExamBackupRestoreTest,com.jaguar.attendancetracker.ui.imExport.PlannerBackupFilesTest,com.jaguar.attendancetracker.ui.imExport.PlannerBackupContentTest,com.jaguar.attendancetracker.ui.board.BoardScreenTest,com.jaguar.attendancetracker.ui.board.BoardPersistenceTest,com.jaguar.attendancetracker.ui.exams.ExamsScreenTest,com.jaguar.attendancetracker.ui.today.TodayContentTest,com.jaguar.attendancetracker.ui.leave.LeavePlannerTest,com.jaguar.attendancetracker.ui.components.PickerCalendarDateComposeTest,com.jaguar.attendancetracker.ui.insights.InsightsScreenTest,com.jaguar.attendancetracker.ui.widget.CampusBriefWidgetTest,com.jaguar.attendancetracker.ui.dashboard.ArchivedTermsTest,com.jaguar.attendancetracker.ui.scheduleView.TimetableReorderTest,com.jaguar.attendancetracker.backend.rollover.TermRolloverTest,com.jaguar.attendancetracker.ui.rollover.RolloverScreenTest,com.jaguar.attendancetracker.backend.reminders.ExamReminderRuntimeTest \
    "$runner" \
    | tr -d '\r' | tee "$results/instrumentation.txt"
# adb can return zero on assertion failure or a runner startup error.
grep -Eq '^OK \(44 tests\)$' "$results/instrumentation.txt"
grep -Fxq 'INSTRUMENTATION_CODE: -1' "$results/instrumentation.txt"
for fixture in populatedV8PreservesAttendanceAndScheduleWithDefaultOrder \
    populatedV9PreservesAttendanceAndScheduleWithExistingOrder \
    populatedV10KeepsAttendanceAndSupportsBoardPersistence \
    populatedV11KeepsDataAndDetachesExamsOnlyWhenSemesterDeleted \
    populatedV12AddsDormantBoardPortabilityWithoutChangingOldBackups \
    restoresConservativelyUpdatesOnlyDerivedTotalsAndRepeatsAsNoOp \
    stalePreviewWritesNothingAndInconsistentLocalTotalsDoNotBlockBoard \
    lateBoardInsertFailureRollsBackEarlierWritesAndKeepsExistingData \
    partialLegacyBackupCountsCannotInventMarksDuringPlannerRestore \
    plannerV3RoundTripKeepsExamsAndBoardPortabilityAcrossReopen \
    plannerV3KeepsConflictingParentsAndChildrenAndCanApplyOnlyChecklistRows \
    plannerV3PinOrChecklistChangeStalesPreviewAndLateChildFailureRollsBack \
    completeUtf8FileRoundTripWaitsForExplicitApplyAndClosesOutput \
    badUtf8NullStreamsAndCloseFailuresCannotReportSuccess \
    previewRequiresApplyAndNoChangePreviewCannotApply \
    titleOnlyTaskCanBeEditedCompletedAndReopened \
    quickPinCancelDoesNotWriteAndExplicitSaveMakesOneUndatedBoardTask \
    boardEditsKeepCompletionAndPlannerBackupRestoresWithoutTimetable \
    v1AddsAttendanceAndBoardWithoutTouchingDatedOrUndatedExams \
    v2ExamRoundTripKeepsConflictsRejectsStaleAndRepeatsWithoutWrites \
    lateExamFailureRollsBackEarlierAttendanceAndBoardWrites \
    titleOnlyExamCanEditAndChooseAnArchivedSemester \
    datedCardReviewsReminderAndDenialNeverLooksScheduled \
    emptyTodayOffersAttendanceExamsAndSubjectDashboardWithoutTimetable \
    leaveReadsRealRowsWithoutWritesAndUsesExactOccurrenceAndExamDates \
    leaveWithoutTimetableRequiresExplicitZeroAndShowsExamsSeparately \
    unchangedSemesterStartAndEndStayOnTheirCalendarDaysInWesternZone \
    unchangedSubjectStartStaysOnItsCalendarDayInWesternZone \
    insightsRoomSnapshotIncludesArchivedButNeverMaterializesTimetable \
    insightsShowsMarkedDenominatorAndArchivedEmptyRateScope \
    campusBriefCountsOnlySelectedWindowsAndDoesNotMaterializeAttendance \
    campusBriefRegistersSeparatelyAndWarmIntentNavigatesToToday \
    quickPinWarmIntentKeepsExistingDraftAndReturnsThroughSingleBoard \
    archivedScopeIsReadOnlyAndBackRestoresActiveSelection \
    archivingGuardsMaterializationAndStaleExtraWhileKeepingHistoryAndBackup \
    archiveAndUnarchiveNeedExplicitConfirmation \
    reordersOnlyExplicitWeekdayGroupWithoutChangingOppositeRows \
    reorderRejectsCrossGroupAndArchiveChangedSinceDragWithoutWrites \
    rolloverCreatesOnlySelectedFreshCoursesAndKeepsSourceAndBackup \
    rolloverRejectsInvalidOrStaleSelectionsBeforeAnyWrites \
    lateRolloverCourseConstraintRollsBackWholeNewTerm \
    rolloverScreenReviewsSelectedCourseBeforeCreatingNewSemester \
    consentClaimsOnceAndDateRoundTripCannotReviveOldWork \
    defaultWorkManagerKeepsOneDelayedRequestPerExam; do
    grep -Fxq "INSTRUMENTATION_STATUS: test=$fixture" "$results/instrumentation.txt"
done

# Optional evidence only: launch the fresh debug app after the migration fixtures passed.
# No test, build or job result depends on screenshot capture.
if timeout 15 adb shell am start -W -n "$app_id/com.jaguar.attendancetracker.MainActivity" >/dev/null 2>&1; then
    if ! timeout 10 adb exec-out screencap -p > "$results/track-at-emulator.png"; then
        rm -f "$results/track-at-emulator.png"
    fi
fi
