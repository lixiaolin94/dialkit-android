#!/usr/bin/env python3
"""Checks commit messages against the convention in CONTRIBUTING.md ("Commit messages").

    scripts/check-commits.py 0.0.1..HEAD     # a range
    scripts/check-commits.py HEAD            # one commit
    scripts/check-commits.py --message FILE  # a message file (e.g. from a commit-msg hook)

Exits non-zero and lists every problem when a message does not conform.
"""
from __future__ import annotations

import re
import subprocess
import sys

TYPES = ("feat", "fix", "perf", "refactor", "docs", "test", "build", "ci", "chore", "revert", "style")
HEADER = re.compile(rf"^(?P<type>{'|'.join(TYPES)})(?:\((?P<scope>[a-z0-9][a-z0-9-]*)\))?(?P<bang>!)?: (?P<subject>.+)$")
MAX_HEADER = 72
MAX_BODY_LINE = 100


def problems(message: str) -> list[str]:
    lines = message.rstrip("\n").split("\n")
    header = lines[0]
    found = []
    if header.startswith(("Merge ", "fixup!", "squash!")):
        return [f"not allowed on main: {header!r} (history is linear; squash fixups before pushing)"]
    m = HEADER.match(header)
    if not m:
        found.append(f"header must be '<type>(<scope>): <subject>' with type in {', '.join(TYPES)}: {header!r}")
    else:
        subject = m.group("subject")
        if subject[0].isupper():
            found.append(f"subject starts lowercase (imperative, e.g. 'add', 'fix'): {subject!r}")
        if subject.endswith("."):
            found.append(f"subject has no trailing period: {subject!r}")
        if re.search(r"[^\x00-\x7f]", header):
            found.append("header is ASCII English")
    if len(header) > MAX_HEADER:
        found.append(f"header is {len(header)} characters; at most {MAX_HEADER}")
    if len(lines) > 1 and lines[1].strip():
        found.append("a blank line separates the header from the body")
    for i, line in enumerate(lines[2:], start=3):
        if len(line) > MAX_BODY_LINE and not re.match(r"^\s*(https?://|\S+:\s*\S+$)", line):
            found.append(f"body line {i} is {len(line)} characters; wrap at {MAX_BODY_LINE}")
    if m and m.group("bang") and "BREAKING CHANGE:" not in message:
        found.append("a '!' breaking change needs a 'BREAKING CHANGE: ...' footer")
    return found


def commits(rev: str) -> list[tuple[str, str]]:
    revs = subprocess.run(
        ["git", "rev-list", "--reverse", rev] if ".." in rev else ["git", "rev-list", "-1", rev],
        capture_output=True, text=True, check=True,
    ).stdout.split()
    out = []
    for sha in revs:
        msg = subprocess.run(["git", "log", "-1", "--format=%B", sha], capture_output=True, text=True, check=True).stdout
        out.append((sha[:7], msg))
    return out


def main() -> int:
    args = sys.argv[1:]
    if args[:1] == ["--message"] and len(args) == 2:
        targets = [("message", open(args[1], encoding="utf-8").read())]
    elif len(args) == 1:
        targets = commits(args[0])
    else:
        print(__doc__)
        return 2
    failed = 0
    for sha, msg in targets:
        p = problems(msg)
        if p:
            failed += 1
            print(f"{sha}  {msg.splitlines()[0] if msg.strip() else '(empty)'}")
            for line in p:
                print(f"    - {line}")
    print(f"{len(targets) - failed}/{len(targets)} commit message(s) conform." if targets else "No commits to check.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
