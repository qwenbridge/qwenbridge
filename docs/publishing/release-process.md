# Release Process

This is how a QwenBridge release is prepared, verified, versioned and published. Maintainers
with release rights carry out the publishing steps. Everything before step 5 can run locally
without credentials.

## Artifacts

| Artifact | Coordinates | Published to |
|---|---|---|
| Java SDK | `io.qwenbridge:qwenbridge-java-sdk` | Maven Central ([guide](maven-central.md)) |
| TypeScript SDK | `@qwenbridge/sdk` | npm ([guide](npm.md)) |
| Server | `qwenbridge-server` fast-jar (`quarkus-app/`) and the container image from `qwenbridge-server/Dockerfile` | Container registry |
| Core, plugin SDK, packs | `qwenbridge-core`, `qwenbridge-plugin-sdk`, `qwenbridge-commerce-pack`, `qwenbridge-library-pack` | Built with the server. Publish only when a stable plugin API is announced |

All modules share one version, held in the root `pom.xml` and in
`qwenbridge-typescript-sdk/package.json`. `scripts/verification/check-versions.py` fails the build when the
two differ.

## 1. Preconditions

- The release branch is up to date. Its working tree is clean (`git status` shows nothing),
  except files the release itself changes.
- The [follow-ups](../roadmap/QwenBridge-1.0-Followups.md) and the
  [release decision](../release/QwenBridge-1.0-Release-Decision.md) list no open blocker for this
  version.
- The [compatibility document](../release/QwenBridge-1.0-Compatibility.md) lists every breaking
  change since the last release. For 1.0 it must include the QB-287 changes; see the
  [cleanup report](../release/QwenBridge-1.0-Cleanup-Report.md).

## 2. Set the version

```bash
mvn -B versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
(cd qwenbridge-typescript-sdk && npm version X.Y.Z --no-git-tag-version)
python3 scripts/verification/check-versions.py
```

Release versions never end in `-SNAPSHOT`. Update the version in the README, the SDK READMEs and
the Compose label `org.opencontainers.image.version`.

## 3. Verify

Run these in order. Each must pass. A failure stops the release; nothing is skipped or
suppressed.

```bash
mvn -B clean verify                                  # all modules and tests
mvn -B -Pcode-quality -DskipTests verify             # Spotless, SpotBugs, architecture rules
bash scripts/verify-local.sh                         # TypeScript, packaging, versions, scripts
python3 -I scripts/verification/quarkus_only_audit.py .
bash e2e/dual-sdk/scripts/run-dual-sdk-e2e.sh --mode ALL
bash scripts/qwenbridge-final-verification.sh        # master verification
```

**Master verification:**
- **What it covers:** builds, every suite, live runtime steps (database-free runtime,
  multilingual, Quarkus-only), performance, Docker and the dual-SDK E2E.
- **Where it writes:** a run directory under `logs/qwenbridge-1.0/`.
- **What it needs:** a local Compose stack. It never deletes volumes.
- **What is reported, not failed:** `BLOCKED` results (for example, live providers without
  authorization).
- **What to record:** the run ID and its pass, fail and warning counts, in the release evidence.

**Release builds also need:**
- an offline dependency CVE audit (CI job `dependency-scan`);
- a container scan (CI job `container-scan`);
- review of every finding.

## 4. Record the evidence

Create `docs/release/<version>-release-evidence.md`. It contains:
- the commit;
- the master run ID and its counts;
- the test counts per module;
- the E2E report path;
- the dependency and container scan results;
- open findings with owners.

Never derive a live result from a mock; list `BLOCKED` items as blocked.

## 5. Tag and publish

These steps change shared state. Only maintainers authorized for the release carry them out.

1. Commit the version change and the evidence on the release branch. Open a pull request and
   merge it after review.
2. Create an annotated tag `vX.Y.Z` on the merge commit and push it. The `Release` workflow
   (`.github/workflows/release.yml`) then:
   - builds and tests;
   - builds the container image;
   - runs `scripts/verify-release.sh` in lightweight mode;
   - uploads the release evidence.
3. Publish the Java SDK to Maven Central, signed with source and Javadoc JARs
   ([maven-central.md](maven-central.md)).
4. Publish the TypeScript SDK (`npm publish --access public` from `qwenbridge-typescript-sdk`,
   after `npm ci && npm run build && npm test`; see [npm.md](npm.md)).
5. Push the container image tagged `X.Y.Z`.
6. Publish the release notes (`docs/release/QwenBridge-<version>-Release-Notes.md`), including the
   breaking changes and the upgrade steps.

## 6. After the release

- Bump to the next development version.
- Move any open items to the follow-ups document.
- Confirm that the published SDKs install in a clean project:
  - the Java consumer: `e2e/dual-sdk/java-consumer` with the released version;
  - the TypeScript consumer, from the npm registry instead of the tarball.

## Rollback

- **Maven Central and npm:** published versions cannot be replaced. Fix forward with a patch
  release. On npm, mark the broken version deprecated with `npm deprecate`.
- **Container images:** retag the previous image.
- **Server data:** platform state is versioned per pack (`ManagedPacks.rollback`). A server
  downgrade must read the state written by the newer version; the compatibility document says
  whether it can.
