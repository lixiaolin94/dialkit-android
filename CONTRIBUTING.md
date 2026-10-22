# Contributing

How this project is built, tested, committed and released. Read it before opening a pull request; maintainers follow
it too.

## Contents

- [Build and test](#build-and-test)
- [Project layout](#project-layout)
- [Code conventions](#code-conventions)
- [Testing](#testing)
- [Commits](#commits)
- [Branches, pull requests and history](#branches-pull-requests-and-history)
- [Versioning and changelog](#versioning-and-changelog)
- [Documentation](#documentation)
- [Releasing](#releasing)

## Build and test

Requirements: JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew :dialkit:testDebugUnitTest     # JVM unit tests (maths, DSL, import, validation)
./gradlew :dialkit:lintRelease :dialkit-noop:lintRelease :dialkit-compose:lintRelease   # lint, library modules
./gradlew assembleDebug assembleRelease  # every module (CI also runs lint and compiles the device tests)
```

Samples: `samples/catalog` (every control and panel feature, one screen each), `samples/embedded` (no floating panel:
tuning on the app's own screens), `samples/compose` and `samples/views`. Build one with
`./gradlew :samples:<name>:assembleDebug`.

With several devices attached, install with `adb -s <serial> install -r <apk>`: `installDebug` and
`connectedAndroidTest` install on every attached device.

`./gradlew :samples:views:assembleMeasure` builds the View sample with R8 and without obfuscation, for measuring
what DialKit adds to an APK (`apkanalyzer dex packages`).

## Project layout

- `dialkit/src/shared`: the DSL, value cells and model, compiled into both `dialkit` and `dialkit-noop`.
- `dialkit/src/main`: entry point, overlay, store and all UI (`ui/`), plus resources (`res/`, all prefixed `dialkit_`).
- `dialkit/src/test` (JVM) and `dialkit/src/androidTest` (device): see [Testing](#testing).
- `dialkit-noop`: the same public API with no UI, for release builds. `dialkit-compose`: the Compose adapter.
- `build-logic/`: the `dialkit.android.library` convention plugin shared by the library modules.
- `scripts/`: `set-version.py` (release), `check-commits.py` (commit messages).
- `reference/` (git-ignored, local only): DialKit's source for comparison. Restore it from the `dialkit` npm package as
  described in [docs/WEB_PARITY.md](docs/WEB_PARITY.md) §2.6; it is never committed.

## Code conventions

- **Provenance of every value.** A number that comes from DialKit carries a `SOURCE` comment pointing at the file and
  line in DialKit 2.0.2's source; an Android conversion is marked `MAP`; a value of our own is marked `DESIGN` (and
  `DESIGN (user)` when the maintainer tuned it on a device). An unlabelled number counts as a guess. See
  [docs/DESIGN.md §0](docs/DESIGN.md).
- **Porting DialKit.** Changes that follow a DialKit release go through [docs/WEB_PARITY.md](docs/WEB_PARITY.md): the
  data layer is ported faithfully, visuals by mapping, interactions by adaptation.
- **Zero runtime dependencies** in `dialkit` and `dialkit-noop` (only the Kotlin standard library). AndroidX may be
  `compileOnly`.
- **Public API is explicit** (`-Xexplicit-api=strict`), documented with KDoc, and mirrored in `dialkit-noop` with the
  same signatures. A change to a public signature is a [breaking change](#versioning-and-changelog).
- **Resources** are prefixed `dialkit_`; strings live in `res/values/dialkit_strings.xml`, colours in
  `dialkit_colors.xml` (with a `values-night` twin).
- **Style:** Kotlin official style (see `.editorconfig`), 120 columns, comments in English that explain why rather
  than what.
- **Never crash the host.** Values from outside (persistence, Import, adb, `setValues`) are validated before they reach
  a control; invalid declarations fail at declaration with `IllegalArgumentException`.

## Testing

- **Unit tests** (`./gradlew :dialkit:testDebugUnitTest`) cover the maths, DSL, import and validation. A port of a
  DialKit rule gets a test whose expected values come from DialKit's own output.
- **Device tests** (`dialkit/src/androidTest`) cover what needs Android: the store's lifecycle and persistence,
  in-page panels, `floatingPanel = false`. They need an unlocked device; run them on one:

  ```bash
  ./gradlew :dialkit:assembleDebugAndroidTest
  adb -s <serial> install -r -t dialkit/build/outputs/apk/androidTest/debug/dialkit-debug-androidTest.apk
  adb -s <serial> shell am instrument -w io.github.lixiaolin94.dialkit.test/androidx.test.runner.AndroidJUnitRunner
  ```

- **A fix comes with a test** that fails without it, unless the bug is purely visual.
- **UI changes are checked on a device**, with screenshots in the pull request.
- CI runs unit tests, lint and every module's build, and compiles both device test suites (`dialkit` and
  `samples/compose`); it does not run them.

## Commits

### One change per commit

Each commit is one logical change, small enough to review and to revert alone, and it **builds and passes the unit
tests on its own**, so `git bisect` works. A feature, a fix, a refactor and a docs update are separate commits; a
feature's own tests and its CHANGELOG entry belong in the feature's commit. Do not batch unrelated work.

### Message format

[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/), in English:

```
<type>(<scope>): <subject>

<body: what changed and why, wrapped at 100 columns>

<footers: BREAKING CHANGE: ..., Fixes #12, Co-authored-by: ...>
```

- **Header** at most 72 characters, ASCII. **Subject** in the imperative mood, lowercase first word, no trailing
  period: `fix(store): load persisted values in non-debuggable builds`.
- **Body** (optional for trivial changes) explains the motivation and the effect the reader cannot see in the diff:
  the bug's cause, the decision taken, what was verified.
- **Breaking change:** `!` after the type or scope, plus a `BREAKING CHANGE:` footer saying what to change.

| Type | For |
|---|---|
| `feat` | a new capability for library users |
| `fix` | a bug fix |
| `perf` | a performance improvement without behaviour change |
| `refactor` | a code change that neither fixes a bug nor adds a feature |
| `test` | tests only |
| `docs` | documentation only (README, docs/, KDoc-only changes) |
| `build` | Gradle, dependencies, build-logic, publishing |
| `ci` | GitHub Actions and other CI configuration |
| `chore` | releases and housekeeping (`chore(release): 0.0.2`) |
| `style` | formatting only |
| `revert` | reverting an earlier commit |

Scopes (optional, one per commit): `dsl`, `store`, `overlay`, `panel`, `page` (in-page panels), `slider`, `color`,
`pad`, `transition`, `image`, `import`, `adb`, `noop`, `compose`, `catalog`, `embedded`, `samples`, `release`,
`deps`.

Check messages before pushing:

```bash
git config commit.template .gitmessage        # once: an editor template with the rules
scripts/check-commits.py origin/main..HEAD    # CI runs the same check on pushes and pull requests
```

## Branches, pull requests and history

- `main` is always releasable and its history is **linear**: rebase, do not merge. Squash fixups before they land.
- Work on a branch and open a pull request (the template lists what to check). Keep a pull request to one topic.
- **Never rewrite history that a release tag points to.** JitPack builds a tag once and freezes it after 7 days.
  Rewriting unreleased commits on `main` (rewording, splitting) is a maintainer decision, announced in the pull
  request or release notes.

## Versioning and changelog

- [Semantic Versioning](https://semver.org/). While the version is `0.x`, a breaking change raises the minor
  version (`0.1.0`), anything else the patch version (`0.0.2`).
- Breaking: removing or renaming public API, changing a signature, changing behaviour that callers rely on (for
  example a declaration that used to work now throwing; rejecting one that could only crash later is a fix),
  changing the persisted JSON shape.
- [`CHANGELOG.md`](CHANGELOG.md) follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Every user-visible
  change adds a line under `## [Unreleased]` (`Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`) in
  the same commit. Internal refactors, tests and CI need no entry.

## Documentation

- [`README.md`](README.md) and [`README.zh-CN.md`](README.zh-CN.md) say the same thing; change both in the same
  commit.
- Design decisions and every value's source go in [`docs/DESIGN.md`](docs/DESIGN.md); differences from DialKit in
  [`docs/WEB_PARITY.md`](docs/WEB_PARITY.md); the release procedure in [`docs/RELEASING.md`](docs/RELEASING.md).
- Third-party code or assets go in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), and in the shipped
  `META-INF/<module>-NOTICES.txt` when they end up in an artifact.

## Releasing

Releases are git tags built by JitPack; see [docs/RELEASING.md](docs/RELEASING.md), which also covers the optional
Maven Central route. Only the maintainer tags and publishes.

`./gradlew publishToMavenLocal -PsignAllPublications=false` needs no credentials and writes unsigned artifacts to
`~/.m2` for checking.
