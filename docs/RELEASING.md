# Releasing

How to publish a new version of `dialkit`, `dialkit-noop` and `dialkit-compose`.

## Contents

- [Overview](#overview)
- [Releasing with JitPack](#releasing-with-jitpack)
  - [1. Prepare the version](#1-prepare-the-version)
  - [2. Verify locally](#2-verify-locally)
  - [3. Tag and push](#3-tag-and-push)
  - [4. Build on JitPack](#4-build-on-jitpack)
  - [5. Announce](#5-announce)
- [Troubleshooting JitPack](#troubleshooting-jitpack)
- [Optional: Maven Central](#optional-maven-central)
  - [One-time setup](#one-time-setup)
  - [Publishing a version](#publishing-a-version)
- [Reference](#reference)

## Overview

Releases are served by [JitPack](https://jitpack.io/#lixiaolin94/dialkit-android), which builds a git tag of this
repository the first time anyone asks for it. There are no accounts, keys or secrets to manage: pushing a tag is the
release. [`jitpack.yml`](../jitpack.yml) tells JitPack to build with JDK 17 and to publish the three library modules
as:

```
com.github.lixiaolin94.dialkit-android:dialkit:<tag>
com.github.lixiaolin94.dialkit-android:dialkit-noop:<tag>
com.github.lixiaolin94.dialkit-android:dialkit-compose:<tag>
```

Tags are plain version numbers (`0.0.2`, not `v0.0.2`), because the tag is the version consumers write.

Maven Central remains possible later, with the `io.github.lixiaolin94` coordinates already in
[`gradle.properties`](../gradle.properties); see [Optional: Maven Central](#optional-maven-central).

## Releasing with JitPack

### 1. Prepare the version

1. Set the version everywhere it is written:

   ```bash
   scripts/set-version.py 0.0.2 --dry-run   # what would change
   scripts/set-version.py 0.0.2
   ```

   It sets `VERSION_NAME` in [`gradle.properties`](../gradle.properties) and `versionName` in
   `samples/*/build.gradle.kts`, updates the dependency coordinates in [`README.md`](../README.md) and
   [`README.zh-CN.md`](../README.zh-CN.md), and moves the [`CHANGELOG.md`](../CHANGELOG.md) entries under
   `## [Unreleased]` into `## [<version>] - <today>`. It neither commits nor tags. Do this only right before the
   tag: until the version is on JitPack, the README would point at coordinates that do not resolve.
2. Review with `git diff`, commit on `main` as `chore(release): <version>` (CONTRIBUTING.md → Commits) and push. Wait for the CI workflow to pass at
   <https://github.com/lixiaolin94/dialkit-android/actions>.

### 2. Verify locally

Run what JitPack will run:

```bash
./gradlew :dialkit:publishToMavenLocal :dialkit-noop:publishToMavenLocal :dialkit-compose:publishToMavenLocal -PsignAllPublications=false -PGROUP=com.github.lixiaolin94.dialkit-android -PVERSION_NAME=<version>
```

Each module in `~/.m2/repository/com/github/lixiaolin94/dialkit-android/` should have an `.aar`, `-sources.jar`,
`-javadoc.jar`, `.module` and `.pom`. Delete that directory afterwards so local builds do not pick it up.

### 3. Tag and push

```bash
git tag <version>
git push origin <version>
```

### 4. Build on JitPack

1. Open <https://jitpack.io/#lixiaolin94/dialkit-android>, find the tag under **Releases** and choose **Get it**.
   That starts the build; any request for the tag's POM does the same:
   <https://jitpack.io/com/github/lixiaolin94/dialkit-android/dialkit/<version>/dialkit-<version>.pom>.
2. The build takes a few minutes. The log icon next to the tag shows its output; the build log is also at
   <https://jitpack.io/com/github/lixiaolin94/dialkit-android/<version>/build.log>.
3. When it is green, check that all three modules resolve, for example from a scratch project, or with:

   ```bash
   curl -sI https://jitpack.io/com/github/lixiaolin94/dialkit-android/dialkit-compose/<version>/dialkit-compose-<version>.aar
   ```

### 5. Announce

Create a GitHub release for the tag at <https://github.com/lixiaolin94/dialkit-android/releases/new> and paste the
version's `CHANGELOG.md` section as the notes:

```bash
gh release create <version> --title <version> --notes-file <file with the changelog section>
```

## Troubleshooting JitPack

| Symptom | Cause and fix |
|---|---|
| The build log shows a Gradle or SDK error | Reproduce with the command in [step 2](#2-verify-locally), fix on `main`, then move the tag (below). |
| A broken build is cached | JitPack keeps failed builds. After fixing, delete the build on the JitPack page (the bin icon next to the tag, when signed in with GitHub) or push a new tag. |
| The tag points to the wrong commit | `git tag -d <version>`, `git push origin :refs/tags/<version>`, tag the right commit and push again, then delete the cached build on JitPack. Prefer a new version once anyone may have downloaded the old one. |
| Consumers get "Could not find com.github.…" | Their build is missing `maven("https://jitpack.io")` in `dependencyResolutionManagement.repositories`. |

## Optional: Maven Central

Maven Central gives the shorter `io.github.lixiaolin94:<module>` coordinates and needs no extra repository for
consumers, at the cost of an account, a signing key and a manual publish step. The
[Release workflow](../.github/workflows/release.yml) is ready for it and runs only when started by hand.

### One-time setup

1. Sign in at <https://central.sonatype.com> with **Continue with GitHub** as `lixiaolin94`. The namespace
   `io.github.lixiaolin94` is verified automatically; check it at
   <https://central.sonatype.com/publishing/namespaces>.
2. Generate a user token at <https://central.sonatype.com/usertoken> and copy its username and password.
3. Create a signing key (`gpg --full-generate-key`, RSA and RSA, 4096 bits), note its id from
   `gpg --list-secret-keys --keyid-format=long`, and upload it:
   `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`. Back the key up; every release must use it.
4. Store the four secrets without pasting them anywhere else:

   ```bash
   gh secret set MAVEN_CENTRAL_USERNAME -R lixiaolin94/dialkit-android
   gh secret set MAVEN_CENTRAL_PASSWORD -R lixiaolin94/dialkit-android
   gh secret set SIGNING_KEY_PASSWORD -R lixiaolin94/dialkit-android
   gpg --export-secret-keys --armor <KEY_ID> | gh secret set SIGNING_KEY -R lixiaolin94/dialkit-android
   ```

### Publishing a version

1. Prepare and tag the version as for JitPack.
2. Run the workflow: `gh workflow run release.yml -R lixiaolin94/dialkit-android -f version=<version>`, or use
   **Run workflow** at <https://github.com/lixiaolin94/dialkit-android/actions/workflows/release.yml>.
3. At <https://central.sonatype.com/publishing/deployments>, wait for **Validated**, check the files, then choose
   **Publish**. Automatic publishing is off, so this is the last chance to stop: a version on Central can never be
   changed or removed.
4. It is live once it appears under <https://repo1.maven.org/maven2/io/github/lixiaolin94/dialkit/>, usually within
   30 minutes.

## Reference

- JitPack documentation: <https://docs.jitpack.io>
- JitPack build environment (`jitpack.yml`): <https://docs.jitpack.io/building/>
- Central Portal publishing guide: <https://central.sonatype.org/publish/publish-portal-guide/>
- gradle-maven-publish-plugin, Central Portal: <https://vanniktech.github.io/gradle-maven-publish-plugin/central/>
