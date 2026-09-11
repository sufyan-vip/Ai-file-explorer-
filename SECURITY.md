# NEXARQ — Security

## Threat model

NEXARQ handles local files and, optionally, root access and third-party AI APIs. The
main risks are malicious archives, shell/command injection, accidental destructive
operations, and credential leakage.

## Archive security

- **Path traversal** — every extraction entry is resolved with
  `FileSystem.safeJoin`, which canonicalizes and verifies the result stays inside the
  destination directory. Unsafe entries are skipped.
- **Absolute / drive-letter paths** are rejected by `ArchiveSecurity.isSafePath`.
- **Decompression bombs** — entry counts and compressed→uncompressed ratios are
  sanity-checked (`ArchiveSecurity.assessEntries`) before extraction.
- **Symlink attacks** — extraction writes plain files; symlink entries are not
  recreated from archives.
- Extraction never follows ".." segments outside the destination.

## Root security

- Root commands are built from a **verb allow-list** (`ls`, `cat`, `stat`, `mount`,
  `cp`, `mv`, `rm`, `chmod`, `chown`, `ln`, …) with each argument **single-quote
  escaped** — never raw string concatenation.
- Dangerous patterns (`rm -rf /`, `mkfs`, writes to `/dev/...`) are blocked.
- Every command has a **timeout**, captured stdout/stderr and an exit code.
- Root is **detected, never assumed**; all root UI clearly indicates elevated mode.
- Destructive root actions require explicit confirmation.

## Data protection

- AI API keys and saved passwords are encrypted with **AES/GCM in the Android
  Keystore** (`SecureStore`) and never written to DataStore/SharedPreferences in
  plaintext.
- Passwords are never logged. Secrets are excluded from backup via
  `backup_rules.xml` / `data_extraction_rules.xml`.
- No signing keys, tokens or API keys are committed to Git (see `.gitignore`).

## Privacy invariants

- No analytics, no telemetry, no advertising SDK.
- File contents are never uploaded unless the user explicitly asks the AI to
  process them.
- Clipboard uses an in-memory, app-internal queue rather than the system clipboard
  to avoid leaking paths to other apps.

## Reporting

If you find a security issue, please report it privately to the maintainers with a
minimal reproduction.
