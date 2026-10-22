# Contributing

## Build and test

Requirements: JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew :dialkit:testDebugUnitTest     # JVM unit tests (maths, DSL, import)
./gradlew :dialkit:lintRelease :dialkit-noop:lintRelease :dialkit-compose:lintRelease   # lint, library modules
./gradlew :samples:catalog:installDebug  # Catalog: every control and panel feature, one screen each
./gradlew :samples:embedded:installDebug # No floating panel: tuning on the app's own screens
./gradlew :samples:compose:installDebug  # Compose sample
./gradlew :samples:views:installDebug    # View sample (plain android.app.Activity)
```

`./gradlew :samples:views:assembleMeasure` builds the View sample with R8 and without obfuscation, for measuring
what DialKit adds to an APK (`apkanalyzer dex packages`).

## Project layout

- `dialkit/src/shared`: the DSL, value cells and model, compiled into both `dialkit` and `dialkit-noop`.
- `dialkit/src/main`: entry point, overlay, store and all UI (`ui/`), plus resources (`res/`, all prefixed `dialkit_`).
- `build-logic/`: the `dialkit.android.library` convention plugin shared by the library modules.
- `reference/` (git-ignored, local only): DialKit's source for comparison. Restore it from the `dialkit` npm package as
  described in [docs/WEB_PARITY.md](docs/WEB_PARITY.md) §2.6; it is never committed.

## Conventions

- Every number that comes from DialKit carries a `SOURCE` comment pointing at the file and line in DialKit 2.0.2's source;
  Android conversions are marked `MAP`; values of our own are marked `DESIGN` (and `DESIGN (user)` when tuned by
  the maintainer on a device). See [docs/DESIGN.md](docs/DESIGN.md).
- Changes that follow a DialKit update go through the process in [docs/WEB_PARITY.md](docs/WEB_PARITY.md).
- The core library keeps zero runtime dependencies. Public API is explicit (`-Xexplicit-api=strict`) and must be
  mirrored in `dialkit-noop`.
- Code style: Kotlin official style (see `.editorconfig`), 120 columns.

## Releasing

Releases are git tags built by JitPack; see [docs/RELEASING.md](docs/RELEASING.md), which also covers the optional
Maven Central route.

`./gradlew publishToMavenLocal -PsignAllPublications=false` needs no credentials and writes unsigned artifacts to
`~/.m2` for checking.
