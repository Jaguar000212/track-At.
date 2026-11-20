"""Pure validation for a newly created TrackAt build tag."""
from dataclasses import dataclass
import re

MAX_CODE = 2_100_000_000
APP_ID = "com.jaguar.attendancetracker"
DECIMAL = r"(?:0|[1-9][0-9]*)"
VERSION = re.compile(rf"{DECIMAL}\.{DECIMAL}\.{DECIMAL}", re.ASCII)
SHA = re.compile(r"[0-9a-f]{40}", re.ASCII)


@dataclass(frozen=True)
class SourceVersion:
    name: str
    code: int


@dataclass(frozen=True)
class TagCandidate:
    channel: str
    tag: str
    version_name: str
    version_code: int
    application_id: str
    source_sha: str
    source_tree: str


def _code(raw):
    if not isinstance(raw, str) or not re.fullmatch(r"[1-9][0-9]{0,9}", raw):
        raise ValueError("Version code must be a canonical positive integer")
    code = int(raw)
    if code > MAX_CODE:
        raise ValueError("Version code exceeds Android limit")
    return code


def parse_source_version(text):
    rows = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep or key not in {"versionName", "versionCode"} or key in rows:
            raise ValueError("Unexpected or duplicate source version field")
        rows[key] = value
    if rows.keys() != {"versionName", "versionCode"} or not VERSION.fullmatch(rows["versionName"]):
        raise ValueError("Expected canonical source version")
    return SourceVersion(rows["versionName"], _code(rows["versionCode"]))


def validate_candidate(*, channel, tag, source_text, source_sha, source_tree,
                       branch_sha, branch_tree, existing_refs=None,
                       previous_public=(), successful_tags=()):
    existing_refs = {} if existing_refs is None else existing_refs
    if not all(isinstance(x, str) and SHA.fullmatch(x) for x in
               (source_sha, source_tree, branch_sha, branch_tree)):
        raise ValueError("Expected full lowercase Git object IDs")
    if (source_sha, source_tree) != (branch_sha, branch_tree):
        raise ValueError("Tag must point to the exact authorized branch tip and tree")
    if tag in successful_tags:
        raise ValueError("A successful build already used this tag")
    if tag in existing_refs and existing_refs[tag] != source_sha:
        raise ValueError("Existing tag points to a different commit")
    version = parse_source_version(source_text)
    if channel == "dev":
        prefix = "dev-build/"
        if not tag.startswith(prefix):
            raise ValueError("Unexpected Dev tag")
        code = _code(tag[len(prefix):])
        for old in existing_refs:
            if old.startswith(prefix) and old != tag and _code(old[len(prefix):]) >= code:
                raise ValueError("Dev tag counter is stale")
        return TagCandidate(channel, tag, version.name + "-dev", code, APP_ID + ".dev",
                            source_sha, source_tree)
    if channel != "public" or tag != "v" + version.name:
        raise ValueError("Public tag must equal the canonical source version")
    for old_tag, old_code in previous_public:
        if (not isinstance(old_tag, str) or not old_tag.startswith("v")
                or not VERSION.fullmatch(old_tag[1:]) or type(old_code) is not int
                or not 1 <= old_code <= MAX_CODE):
            raise ValueError("Unsupported previous public release metadata")
        if old_tag == tag or old_code >= version.code:
            raise ValueError("Public version code must increase")
    return TagCandidate(channel, tag, version.name, version.code, APP_ID,
                        source_sha, source_tree)
