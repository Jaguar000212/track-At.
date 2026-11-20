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
