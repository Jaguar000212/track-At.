import importlib.util
import sys
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import release_pipeline as pipeline


class PipelineTest(unittest.TestCase):
    def test_cross_host_redirect_drops_token(self):
        request = urllib.request.Request('https://api.github.com/test', headers={'Authorization': 'Bearer secret'})
        redirected = pipeline.SafeRedirect().redirect_request(request, None, 302, '', {}, 'https://example.invalid/artifact')
        self.assertIsNone(redirected.get_header('Authorization'))
        with self.assertRaises(ValueError):
            pipeline.SafeRedirect().redirect_request(request, None, 302, '', {}, 'http://example.invalid/artifact')

    def test_provenance_requires_intended_successful_workflow(self):
        for run in [{'event': 'pull_request', 'conclusion': 'success', 'path': '.github/workflows/dev-build.yml'},
                    {'event': 'push', 'conclusion': 'failure', 'path': '.github/workflows/dev-build.yml'},
                    {'event': 'push', 'conclusion': 'success', 'path': '.github/workflows/other.yml'}]:
            with self.assertRaises(ValueError):
                pipeline.verify_dev_evidence(run, 'a'*40, 'b'*64, '6.0.0')

    def test_moved_tag_blocks_publication(self):
        state = dict(channel='production', source_sha='a'*40, tag='v6.0.0')
        with patch.object(pipeline, 'api', return_value={'object': {'sha': 'a'*40}}), patch.object(pipeline, 'live_tag_commit', return_value='b'*40):
            with self.assertRaises(ValueError):
                pipeline.publication_guard(state)

    def test_signing_and_authenticated_verification_are_separate(self):
        script = (ROOT / 'scripts/sign_apk.sh').read_text()
        self.assertNotIn('release_pipeline.py finish', script)
        for name in ['dev-build.yml', 'public-release.yml']:
            text = (ROOT / '.github/workflows' / name).read_text()
            self.assertIn('Verify signed output and unchanged refs', text)
            self.assertIn('GH_TOKEN: ${{ github.token }}', text)
        public = (ROOT / '.github/workflows/public-release.yml').read_text()
        before_publish = public.split('  publish:')[0]
        self.assertNotIn('contents: write', before_publish)
        self.assertIn('needs: build', public)
        self.assertIn('actions/download-artifact@v4', public)

    def test_channels_are_explicit_and_no_dispatch_or_secret_fallback(self):
        dev = (ROOT / '.github/workflows/dev-build.yml').read_text()
        public = (ROOT / '.github/workflows/public-release.yml').read_text()
        self.assertIn("tags: ['dev-build/*']", dev)
        self.assertNotIn('contents: write', dev)
        self.assertIn("tags: ['v*']", public)
        for text in [dev, public]:
            self.assertNotIn('workflow_dispatch:', text)
            self.assertIn('cancel-in-progress: false', text)
            self.assertIn(':app:testReleaseUnitTest', text)
            self.assertIn(':app:lintRelease', text)
            self.assertIn('prepare ', text)
        self.assertNotIn('gh release', dev)
        self.assertIn('--verify-tag --draft', public)
        self.assertNotIn('--clobber', public)
        self.assertNotIn('--overwrite', public)
