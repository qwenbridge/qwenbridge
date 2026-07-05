# Publishing to Maven Central

This guide documents the release process for QwenBridge Java artifacts.

## Published artifacts

QwenBridge publishes these Maven artifacts:

- `io.qwenbridge:qwenbridge-java-sdk`
- `io.qwenbridge:qwenbridge-spring-boot-starter`

The server module is an application and is not intended to be published as a reusable Maven library.

## Release version

Release artifacts must not use `-SNAPSHOT`.

```xml
<version>0.9.0</version>
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

## Release flow

```bash
git checkout main
git pull --ff-only origin main

mvn versions:set -DnewVersion=0.9.0
mvn clean verify

git add -A
git commit -m "chore(release): prepare v0.9.0"

git tag -a v0.9.0 -m "QwenBridge v0.9.0"
git push origin main
git push origin v0.9.0
```

## Publication boundary

Publish only after public documentation, examples, license files, signing configuration, and release evidence are finalized.
