# Phase 0: development validation

Feature branches target `dev`; this baseline does not change `main`, signing, releases or repository settings. Keep related changes in small coherent commits, open a draft PR while developing, then mark it ready for review for full Android checks. Do not interpret a draft's successful lightweight check as Android validation.

## Checks and cost

One Linux workflow per PR update, targeting `dev` only. Superseded runs for the same PR cancel. No duplicate branch-push builds, emulator matrix, APK uploads or signing secrets. Unsigned release checks are selective for channel/build configuration changes. Reports remain available for seven days. Gradle dependency caching is read-only in PRs, including forks; no privileged cache-writing job executes PR code. There is no new cache-population run just to save build time; measure real timings before adding one.

`PR checks` always reports, including documentation-only changes. Drafts run CI-policy tests only. Ready code PRs build debug, run JVM tests and lint, and compile the instrumentation APK. A narrow documentation allowlist skips Android work; workflow/build/resource/script changes, renames from code, unknown paths or diff failures require full validation. No `paths` filter leaves a missing check. Repository branch protection is not configured by this change; an owner may later require `PR checks` on `dev`.

## Local commands

Use JDK 17, Android SDK 36 and build-tools 36.0.0, with the checked-in Gradle 8.13 wrapper:

```sh
python3 -m unittest discover -s scripts/tests -v
bash ./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Unit tests cover current attendance arithmetic and backup representations. They document current supported behavior, not a hardened backup-import guarantee. Device database transactions, destructive import confirmation, process recreation and migration require separate tests.

## Emulator/device boundary

Instrumentation compilation is not execution. The existing package smoke test targets the debug application ID (`com.jaguar.attendancetracker.dev`). Run on an emulator or device with:

```sh
bash ./gradlew :app:connectedDebugAndroidTest
```

An emulator is intentionally not an automatic Phase 0 gate: the current suite is only a package smoke test, and booting an emulator on every PR would add cost without exercising attendance or database behavior. Add focused Room/import and Compose lifecycle tests before introducing a selective emulator job. Never claim those tests passed because an APK compiled.

## Verification record

Local Python policy tests and source/diff checks can run in the assistant workspace. Android validation requires Gradle/dependency downloads and an SDK; if unavailable, record the exact blocker and leave Android checks pending. A draft PR does not run those checks until marked ready. No application behavior or release signing changes are intended in this baseline.

### Known baseline gaps, not changed here

- The current floating-point make-up calculation can overcount at some thresholds: 3 attended of 5 at an 80% target returns 6 rather than the minimum 5. Targets 0/100 also need explicit domain handling. Phase 0 tests cover established 75% behavior without enshrining those defects as correct.
- Backup serialization tests do not execute ViewModel migration/import transactions. Unsupported versions and provider streams need a dedicated hardening phase; no claim is made that imports are fully validated.
- The original debug instrumentation package assertion lacked the `.b` suffix; the baseline corrected that assertion; the separate Dev-channel phase then consistently moves debug builds and that assertion to `.dev`, leaving the public ID unchanged.

Ready PRs touching build/channel/workflow configuration additionally compile/test/lint both explicit **unsigned** release configurations and inspect their APK identities. This selective check catches Dev suffix/versionCode mistakes without signing, tagging or publishing. Ordinary UI-only PRs retain the shorter debug gate. A classifier error takes the full path.

## Phase 2: Today front door (Android)

The new Today tab reads the current local day's saved attendance and eligible timetable slots.
It does not create records. Use **Record attendance** to open Day View for today's date and
its existing marking and **Extra Class** actions. No timetable is required to log an extra
class, but a subject is still required. Historical dates remain in Day View.

1. Fresh Dev install: Today shows the current local date and a useful empty state. Use the
   subject dashboard shortcut, create a semester and subject, and return to Today. With no
   timetable, open **Record attendance**, add an **Extra Class**, mark it, then go back.
   Today shows the saved class and correct status; reopening Today alone must not create records.
2. Add timetable entries for today, another weekday, and a future start date. Only eligible
   entries appear. Open the marking flow and verify a timetable slot becomes one saved row,
   not both rows, and the number of classes to mark stays the same until a status changes.
   Mark one present, one absent, and one cancelled; verify textual status,
   counts, and no duplicate of the cancelled slot. Verify a prior week's attendance does not
   hide today's recurrence.
3. Browse to a different day, return to Today, then use **Record attendance**: it must open
   the current date. Background and resume after a date change; Today should refresh (while
   continuously visible, allow up to 30 seconds).
4. Open Dashboard, Schedule, Day and Today from the bottom bar and check selected states,
   system Back, subject details, Help topics and import/export reachability. From the
   overview widget use a subject shortcut, then Back; the same subject should open and
   Back should return to Today.
5. Verify TalkBack announces all four tab names, the selected tab, headings and status
   text; verify both actions and scrolling at large font size on a narrow device, light
   and dark appearance, landscape, and a wide window. Confirm Today does not depend on
   status color to communicate attendance.

JVM tests cover the Today projection. A Compose instrumentation test exercises the empty
state and both actions; CI compiles it, but it must be executed separately on a device
or emulator. This slice does not include a database migration or test a public APK upgrade.


## Room migration: populated v8, v9, v10 and v11 to v12

For ready PRs that touch Room schema/migrations or their fixtures, the existing
Linux PR check runs only the focused Room migration and internal restore tests on a single API 29
emulator. It reuses the debug APKs already built by the normal job; no separate
workflow, matrix, APK uploads, signing keys, or release is involved. The emulator
step has a 12-minute limit and publishes test output with the existing reports.
A skipped emulator step is not a migration pass.

On an Android emulator or a spare device, run:

```sh
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jaguar.attendancetracker.backend.migrations.LegacyAttendanceMigrationTest,com.jaguar.attendancetracker.backend.migrations.PlannerRestoreApplyTest
```

Four populated fixtures open through Room with the production migration chain.
The v8 and v9 fixtures cover historical upgrades and verify that the Board
table starts empty. The independent v10 fixture under
`app/src/androidTest/assets/room/attendance-v10.sql` verifies that all four
original tables keep their schema, indexes and populated rows; it also
checks that Board entries with and without a due date survive a database
reopen and do not alter attendance data. The v11 fixture verifies all five
existing tables and rows are unchanged, and that archiving preserves exam
links while deleting a semester detaches exams without deleting them.
These emulator fixtures do not
prove a same-signed public APK upgrade.

The Exams migration is additive. Legacy attendance exports stay at v10; the
separate Planner backup includes Exams as of Planner v2.
No destructive fallback or signing changes.

## Internal planner backup format prototype

JVM codec tests cover a complete planner snapshot (attendance JSON v10 plus
Board rows, including archived terms and undated tasks), exact stable IDs,
nullable fields, and empty versus missing collections. They also check
duplicate names and IDs, malformed/unsupported inputs and broken parent
references. The existing attendance-only import/export still uses its own
unchanged v10 path.

Planner files and legacy attendance transfers are separate and neither is an
encrypted vault. Board items are included in the complete planner backup.

The internal keep-local merge preview accepts a validated complete planner
snapshot and a complete local snapshot. Its JVM tests cover inserts, identical
rows, conflicts and dependency blocks, including both cross-snapshot directions
where an orphaned attendance reference would start pointing at another subject.
It preserves genuine historical orphans and never retargets a UUID.

The internal Apply path uses the same preview and re-reads all five tables in a
single Room transaction before writes. A stale preview writes nothing; accepted
rows insert without replacing existing records. The snapshot and confirmation
handle contain private records; do not log, serialize or persist them. This
path is used only by the separate Planner backup screen, not by legacy attendance transfer.

The existing selective API 29 step now also runs the three named internal apply
tests in `PlannerRestoreApplyTest`. They verify that a repeated merge with a
fresh preview is a no-op, a stale preview does not write, an inconsistent
historical total does not prevent unrelated Board work, and a late SQLite
trigger rolls back the entire restore. The only updates allowed are the two
cached subject count columns when the source/local counts agree with their
attendance rows and accepted marks change those counts. Present/Absent count
toward total (Present counts as attended); cancelled and unmarked rows do not.
New subjects' counters reflect only accepted records. No attendance history,
subject fields, or planner items are otherwise overwritten or deleted.


## Planner backup Android file flow

On Dev, open Dashboard → Import or export data → **Planner backup**. The
complete file includes private data and is not encrypted. The system picker
controls the chosen location; there is no automatic share/upload. Planner
files are not attendance-transfer files (that import action directs you to
Planner backup). Current legacy files still use the attendance transfer path.

1. Cancel each picker, choose Restore and Cancel the preview, or leave the
   screen while reviewing. Nothing should be added. Reopening should require a
   fresh selection/review. Rotation never serializes the preview.
2. Save a populated backup to a user-selected local test folder. Check file
   contents and that the app only announces success after write/close. Try a
   failing or revoked provider if available: report failure, not success (the
   selected file may be incomplete; no automatic deletion).
3. Restore on a separate test profile/install: review per-section counts and
   Apply explicitly. Try same backup twice, a malformed/truncated file,
   invalid UTF-8, unsupported version, and a legacy-only export; do not accept
   any failed file or treat an empty/incomplete read as successful. Existing
   conflicting records must remain local. If the app state changes before
   Apply, choose the file again and review anew.
4. Check narrow screen / large font, TalkBack headings and error announcements,
   dismissal and focus return. Verify the existing attendance transfer and
   subject/widget routes remain reachable.

The selective API 29 CI step also executes two stream/Room tests and one
Compose confirmation test. They cover UTF-8 bytes through
in-memory streams, malformed UTF-8, null/failed-close streams, cancellation
and explicit Apply. They do not drive DocumentsUI, a third-party file
provider, in-place signed upgrades, or the entire navigation stack.


## Board MVP

1. On a fresh Dev build without semesters, subjects or timetable, open
   **Board** and add a title-only task. Blank titles cannot be saved. Cancel a
   draft and verify the list is unchanged.
2. Edit the task, add/remove its due date, mark it done, find it under **Done**
   and choose **Reopen task**. Verify that status changes do not alter the
   title/date and edits on completed tasks do not reopen them.
3. Close/reopen the app; the saved task and due date should persist. Save a
   Planner backup and restore on another test install/profile. Confirm the
   same Board items and statuses appear; no timetable is created. Recheck
   Today, Day, Schedule, Dashboard and system Back; use handset large font,
   TalkBack, dark mode, and a standalone picker check including a time zone
   west of UTC (the stored due date should be the selected calendar day).
4. No delete, reminders, courses, attachments or sharing from Board.
   Draft editor contents are in memory; backing out or recreating the screen
   can discard unsaved changes. Saved tasks remain unaffected.

The same selective API 29 job adds one Board Compose workflow test and one
Room/file-stream round trip. The emulator uses controlled streams, not the
real system picker or a third-party file provider.


## Planner v2 exam compatibility

The visible Planner backup screen writes v2 and accepts both v1 and v2.
Its attendance section remains legacy v10 JSON. V2 shows exam preview counts
and v1 states that it has no exams section. V2 keeps the same
`track-at-planner` discriminator, requires a complete exams array, and cannot
be opened by older builds that only understand Planner v1. The legacy
attendance-transfer importer recognizes both planner versions as separate.

The API 29 gate validates that Planner v1 adds attendance and Board data
without modifying dated or undated local exams, and that exam-only changes
do not stale a v1 plan. Internal Planner v2 tests use actual Room and controlled
file streams to check complete exam round trips, stable IDs, keep-local
conflicts, exam-only staleness, idempotent repeat, empty-by-design exams
sections, and an intentional late exam insert failure rolling back all writes.
No reminder settings or scheduling are added.


## Exams MVP

1. From **Today → Exams**, add an exam with a title only before creating any
   timetable or semester. It appears under **Undated**, with **Date TBD** and
   **No semester**. Blank titles cannot be saved. Edit the title and choose
   or clear date and semester, verifying dated exams show under **Scheduled**.
2. Link an archived semester and verify its archived label, while no semester
   remains a valid choice. Archive should retain the link; deleting a semester
   should detach the exam instead of deleting it. Do not delete real semester
   data just to test this; use a test profile/fixture.
3. Rotate while editing; unsaved title/date/semester should remain in memory
   during ordinary configuration recreation. The active date/semester chooser
   can return to the editor. Drafts are not written to disk or recovered after
   process death. Cancel/Back should not save an editing draft.
4. Save a Planner backup and verify its warning that older Dev builds cannot
   open v2. Restore a v2 file and review the Exam counts before Apply; restore
   a v1 file and verify it explicitly says exams stay unchanged. Check the
   real Android DocumentsUI, selected provider, TalkBack, large fonts, narrow
   navigation and Back, and a date selection west of UTC.
5. Board, Day, Schedule, Dashboard, the subject/widget entry points, and legacy
   attendance v10 transfer remain usable. No reminder scheduling or exam deletion.

The single selective API 29 step covers the Today entry action, title-only
and archived-term editing, v2 exam counts/v1 review text, the existing v1/v2
Room/stream tests, and populated migration fixtures. Actual Android document
provider, date picker selection, device rotation/navigation and accessibility
checks remain manual.
