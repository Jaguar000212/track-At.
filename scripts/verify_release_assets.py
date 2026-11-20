"""Verify a newly staged draft's server hashes before making it public. Never delete/replace."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import urllib.parse
from release_pipeline import publication_guard

release = json.loads(subprocess.check_output(['gh', 'api', 'repos/Jaguar000212/track-At./releases/tags/' + urllib.parse.quote(sys.argv[1], safe='')], text=True))
state = json.loads(Path('dist/metadata.json').read_text())
publication_guard(state)
if release['tag_name'] != state['tag'] or release['name'] != state['tag']:
    raise ValueError('Staged release identity mismatch')
if not release['draft']:
    raise ValueError('Expected newly staged draft')
expected = {p.name: p for p in Path('dist').iterdir() if p.is_file()}
assets = release['assets']
if len(assets) != len(expected) or {a['name'] for a in assets} != set(expected):
    raise ValueError('Incomplete release assets')
for asset in assets:
    path = expected[asset['name']]
    if asset['size'] != path.stat().st_size or asset.get('digest') != 'sha256:' + hashlib.sha256(path.read_bytes()).hexdigest():
        raise ValueError('Release checksum mismatch; draft retained for manual review')
