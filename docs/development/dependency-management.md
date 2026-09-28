# Dependency management and supply-chain runbook

This document describes how dependency updates are expected to work in this repository and how to recover safely when a
Renovate PR cannot update Gradle lock or verification state completely.

The goal is to keep routine updates low-maintenance without weakening dependency verification when a toolchain or plugin
update needs manual attention.

## Ownership model

Renovate owns dependency-update PR creation. GitHub remains the enforcement layer through required CI, CodeQL,
signed-commit rules, and merge protection.

Gradle dependency locking and dependency verification are intentional security and reproducibility controls. A red
dependency-update PR is not a reason to disable them.

The main files involved are:

- `gradle/libs.versions.toml` — dependency/plugin versions managed by the version catalog.
- `gradle.lockfile` — locked project dependency graph.
- `settings-gradle.lockfile` — locked settings/plugin-resolution state where applicable.
- `gradle/verification-metadata.xml` — accepted checksums plus narrowly scoped trusted IDE-only artifacts.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle wrapper version and distribution integrity configuration.
- `.github/renovate.json5` — Renovate scheduling, review, and automerge policy.

## Update policy

The repository currently uses these rules:

| Update type                              | Expected handling                                                |
|------------------------------------------|------------------------------------------------------------------|
| Vulnerability/security fix               | Immediate Renovate PR, manual review                             |
| Major                                    | PR, manual review                                                |
| Minor                                    | PR, CI, manual review                                            |
| Stable patch for a `>= 1.0` dependency   | PR, 14-day minimum release age, CI, then automerge when eligible |
| Initial immutable SHA/digest pinning     | PR, CI, automerge                                                |
| Movement of an already-pinned digest/SHA | Manual security review                                           |
| Pre-1.0 dependency                       | Manual review                                                    |
| Prerelease dependency                    | Manual review                                                    |

Repository rules and the current `.github/renovate.json5` remain the source of truth if this table ever drifts.

## Reviewing a normal Renovate PR

For a routine dependency PR:

1. Confirm the update scope matches the PR description.
2. Confirm the Renovate commit is GitHub-verified.
3. Check which dependency-state files changed. A JVM/plugin update can legitimately change the version catalog, lock
   state, and verification metadata.
4. Let `Build and verify` and CodeQL finish.
5. Do not bypass a dependency-verification failure just to merge the update.

If Renovate generated complete lock and verification state and required checks pass, no manual regeneration is needed.

## Repairing an incomplete Gradle dependency update

Some plugin, prerelease, or toolchain updates resolve artifacts that Renovate's generated metadata does not cover
completely. CI will then fail with a dependency-verification error.

Do not switch verification to `off` or `lenient`, and do not add broad trust rules for an entire group, repository, POM
type, or ZIP type.

Work on the Renovate branch and regenerate from an empty temporary Gradle user home:

```bash
git fetch origin
git switch --track origin/renovate/<branch>
```

If the branch already exists locally:

```bash
git switch renovate/<branch>
git reset --hard origin/renovate/<branch>
```

Generate lock and checksum state from a clean cache:

```bash
tmp="$(mktemp -d)"
GRADLE_USER_HOME="$tmp" \
  ./gradlew --no-daemon \
  --write-locks \
  --write-verification-metadata sha256 \
  build
status=$?
rm -rf "$tmp"
exit $status
```

Then validate the resulting state from another empty cache:

```bash
tmp="$(mktemp -d)"
GRADLE_USER_HOME="$tmp" \
  ./gradlew --no-daemon \
  build \
  --dependency-verification=strict
status=$?
rm -rf "$tmp"
exit $status
```

Review the diff before committing:

```bash
git status --short
git diff -- gradle.lockfile settings-gradle.lockfile gradle/verification-metadata.xml
```

Expected changes should correspond to the dependency or toolchain update being reviewed. Do not accept unrelated
checksum churn without understanding why it appeared.

Commit any human repair with a signature:

```bash
git add gradle.lockfile settings-gradle.lockfile gradle/verification-metadata.xml
git commit -S -m "chore: refresh Gradle dependency verification"
git push
```

Only add files that actually changed.

### Why the clean temporary Gradle home matters

Verification metadata generated from a warm developer cache can omit metadata artifacts that a clean CI runner later
downloads. Generating and validating from empty temporary Gradle homes makes the local check much closer to a fresh CI
environment.

## IDE-only dependency verification

IntelliJ/Gradle project import can resolve documentation and tooling artifacts that the normal CLI build does not need.
In this repository, `gradle/verification-metadata.xml` intentionally trusts only narrow IDE-only source/documentation
patterns:

```text
*-javadoc.jar
*-sources.jar
gradle-*-src.zip for the Gradle distribution source artifact
```

These exceptions do not broadly trust runtime dependencies, compiler/plugin binaries, arbitrary POMs, or arbitrary ZIP
files.

The metadata also contains an explicit checksum for `org.jetbrains.kotlin:kotlin-reflect:2.4.0`'s POM because the
IntelliJ Gradle model import resolves that compatibility metadata even though the application itself uses the current
Kotlin line.

When a new `prepareKotlinBuildScriptModel` verification failure appears:

1. First confirm `./gradlew clean build --dependency-verification=strict` succeeds.
2. Identify the exact artifact and why the IDE is resolving it.
3. Prefer an exact checksum for executable/build metadata.
4. Use a trust pattern only for a clearly non-executable IDE source/Javadoc artifact, and scope the pattern as narrowly
   as possible.
5. Do not disable verification globally.

For a manually added checksum, independently compare the downloaded artifact with the authoritative upstream repository
before committing the hash.

## CycloneDX metadata resolution

The CycloneDX task deliberately uses:

```kotlin
includeMetadataResolution = false
```

Keep this setting unless the dependency-verification workflow is redesigned.

When metadata resolution was enabled, CycloneDX performed additional Maven POM lookups during SBOM generation. Those
extra lookups could fall outside the metadata Renovate had generated, causing otherwise-valid dependency PRs to fail CI.

The SBOM still uses the configured compile/runtime dependency graph; the disabled setting only avoids the extra Maven
metadata-enrichment pass.

## Gradle wrapper updates

Treat Gradle wrapper upgrades as manual toolchain updates, even when Renovate opens the PR.

A wrapper upgrade can change Gradle's own plugin/tooling dependency graph, so changing only `distributionUrl` may not be
sufficient. After the wrapper version changes:

1. Verify the target Gradle release and the official SHA-256 for the exact distribution being used.
2. Add or update `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`.
3. Regenerate dependency lock/verification state from a clean temporary Gradle home using the procedure above.
4. Run a second clean-cache strict build.
5. Review all new Gradle/Kotlin tooling artifacts before committing.

The checksum must match the exact configured distribution, for example the `-bin.zip` when `distributionUrl` uses the
binary distribution.

## Signed commits and rebases

The default branch requires signed commits.

Renovate uses platform commits so its bot commits are GitHub-verified. Human repair commits should use `git commit -S`.

For a human-authored branch that needs rebasing, prefer a local signed rebase:

```bash
git fetch origin
git rebase --gpg-sign origin/main
```

Avoid server-side/UI rebases for human-signed commits because the recreated commits cannot use the developer's local
signing key.

For a Renovate-owned branch, prefer Renovate's rebase/retry checkbox so Renovate recreates its own platform-signed
commit. If a human has added a signed repair commit to that branch, inspect the resulting history carefully before
forcing another rebase.

## Supply-chain guardrails

Do not fix dependency-update friction by weakening the controls that are detecting it.

In particular:

- Do not set dependency verification to `off` or `lenient`.
- Do not broadly trust all POMs, all artifacts from a dependency group, all ZIP files, or an entire repository.
- Do not hand-edit lockfiles.
- Do not generate verification metadata only from a warm local cache.
- Do not merge through failing required checks.
- Do not accept unexpected verification-metadata churn without reviewing the affected coordinates.
- Do not use administrator bypass as the normal dependency-update workflow.

When an update needs more manual work than expected, keep the PR manual and make the smallest auditable change that
restores a clean strict build.
