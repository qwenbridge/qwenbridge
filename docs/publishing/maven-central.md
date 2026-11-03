# Publishing to Maven Central

This guide documents the release process for QwenBridge Java artifacts.

## Published artifacts

QwenBridge publishes these Maven artifacts:

- `io.qwenbridge:qwenbridge-java-sdk`

The server module is an application and is not intended to be published as a reusable Maven library.

## Release version

Release artifacts must not use `-SNAPSHOT`.

```xml
<version>1.0.0-RC1</version>
```

## Maven Central requirements

Each published artifact must provide:

- project name, description, and project URL
- license metadata
- developer metadata
- SCM metadata
- source JAR
- Javadoc JAR
- cryptographically signed artifacts

## Pre-publish verification

```bash
mvn clean verify
bash scripts/verify-release.sh
git status
```

The working tree must be clean before creating a release tag.

## Signing key

Releases from 1.0.0-RC1 on are signed with:

| | |
|---|---|
| User ID | Mohammad Banai (QwenBridge release signing) |
| Fingerprint | `9EC1 35B1 6293 FF70 7046  5C0F AF37 0896 15B0 1391` |
| Created / expires | 2026-11-03 / 2029-11-02 |
| Published on | `keys.openpgp.org`, `keyserver.ubuntu.com` |

Version 0.9.1 was signed with the earlier key `C265 426F DC3B 4963 E3C9  D5CD 1688 D9BA 5B4F 0E0F`.
Keep that public key published; it is no longer used for new releases.

The maintainer's keyring holds both keys, so name the signing key explicitly with
`-Dgpg.keyname`. Unlock it first in an interactive terminal (GPG asks for the passphrase there);
the agent then keeps it unlocked for the build:

```bash
echo test | gpg --local-user 9EC135B16293FF7070465C0FAF37089615B01391 --clearsign > /dev/null
```

## Release flow

`main` accepts changes only through pull requests.

```bash
git checkout -b release/1.0.0-RC1 main
mvn -B versions:set -DnewVersion=1.0.0-RC1 -DgenerateBackupPoms=false
(cd qwenbridge-typescript-sdk && npm version 1.0.0-RC1 --no-git-tag-version)
python3 scripts/verification/check-versions.py
mvn -B clean verify
# commit, push, open a pull request and merge it after review

git checkout main && git pull --ff-only
git tag -a v1.0.0-RC1 -m "QwenBridge v1.0.0-RC1"
git push origin v1.0.0-RC1
```

## Upload

Only the parent POM and the Java SDK are deployed. The `central-release` profile signs the
artifacts, adds the source and Javadoc JARs and uploads them to the Central Portal with
`autoPublish=false`:

```bash
mvn -B -Pcentral-release -Dgpg.keyname=9EC135B16293FF7070465C0FAF37089615B01391 \
  -pl .,qwenbridge-java-sdk deploy
```

The deployment then waits in the Central Portal (Deployments) for review. Pressing **Publish**
there makes it public. A published version can never be changed or deleted; fix problems with a
new version.

## Publication boundary

Publish only after public documentation, license files, signing configuration, and release evidence are finalized.
