#!/usr/bin/env python3
"""Sets the release version everywhere it is written, for docs/RELEASING.md step 1.

    scripts/set-version.py 0.0.2            # edit the files
    scripts/set-version.py 0.0.2 --dry-run  # only show what would change

Updates VERSION_NAME in gradle.properties, versionName in every samples/*/build.gradle.kts, the dependency
coordinates in README.md and README.zh-CN.md, and moves CHANGELOG.md's [Unreleased] entries under
`## [<version>] - <today>`, leaving an empty [Unreleased] above. It changes nothing else and never commits or tags.
"""
from __future__ import annotations

import datetime
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
COORDINATE = re.compile(r"(com\.github\.lixiaolin94\.dialkit-android:[a-z-]+:)[0-9][0-9A-Za-z.\-]*")


def replace(path: pathlib.Path, pattern: str, repl: str, expect: int | None = None, flags: int = 0) -> tuple[str, str, int]:
    old = path.read_text(encoding="utf-8")
    new, count = re.subn(pattern, repl, old, flags=flags)
    if expect is not None and count != expect:
        sys.exit(f"{path.relative_to(ROOT)}: expected {expect} match(es) of {pattern!r}, found {count}")
    return old, new, count


def changelog(path: pathlib.Path, version: str) -> tuple[str, str, int]:
    old = path.read_text(encoding="utf-8")
    head = "## [Unreleased]\n"
    if head not in old:
        sys.exit("CHANGELOG.md: no '## [Unreleased]' section")
    if f"## [{version}]" in old:
        sys.exit(f"CHANGELOG.md: [{version}] already exists")
    start = old.index(head) + len(head)
    end = old.find("\n## [", start)
    body = old[start:end if end != -1 else len(old)].strip("\n")
    if not body:
        sys.exit("CHANGELOG.md: [Unreleased] is empty; nothing to release")
    today = datetime.date.today().isoformat()
    section = f"{head}\n## [{version}] - {today}\n\n{body}\n"
    rest = old[end + 1:] if end != -1 else ""
    new = old[:old.index(head)] + section + ("\n" + rest if rest else "")
    return old, new, 1


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    if len(args) != 1 or not re.fullmatch(r"\d+\.\d+\.\d+([-.][0-9A-Za-z.]+)?", args[0]):
        sys.exit(__doc__)
    version = args[0]

    edits = [
        (ROOT / "gradle.properties", replace(ROOT / "gradle.properties", r"(?m)^VERSION_NAME=.*$", f"VERSION_NAME={version}", 1)),
    ]
    for gradle in sorted((ROOT / "samples").glob("*/build.gradle.kts")):
        edits.append((gradle, replace(gradle, r'versionName = "[^"]*"', f'versionName = "{version}"', 1)))
    for readme in ("README.md", "README.zh-CN.md"):
        path = ROOT / readme
        edits.append((path, replace(path, COORDINATE.pattern, rf"\g<1>{version}")))
    edits.append((ROOT / "CHANGELOG.md", changelog(ROOT / "CHANGELOG.md", version)))

    for path, (old, new, count) in edits:
        rel = path.relative_to(ROOT)
        if old == new:
            print(f"  {rel}: already {version}")
            continue
        print(f"  {rel}: {count} change(s)")
        if not dry:
            path.write_text(new, encoding="utf-8")
    print(("Would set" if dry else "Set") + f" version {version}. Review with `git diff`, then commit and tag (docs/RELEASING.md).")


if __name__ == "__main__":
    main()
