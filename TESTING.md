# NEXARQ — Testing

## Unit tests (JVM, no device)

```bash
./gradlew test
```

Existing suites (in `app/src/test/`):

- `core/PathSafetyTest` — path normalization, parent/leaf handling, traversal-safe
  joining, archive entry path validation and bomb heuristics.
- `core/ChecksumTest` — MD5/SHA-256 known vectors, case-insensitive verification,
  high-byte hex masking.
- `core/FormatTest` — byte/ratio/ETA formatting.
- `tools/BatchRenameTest` — prefix/number/replace rename + collision handling.

## Instrumentation tests

```bash
./gradlew connectedAndroidTest   # requires a device/emulator
```

The Compose UI test setup is configured (`ui-test-junit4`, `ui-test-manifest`).
Suggested instrumentation coverage: navigation between tabs, file selection mode,
archive viewer open/extract flow, settings persistence, operation progress.

## Security test checklist

- Path-traversal archive entries (`../`, absolute, drive-letter) — covered in unit
  tests via `ArchiveSecurity`.
- Shell-injection attempts — blocked by `RootManager` allow-list/deny-patterns.
- Malformed input — corrupted archives must fail gracefully with a readable error.
- Permission failures — `FileSystem` surfaces I/O exceptions as user messages.

## Manual QA matrix (release gate)

- [ ] Browse internal storage, SD/USB storage
- [ ] Create ZIP / 7Z / TAR.GZ; extract each
- [ ] Password-protected ZIP create + extract (correct and wrong password)
- [ ] Copy / move / delete with progress and conflict handling
- [ ] Search (name/extension/category), analyzer, duplicate finder, hash
- [ ] Text editor save, hex viewer, image preview
- [ ] Root browser (rooted device only): list, chmod, chown, mounts
- [ ] Rotation / backgrounding / process recreation during an operation
- [ ] Low-storage, permission-denied, corrupted-archive cases
