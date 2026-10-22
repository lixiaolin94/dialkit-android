## What and why

<!-- One topic per pull request. What does it change, and why? Link the issue: Fixes #123 -->

## How it was verified

<!-- Unit tests, device tests (device and Android version), screenshots or a recording for UI changes. -->

## Checklist

- [ ] Each commit is one logical change, builds and passes `./gradlew :dialkit:testDebugUnitTest` on its own
- [ ] Commit messages follow CONTRIBUTING.md (`scripts/check-commits.py origin/main..HEAD`)
- [ ] User-visible changes have a `CHANGELOG.md` [Unreleased] line
- [ ] Public API changes are mirrored in `dialkit-noop`; breaking changes are marked
- [ ] Every new number carries a `SOURCE` / `MAP` / `DESIGN` label
- [ ] `README.md` and `README.zh-CN.md` stay in sync
