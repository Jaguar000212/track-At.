"""Fail closed to Android validation; only an explicit documentation allowlist skips it."""
import os
import re
import subprocess
import sys


def docs_only(paths):
    return bool(paths) and all(
        path in {"README.md", "LICENSE", "LICENSE.md"}
        or (path.startswith("docs/") and path.endswith(".md") and ".." not in path.split("/"))
        for path in paths
    )


def changed_paths(base, head):
    if not all(re.fullmatch(r"[0-9a-f]{40}", sha or "") for sha in (base, head)):
        raise ValueError("Expected exact commit hashes")
    # --no-renames reports both the deletion and addition, so moving code into docs is not skipped.
    data = subprocess.check_output(["git", "diff", "--no-renames", "--name-only", "-z", base, head, "--"], timeout=30)
    return [p.decode("utf-8", errors="strict") for p in data.split(b"\0") if p]


def channel_changes(paths):
    return any(path.startswith((".github/", "release/", "gradle/"))
               or path.endswith((".gradle", ".gradle.kts"))
               or path in {"version.properties", "gradle.properties", "scripts/release_pipeline.py", "scripts/tag_policy.py", "scripts/sign_apk.sh", "scripts/verify_candidate.py", "scripts/verify_release_assets.py", "scripts/check_channel_apk.py"}
               for path in paths)


def migration_changes(paths):
    package = "com/jaguar/attendancetracker/backend/"
    main = "app/src/main/java/" + package
    test = "app/src/androidTest/java/" + package
    board = "com/jaguar/attendancetracker/ui/board/"
    exams = "com/jaguar/attendancetracker/ui/exams/"
    leave = "com/jaguar/attendancetracker/ui/leave/"
    return any(path.startswith((main + "migrations/", main + "entities/", main + "typeConverters/", test + "migrations/", "app/src/androidTest/assets/room/",
                                    "app/src/androidTest/java/com/jaguar/attendancetracker/ui/imExport/PlannerBackup",
                                    "app/src/main/java/" + board, "app/src/androidTest/java/" + board,
                                    "app/src/main/java/" + exams, "app/src/androidTest/java/" + exams,
                                    "app/src/androidTest/java/" + leave))
               or path in {main + "AttendanceDatabase.kt", main + "CONSTANTS.kt",
                           main + "daos/PlannerRestoreDao.kt", main + "daos/BoardItemDao.kt", main + "daos/ExamDao.kt",
                           main + "repositories/PlannerRestoreRepository.kt",
                           "app/src/main/java/com/jaguar/attendancetracker/ui/imExport/PlannerMergePreview.kt",
                           "app/src/main/java/com/jaguar/attendancetracker/ui/imExport/PlannerBackupFormat.kt",
                           "app/src/main/java/com/jaguar/attendancetracker/ui/imExport/PlannerBackupFiles.kt",
                           "app/src/main/java/com/jaguar/attendancetracker/ui/imExport/PlannerBackupScreen.kt",
                           "app/src/main/java/com/jaguar/attendancetracker/ui/imExport/PlannerBackupViewModel.kt",
                           "app/src/main/java/" + leave + "LeaveScreen.kt",
                           "app/src/main/java/" + leave + "LeaveScenario.kt",
                           "app/src/main/java/" + leave + "LeaveViewModel.kt",
                           "app/src/main/java/com/jaguar/attendancetracker/ui/today/Today.kt",
                           "app/src/androidTest/java/com/jaguar/attendancetracker/ui/today/TodayContentTest.kt",
                           "app/src/main/java/com/jaguar/attendancetracker/dependencies/DatabaseModule.kt",
                           "scripts/run_room_migration_tests.sh"}
               for path in paths)


def main():
    try:
        paths = changed_paths(os.environ.get("BASE_SHA"), os.environ.get("HEAD_SHA"))
        only_docs = docs_only(paths)
        channels = channel_changes(paths)
        migrations = migration_changes(paths)
    except (ValueError, UnicodeError, OSError, subprocess.SubprocessError):
        only_docs = False
        channels = True
        migrations = True
    value = "false" if only_docs else "true"
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"android={value}\nchannels={str(channels).lower()}\nmigrations={str(migrations).lower()}\n")
    print("Documentation-only change" if only_docs else "Full Android validation required")


if __name__ == "__main__":
    main()
