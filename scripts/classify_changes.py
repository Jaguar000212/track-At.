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


def main():
    try:
        only_docs = docs_only(changed_paths(os.environ.get("BASE_SHA"), os.environ.get("HEAD_SHA")))
    except (ValueError, UnicodeError, OSError, subprocess.SubprocessError):
        only_docs = False
    value = "false" if only_docs else "true"
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"android={value}\n")
    print("Documentation-only change" if only_docs else "Full Android validation required")


if __name__ == "__main__":
    main()
