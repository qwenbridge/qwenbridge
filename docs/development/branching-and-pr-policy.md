# Branching and Pull Request Policy

## Branches

- `main` is the protected integration and release branch.
- Use a short-lived branch for every change.
- Use conventional prefixes such as `feat/`, `fix/`, `docs/`, `chore/`, `refactor/`, and `ci/`.

Examples:

```text
feat/typed-streaming-client
fix/opensearch-timeout-handling
docs/finalize-public-documentation
chore/release-v0.9.0
```

## Pull requests

Every change to `main` should be merged through a pull request.

A pull request must include:

- a focused title and description
- tests for changed behavior
- documentation updates when public behavior changes
- passing CI checks
- no committed secrets, build outputs, `node_modules`, or `target` directories

## Merge policy

Use squash merge or a consistent merge policy chosen for the repository. Require review and successful status checks before merge. Release tags are created from `main` only after verification passes.
