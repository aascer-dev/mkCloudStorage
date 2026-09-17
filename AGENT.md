# MKCS Agent Requirements

## Command Execution

- Use Git Bash as the default shell for repository commands.
- Prefer POSIX commands and Git Bash tools such as `rg`, `find`, `sed`, `git`, `docker`, and `./mvnw`. When Git Bash does not provide `rg`, use `grep -R` rather than switching shells just to search text.
- When the host shell is not Git Bash, invoke commands through Git Bash, for example:
  ```bash
  "D:\Scoop\apps\git\current\bin\bash.exe" -lc 'git status --short'
  ```
- Use PowerShell only for Windows-only tasks that Git Bash cannot perform reliably, such as Windows reserved-port inspection or Windows service configuration. State the reason when doing so.

## Project Conventions

- This is a multi-module Spring Boot project: `mkcs-common`, `mkcs-model`, and `mkcs-server`.
- Flyway migrations live in `mkcs-server/src/main/resources/db/migration/`. Use immutable
  `V<version>__<description>.sql` files; never edit a migration once it has been applied.
- Root `sql/mkCloudStorage.sql` is a schema snapshot for inspection and manual recovery, not a
  runtime migration source. Do not add new runtime migrations under root `sql/`.
- Preserve existing user changes. Inspect `git status` before edits and never use destructive Git commands unless explicitly requested.
- Use `apply_patch` for deliberate source and documentation edits.
- Keep secrets in ignored `.env`; update `.env.example` with placeholders only. Never log, return, document, or commit credentials, tokens, password hashes, or private object-storage paths.

## Verification

- Use the smallest relevant Maven test first, then run broader tests when the change affects shared behavior.
- Treat compilation as compilation only; report integration tests separately when MySQL, Redis, MinIO, or RabbitMQ are required.
- For API changes, verify validation, unauthenticated access, authorization, and error response behavior.


## Issue Management

- MUST check the repository root `Issue/Open/` directory before handling any request related to bug fixes, feature changes, issue investigation, issue verification, or issue continuation.
- MUST treat documents in `Issue/Open/` as the primary task source for active Issues.
- MUST read the corresponding Issue document before editing code.
- MUST follow the Issue's stated requirements, scope, and acceptance criteria unless the user explicitly overrides them.
- MUST NOT close, archive, or move an Issue without completing the required verification steps.
