"""Fail-closed tag gating, APK evidence and promotion. No secret values are printed."""
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.parse
import urllib.request
import zipfile

from tag_policy import parse_source_version, validate_candidate

REPO = 'Jaguar000212/track-At.'
MAX_BYTES = 128 * 1024 * 1024


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        if urllib.parse.urlsplit(newurl).scheme != "https":
            raise ValueError("Non-HTTPS redirect")
        redirected = super().redirect_request(request, fp, code, msg, headers, newurl)
        if urllib.parse.urlsplit(newurl).netloc != urllib.parse.urlsplit(request.full_url).netloc:
            redirected.remove_header("Authorization")
        return redirected


def api(path, binary=False):
    request = urllib.request.Request('https://api.github.com/repos/' + REPO + path,
        headers={'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
                 'Accept': 'application/octet-stream' if binary else 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'})
    with urllib.request.build_opener(SafeRedirect()).open(request, timeout=60) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError('Response exceeds limit')
    return data if binary else json.loads(data)


def pages(path, key=None):
    result = []
    for page in range(1, 101):
        data = api(path + ('&' if '?' in path else '?') + f'per_page=100&page={page}')
        batch = data[key] if key else data
        result.extend(batch)
        if len(batch) < 100:
            return result
    raise ValueError('Inventory limit reached; manual review required')


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def fingerprint(apk):
    result = subprocess.check_output([os.environ['APKSIGNER'], 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    values = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', result, re.M)
    if len(values) != 1:
        raise ValueError('Expected exactly one signing certificate')
    return values[0].lower()


def package(apk):
    result = subprocess.check_output([os.environ['AAPT'], 'dump', 'badging', str(apk)], text=True)
    match = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", result, re.M)
    if not match or 'application-debuggable' in result:
        raise ValueError('Invalid or debuggable release APK')
    return match.group(1), int(match.group(2)), match.group(3)


def workflow_runs(name):
    return pages('/actions/workflows/' + name + '/runs?event=push&status=success', 'workflow_runs')


def verify_dev_evidence(run, expected_tree, dev_pin, expected_version):
    if run['event'] != 'push' or run['conclusion'] != 'success' or run['path'] != '.github/workflows/dev-build.yml':
        raise ValueError('Unexpected provenance workflow')
    tag = run['head_branch']
    match = re.fullmatch(r'dev-build/([1-9][0-9]*)', tag)
    if not match:
        raise ValueError('Invalid provenance tag')
    number = int(match[1])
    sha = run['head_sha']
    if api('/git/commits/' + sha)['tree']['sha'] != expected_tree:
        return None
    # The immutable tag must still identify this tested commit, on the dev history.
    if git('rev-parse', f'refs/tags/{tag}^{{commit}}') != sha:
        raise ValueError('Provenance tag moved')
    subprocess.run(['git', 'merge-base', '--is-ancestor', sha, 'origin/dev'], check=True)
    artifacts = pages(f"/actions/runs/{run['id']}/artifacts", 'artifacts')
    candidates = [a for a in artifacts if a['name'] == f'track-at-dev-{number}' and not a['expired']]
    if len(candidates) != 1:
        raise ValueError('Verified Dev artifact unavailable; rebuild/test before promotion')
    raw = api(f"/actions/artifacts/{candidates[0]['id']}/zip", binary=True)
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names = archive.namelist()
        if set(names) != {'track-at-dev.apk', 'metadata.json', 'SHA256SUMS'} or len(names) != 3:
            raise ValueError('Unexpected artifact contents')
        if sum(item.file_size for item in archive.infolist()) > MAX_BYTES:
            raise ValueError('Artifact too large')
        metadata = json.loads(archive.read('metadata.json'))
        apk = archive.read('track-at-dev.apk')
        digest = hashlib.sha256(apk).hexdigest()
        if archive.read('SHA256SUMS').decode('utf-8') != digest + '  track-at-dev.apk\n':
            raise ValueError('Dev checksum file mismatch')
        if metadata.get('source_sha') != sha or metadata.get('source_tree') != expected_tree or metadata.get('run_id') != run['id'] or metadata.get('version_code') != number or metadata.get('certificate_sha256') != dev_pin or metadata.get('apk_sha256') != digest or metadata.get('version_name') != expected_version + '-dev':
            raise ValueError('Dev provenance mismatch')
        path = Path(os.environ['RUNNER_TEMP']) / 'provenance-dev.apk'
        path.write_bytes(apk)
        if fingerprint(path) != dev_pin or package(path) != ('com.jaguar.attendancetracker.dev', number, metadata['version_name']):
            raise ValueError('Dev APK identity mismatch')
    return run['id']


def prepare(channel):
    if channel not in {'dev', 'production'}:
        raise ValueError('Unsupported release channel')
    if os.environ['GITHUB_REPOSITORY'] != REPO or os.environ['GITHUB_EVENT_NAME'] != 'push' or os.environ.get('GITHUB_REF_TYPE') != 'tag':
        raise ValueError('Only repository tag pushes are accepted')
    tag = os.environ['GITHUB_REF_NAME']
    sha = os.environ['GITHUB_SHA']
    if not re.fullmatch('[0-9a-f]{40}', sha) or git('rev-parse', 'HEAD') != sha or git('rev-parse', f'refs/tags/{tag}^{{commit}}') != sha:
        raise ValueError('Tag/source mismatch')
    version = parse_source_version(Path('version.properties').read_text())
    tree = git('rev-parse', 'HEAD^{tree}')
    pins = json.loads(Path('release/signers.json').read_text())
    pin = pins['devCertificateSha256' if channel == 'dev' else 'publicCertificateSha256']
    if not re.fullmatch('[0-9a-f]{64}', pin):
        raise ValueError('Verified signing fingerprint is not configured')
    branch = 'dev' if channel == 'dev' else 'main'
    if git('rev-parse', 'origin/' + branch) != sha:
        raise ValueError('Tag must identify current ' + branch + ' head')
    refs = {name: git('rev-parse', f'refs/tags/{name}^{{commit}}') for name in git('tag', '--list', 'dev-build/*').splitlines()}
    candidate = validate_candidate(channel='dev' if channel == 'dev' else 'public', tag=tag,
        source_text=Path('version.properties').read_text(), source_sha=sha, source_tree=tree,
        branch_sha=git('rev-parse', 'origin/' + branch), branch_tree=git('rev-parse', 'origin/' + branch + '^{tree}'), existing_refs=refs)
    workflow = 'dev-build.yml' if channel == 'dev' else 'public-release.yml'
    for old_run in pages('/actions/workflows/' + workflow + '/runs?event=push', 'workflow_runs'):
        if old_run['head_branch'] == tag and old_run['head_sha'] != sha:
            raise ValueError('Tag was previously used for another commit')
    evidence = None
    if channel == 'dev':
        match = re.fullmatch(r'dev-build/([1-9][0-9]*)', tag)
        if not match or not 1 <= int(match[1]) <= 2100000000:
            raise ValueError('Invalid Dev counter')
        code = candidate.version_code; name = candidate.version_name
        for existing in git('tag', '--list', 'dev-build/*').splitlines():
            m = re.fullmatch(r'dev-build/([1-9][0-9]*)', existing)
            if m and existing != tag and int(m[1]) >= code:
                raise ValueError('Dev counter must exceed existing tags')
        for run in workflow_runs('dev-build.yml'):
            m = re.fullmatch(r'dev-build/([1-9][0-9]*)', run['head_branch'])
            if m and int(m[1]) >= code:
                raise ValueError('Dev counter already used or superseded')
    else:
        if tag != 'v' + version.name:
            raise ValueError('Public tag must match source version')
        code = candidate.version_code; name = candidate.version_name
        floor = pins['lastVerifiedPublicVersionCode']
        if type(floor) is not int or not 1 <= floor <= 2100000000 or code <= floor:
            raise ValueError('Public code must exceed verified installed baseline')
        releases = pages('/releases')
        baseline = next((r for r in releases if r['tag_name'] == 'v6.0.0' and not r['draft']), None)
        if baseline is None:
            raise ValueError('Verified legacy release baseline unavailable')
        for release in releases:
            if release['draft'] or release['prerelease']:
                continue
            assets = [a for a in release.get('assets', []) if a['name'] == 'metadata.json']
            if len(assets) > 1:
                raise ValueError('Ambiguous prior release metadata')
            if assets:
                prior = json.loads(api('/releases/assets/' + str(assets[0]['id']), binary=True))
                if type(prior.get('version_code')) is not int or not 1 <= prior['version_code'] < code or prior.get('channel') != 'production' or prior.get('application_id') != 'com.jaguar.attendancetracker' or prior.get('tag') != release['tag_name'] or 'v' + str(prior.get('version_name')) != release['tag_name']:
                    raise ValueError('Public version code is not increasing')
            elif release['published_at'] > baseline['published_at']:
                raise ValueError('Previous public release lacks verifiable version metadata')
        # Same source tree, not merely a claimed input SHA or a PR check name.
        for run in workflow_runs('dev-build.yml'):
            evidence = verify_dev_evidence(run, tree, pins['devCertificateSha256'], version.name)
            if evidence is not None:
                break
        if evidence is None:
            raise ValueError('No successful same-tree Dev build evidence')
        for run in workflow_runs('public-release.yml'):
            if run['head_branch'] == tag:
                raise ValueError('Public tag already successfully published')
    state = dict(channel=channel, tag=tag, source_sha=sha, source_tree=tree, version_code=code,
        version_name=name, application_id='com.jaguar.attendancetracker' + ('.dev' if channel == 'dev' else ''),
        certificate_sha256=pin, dev_evidence_run=evidence, run_id=int(os.environ['GITHUB_RUN_ID']))
    Path('release-state.json').write_text(json.dumps(state, sort_keys=True))
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'code={code}\nversion={name}\n')


def live_tag_commit(tag):
    obj = api('/git/ref/tags/' + urllib.parse.quote(tag, safe=''))['object']
    for _ in range(8):
        if obj['type'] == 'commit':
            return obj['sha']
        if obj['type'] != 'tag':
            break
        obj = api('/git/tags/' + obj['sha'])['object']
    raise ValueError('Unsupported tag object')


def publication_guard(state):
    branch = 'dev' if state['channel'] == 'dev' else 'main'
    if api('/git/ref/heads/' + branch)['object']['sha'] != state['source_sha'] or live_tag_commit(state['tag']) != state['source_sha']:
        raise ValueError('Branch or tag changed during build; publication blocked')


def finish(apk):
    state = json.loads(Path('release-state.json').read_text())
    publication_guard(state)
    if fingerprint(apk) != state['certificate_sha256'] or package(apk) != (state['application_id'], state['version_code'], state['version_name']):
        raise ValueError('Signed APK identity/certificate mismatch')
    state['apk_sha256'] = hashlib.sha256(Path(apk).read_bytes()).hexdigest()
    Path('dist/metadata.json').write_text(json.dumps(state, sort_keys=True, indent=2) + '\n')
    Path('dist/SHA256SUMS').write_text(state['apk_sha256'] + '  ' + Path(apk).name + '\n')


if __name__ == '__main__':
    if sys.argv[1] == 'prepare': prepare(sys.argv[2])
    elif sys.argv[1] == 'finish': finish(sys.argv[2])
    else: raise ValueError('Unknown command')
