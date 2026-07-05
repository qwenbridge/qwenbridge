# Secrets Handling Policy

Secrets must never be committed to the repository.

## Never commit

Do not commit:

- `.env`
- API keys
- access tokens
- passwords
- private endpoints
- private keys
- signing keys
- Maven Central credentials
- npm tokens
- cloud credentials
- production configuration files

## Local development

Use `.env.example` for safe placeholders only. Real local values must stay in untracked local files or local shell environment variables.

## CI and publishing

Use the repository or organization secret store for:

- Maven Central credentials
- GPG signing keys
- npm tokens
- NVD API keys
- deployment credentials

## Accidental exposure

If a secret is committed:

1. rotate the secret immediately
2. remove it from the current tree
3. audit whether history rewrite is required
4. review logs and access records
5. document the remediation privately

Deleting the file in a later commit is not enough if the secret remains in Git history.
