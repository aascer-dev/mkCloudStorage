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
- Root `sql/` is the only database script source. Do not add a second migration directory under application resources.
- Preserve existing user changes. Inspect `git status` before edits and never use destructive Git commands unless explicitly requested.
- Use `apply_patch` for deliberate source and documentation edits.
- Keep secrets in ignored `.env`; update `.env.example` with placeholders only. Never log, return, document, or commit credentials, tokens, password hashes, or private object-storage paths.

## Verification

- Use the smallest relevant Maven test first, then run broader tests when the change affects shared behavior.
- Treat compilation as compilation only; report integration tests separately when MySQL, Redis, MinIO, or RabbitMQ are required.
- For API changes, verify validation, unauthenticated access, authorization, and error response behavior.
