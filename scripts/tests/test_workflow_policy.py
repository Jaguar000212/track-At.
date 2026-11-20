from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class WorkflowPolicyTest(unittest.TestCase):
    def test_pr_only_read_only_and_cancellable(self):
        text = (ROOT / '.github/workflows/pr-checks.yml').read_text()
        self.assertIn('branches: [dev]', text)
        self.assertIn('ready_for_review', text)
        self.assertIn('converted_to_draft', text)
        self.assertIn('name: PR checks', text)
        self.assertIn('contents: read', text)
        self.assertIn('cancel-in-progress: true', text)
        self.assertIn('persist-credentials: false', text)
        self.assertIn('cache-read-only: true', text)
        for prohibited in ['pull_request_target:', 'workflow_dispatch:', 'push:', 'paths:', 'secrets.', 'contents: write']:
            self.assertNotIn(prohibited, text)

    def test_ready_code_has_real_checks_and_drafts_are_explicit(self):
        text = (ROOT / '.github/workflows/pr-checks.yml').read_text()
        for task in [':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebugAndroidTest']:
            self.assertIn(task, text)
        self.assertIn("!github.event.pull_request.draft && steps.changes.outputs.android != 'false'", text)
        self.assertIn('Draft: CI policy checks only', text)
        self.assertIn('Device tests are not executed', text)
        self.assertIn('steps.changes.outputs.channels', text)
        self.assertIn('-PtrackAtReleaseChannel=dev', text)
        self.assertIn('-PtrackAtReleaseChannel=production', text)

    def test_migration_fixture_uses_one_selective_step(self):
        text = (ROOT / '.github/workflows/pr-checks.yml').read_text()
        self.assertEqual(1, text.count('bash scripts/run_room_migration_tests.sh'))
        self.assertIn("!github.event.pull_request.draft && steps.changes.outputs.migrations != 'false'", text)
        self.assertIn('timeout-minutes: 12', text)
        self.assertIn('app/build/room-migration-results/', text)
