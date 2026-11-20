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

## Leave Planner calculation foundation

The pure JVM projection accepts a prospective inclusive range up to 366 days,
confirmed current attended/total counts, an attendance target, and effective
occurrences with explicitly declared coverage. It does not touch storage.
Current counts must be verified against counted Present/Absent attendance;
contradictory or absent history and invalid targets cannot become a projection.
Zero attendance history is unknown. A weekly template or an empty weekend
does not prove full coverage; date/session/enrollment/semester boundaries and
timetable exceptions must be considered before the future UI declares it.
This occurrence identity describes timetable sessions; an unmapped extra
class cannot be guessed from a null session ID. Keep automatic coverage
partial or account for it only in a user-entered estimate until a separate
stable extra-class occurrence identity is modeled.
An explicit non-negative manual estimate can stand in for missing or ambiguous
schedule information, and is labeled as manual rather than confirmed.

JVM tests cover inclusive dates, weekends, unknown/partial coverage, duplicate
or moved occurrences, cross-subject ID collisions, recorded or cancelled
sessions, exact threshold comparisons, and integer bounds. The calculation has no persistence and never changes attendance.


## Leave Planner UI

Open **Today → Leave Planner**. Choose prospective inclusive whole dates,
up to 366 days. The planner reads a consistent attendance/schedule/subject/
semester/exam snapshot. Weekly entries and saved unmarked extras are shown
as incomplete context, not a complete timetable. Archived/ended subjects
or subjects outside their recorded dates are not projected. Date changes
clear manual estimates. Leave is not saved, and no marks or reminders are made.

1. Without a timetable, leave an estimate blank: no projection. Type 0
   explicitly for a subject with verified attendance, then type a positive
   whole number; compare current and projected fractions and threshold.
   A missing/zero history or inconsistent cached counts must not produce a
   reassuring result. No attendance should change after any scenario.
2. Use a range including scheduled, marked and cancelled classes and saved
   unmarked extras; only the scoped unmarked entries are counted for context.
   Test weekends and recorded semester/subject/session dates.
3. Dated exams overlapping the range appear even from archived terms or no
   term. Undated exams have a separate uncertainty label. Board deadlines
   are not included in this slice.
4. Manual checks: real date picker, selection in time zones west of UTC,
   range reset and Back, large text/TalkBack/IME scrolling and old routes.
   The selective API 29 runner tests complete read-only Room fixtures and
   Compose no-timetable manual zero/exam sections, not the full picker flow.


## Date-only picker regression

A Material date picker returns UTC midnight for the chosen calendar day. Use
that calendar day unchanged when opening Day View or editing subject/semester
dates. The device zone still determines paths based on “today”. Existing
stored dates are never adjusted or rewritten automatically.

Use a test profile west of UTC (e.g. America/Los_Angeles), then compare UTC and
Asia/Kolkata. Choose winter and summer dates in Day View; the title and shown
records must match the selected calendar day. Open and save an existing
subject and semester without editing their dates; dates must not move.
Change each date, cancel a separate edit, and clear an optional end date.
Also recheck Board, Exams and Leave date selection. The JVM test restores
its temporary test timezone and covers all three zones. The selective API 29
tests exercise actual subject/semester edit sheets in a west zone, not
manual calendar navigation or a stored-data correction.

## Attendance Insights calculation

The Insights aggregator is read-only and uses saved attendance only.
It counts saved attendance across eight local Monday–Sunday calendar weeks;
the final week stops at the supplied local date. All-subject scope includes
historical and archived subjects; an explicit subject scope narrows the
report. Present and Absent count toward the rate. Unmarked and Cancelled
stay separate, and a week without marked records has no rate. No calendar
or timetable entries are materialized for this report.

A set of multiple regular rows with the same subject/session/calendar-day
identity, or a regular row without that identity, is flagged as excluded.
Independently saved Extra classes keep their own record IDs. The tests cover
mixed/empty records, archived-inclusive versus scoped behavior, year/week
boundaries, future exclusion and identity conflicts. The UI displays excluded-row counts separately and never treats them as absences.


## Attendance Insights screen

Open **Today → Insights**; no sticky setting or new tab is needed. It reads
eight calendar weeks from saved attendance only, rolling with the local
date. The latest week stops today; future records and the timetable do not
inflate the report. Bars compare Present and Absent proportions only, with
an equivalent text breakdown. Unmarked, Cancelled and ambiguous excluded
rows are reported separately; they are not part of the denominator.

1. Use a test profile with marked, cancelled and unmarked records across
   two weeks. Confirm the latest week is labeled partial through today;
   important denominator and counts are readable without color.
2. Choose a specific subject; archived terms are labeled and included in
   All subjects. Confirm a no-marks scope shows no percentage or bar.
3. Verify saved rows stay unchanged after switching scopes or generating
   no reports. Large text, dark mode, TalkBack and Back return to Today;
   existing attendance routes are still available.

The existing selective API 29 runner verifies the actual Room snapshot makes
no scheduled rows, and the Compose filter/archived label/denominator/no-marks
workflow. Hands-on visual and full navigation checks remain manual.


## Campus Brief launcher widget

Add the separate **Campus Brief** widget from your launcher. The original
Tracker/Overview widget and subject links remain independently available.
Campus Brief displays aggregate counts only: today’s saved Present/Absent
marks and saved unmarked rows, excluding cancellations. It does not project
or write attendance from the timetable. Larger sizes add undone tasks with
a due date and dated exams in the seven days starting today. No titles,
classes, notes, names, or timestamps of individual records appear on it.
Counts are still visible information on the home screen; add only if desired.

1. Test compact/expanded sizes, light/dark, large font, TalkBack, and the
   **Open Today** action when Track-At is cold or already on another screen.
   Back and the existing Tracker subject shortcuts still work.
2. Edit attendance, a dated task and an exam separately; return to the
   launcher. An installed widget is refreshed best-effort when the app leaves
   the foreground. No timed work/alarm/network/notification is added; without
   launching/revisiting the app or a launcher refresh, the as-of date may
   remain old. Rotation does not request another app-departure refresh.
3. Compare counts with/without an unmarked saved extra, cancelled session,
   done/undated task, undated exam and a future or overdue date. Read
   failures show “Counts unavailable” rather than a false zero.
4. The API 29 runner tests the real aggregate query, both manifest receivers,
   and an explicit warm Today intent. It does not drive the launcher widget
   pin, actual resize, RemoteViews draw, screenshot, or launcher refresh timing.

## Archived semesters

On the subject Dashboard, switch between **Active** and **Archived**. An
archived term shows its dates, subjects, and saved attendance totals only.
It does not link to editing, deletion or marking. The numbers are the saved
subject totals, not a new calculation from the attendance log. This does
not change how Day View, Today, or the widgets treat dates or scheduling.

1. Compare a fixture containing archived and active semesters, including an
   archived term with no subjects. Only Active offers the existing create,
   edit, delete and subject-detail actions.
2. Expand/collapse an Active semester, switch to Archived, then press Back.
   The Active scope and its expansion/scroll state should return. Back from
   Active should still leave the Dashboard normally. Check narrow width,
   TalkBack and large fonts.
3. Compare an archived subject's records before and after browsing. The
   focused API 29 test verifies Room scope separation and intact historical
   marks, plus returning to retained Active state without exposing active
   actions in Archived. Creation/archive/restore are not part of browsing.
