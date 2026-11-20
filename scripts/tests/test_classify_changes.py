import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("classifier", ROOT / "scripts/classify_changes.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ClassifierTest(unittest.TestCase):
    def test_narrow_allowlist(self):
        self.assertTrue(module.docs_only(["README.md", "docs/testing.md"]))
        for path in ["app/src/main/res/values/strings.xml", ".github/workflows/ci.yml", "build.gradle.kts", "scripts/check.py", "docs/build.sh", "docs/../app/code.md", "gradle.properties", "app/src/main/AndroidManifest.xml"]:
            self.assertFalse(module.docs_only(["README.md", path]), path)
        self.assertFalse(module.docs_only([]))

    def test_channel_configuration_changes(self):
        self.assertTrue(module.channel_changes(["app/build.gradle.kts"]))
        self.assertTrue(module.channel_changes(["version.properties"]))
        self.assertTrue(module.channel_changes([".github/workflows/dev-build.yml"]))
        self.assertFalse(module.channel_changes(["docs/testing.md"]))
        self.assertFalse(module.channel_changes(["app/src/main/java/Screen.kt"]))

    def test_errors_require_android(self):
        for base, head in [(None, None), ("main", "a" * 40), (";bad", "b" * 40)]:
            with self.assertRaises(ValueError):
                module.changed_paths(base, head)

    def test_main_falls_back_to_full_validation_on_diff_error(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            with patch.dict("os.environ", {"GITHUB_OUTPUT": str(output)}), patch.object(module, "changed_paths", side_effect=subprocess.TimeoutExpired("git", 30)):
                module.main()
            self.assertEqual("android=true\nchannels=true\nmigrations=true\n", output.read_text())

    def test_migration_emulator_scope(self):
        prefix = "app/src/main/java/com/jaguar/attendancetracker/"
        self.assertTrue(module.migration_changes([prefix + "backend/migrations/Migration9-10.kt"]))
        self.assertTrue(module.migration_changes([prefix + "backend/AttendanceDatabase.kt"]))
        self.assertTrue(module.migration_changes(["app/src/androidTest/assets/room/attendance-v10.sql"]))
        self.assertFalse(module.migration_changes(["docs/testing.md", prefix + "ui/today/TodayScreen.kt"]))
        self.assertFalse(module.migration_changes([prefix + "ui/imExport/AttendanceBackupFormat.kt"]))
        for path in ("backend/daos/PlannerRestoreDao.kt", "backend/repositories/PlannerRestoreRepository.kt",
                     "ui/imExport/PlannerMergePreview.kt", "ui/imExport/PlannerBackupFormat.kt",
                     "ui/imExport/PlannerBackupFiles.kt", "ui/imExport/PlannerBackupScreen.kt",
                     "ui/imExport/PlannerBackupViewModel.kt", "backend/daos/BoardItemDao.kt",
                     "ui/board/BoardScreen.kt", "ui/board/BoardViewModel.kt", "backend/daos/ExamDao.kt", "ui/exams/ExamsScreen.kt", "ui/today/Today.kt", "ui/leave/LeaveScreen.kt", "ui/leave/LeaveScenario.kt", "ui/leave/LeaveViewModel.kt", "ui/components/PickerCalendarDate.kt", "ui/dayView/DayView.kt", "ui/insights/InsightsScreen.kt", "backend/daos/AttendanceRecordDao.kt"):
            self.assertTrue(module.migration_changes([prefix + path]))

    def test_rename_reports_both_paths(self):
        import os
        old = os.getcwd()
        with tempfile.TemporaryDirectory() as directory:
            try:
                os.chdir(directory)
                subprocess.run(["git", "init", "-q"], check=True)
                subprocess.run(["git", "config", "user.email", "test@example.invalid"], check=True)
                subprocess.run(["git", "config", "user.name", "Test"], check=True)
                Path("code.kt").write_text("code")
                subprocess.run(["git", "add", "."], check=True)
                subprocess.run(["git", "commit", "-qm", "base"], check=True)
                base = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
                Path("docs").mkdir()
                Path("code.kt").rename("docs/note.md")
                subprocess.run(["git", "add", "-A"], check=True)
                subprocess.run(["git", "commit", "-qm", "rename"], check=True)
                head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
                paths = module.changed_paths(base, head)
                self.assertEqual(set(paths), {"code.kt", "docs/note.md"})
                self.assertFalse(module.docs_only(paths))
            finally:
                os.chdir(old)
