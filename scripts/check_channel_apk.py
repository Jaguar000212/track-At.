"""PR validation only: unsigned release identities; no signing or publication."""
import sys
from pathlib import Path
from release_pipeline import package
from tag_policy import parse_source_version

version = parse_source_version(Path('version.properties').read_text())
channel, path = sys.argv[1:]
if channel not in {'dev', 'production'}:
    raise ValueError('Unknown channel')
expected = ('com.jaguar.attendancetracker.dev', 1, version.name + '-dev') if channel == 'dev' else ('com.jaguar.attendancetracker', version.code, version.name)
if package(path) != expected:
    raise ValueError('Unsigned channel APK identity mismatch')
print('Unsigned channel identity verified:', channel)
