"""Publisher consumes only the immutable artifact from its successful build dependency."""
import hashlib
import json
import os
from pathlib import Path
from release_pipeline import publication_guard, git

state = json.loads(Path('dist/metadata.json').read_text())
expected = {'track-at-production.apk', 'metadata.json', 'SHA256SUMS'}
if {p.name for p in Path('dist').iterdir()} != expected:
    raise ValueError('Unexpected candidate files')
if state['channel'] != 'production' or state['source_sha'] != os.environ['GITHUB_SHA'] or state['source_tree'] != git('rev-parse', 'HEAD^{tree}') or state['run_id'] != int(os.environ['GITHUB_RUN_ID']) or state['tag'] != os.environ['GITHUB_REF_NAME']:
    raise ValueError('Candidate does not belong to this exact successful build')
actual = hashlib.sha256(Path('dist/track-at-production.apk').read_bytes()).hexdigest()
if actual != state['apk_sha256'] or Path('dist/SHA256SUMS').read_text() != actual + '  track-at-production.apk\n':
    raise ValueError('Candidate checksum mismatch')
publication_guard(state)
