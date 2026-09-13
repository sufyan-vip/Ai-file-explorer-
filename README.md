# NEXARQ

**NEXARQ** (pronounced "Nex-ark") is a modern, premium Android archive manager and
file-management power suite — plus a root power tool and an optional AI-assisted file
workspace.

It is an original implementation inspired by the *usability* of classic archive
managers, with its own UI, architecture, branding and code. NEXARQ is **not**
affiliated with, endorsed by, or derived from ZArchiver.

## Highlights

- **File manager** — browse internal storage, SD/USB storage and the full filesystem
  (root), with list/grid views, multi-select, breadcrumb navigation, favorites,
  recents, search, and a copy/move clipboard queue.
- **Archive engine** — create / extract / inspect / test ZIP, 7Z, TAR, TAR.GZ,
  TAR.BZ2, TAR.XZ, TAR.ZSTD, GZIP, BZIP2, XZ and ZSTD archives. Password-protected
  ZIP (AES) create/extract. Entry preview, search and metadata.
- **Root mode** — safe `su` abstraction, root file browser with permissions/ownership,
  chmod/chown, symlink creation, mount info. Strictly validated, timeout-guarded.
- **Tools** — storage analyzer, duplicate finder, hash calculator (MD5/SHA-1/SHA-256/
  SHA-512), APK inspector, text editor, hex viewer, batch renamer, file comparison,
  log analyzer.
- **AI Center (optional)** — Google Gemini and OpenRouter chat with streaming,
  per-provider API keys stored in the Android Keystore, provider fallback, and
  privacy controls. AI never acts on files without explicit approval.
- **Privacy-first** — local processing by default, no analytics, no telemetry, no
  advertising SDK. Network is used only when you configure an AI provider.

## Build

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # unsigned (or signed, see RELEASE.md)
./gradlew test               # unit tests
./gradlew lint               # static analysis
```

Requirements: JDK 17, Android SDK with compileSdk 35.

## Project layout

```
app/src/main/java/com/nexarq/app/
  core/       models, filesystem primitives, formatting, file-type, intents
  archive/    archive engine + extraction security
  root/       root detection + safe shell abstraction
  search/     bounded local search engine
  tools/      hashing, APK, text/hex, analyzer, duplicates, batch rename, compare, logs
  ai/         Gemini + OpenRouter providers, secure keys, chat
  data/       DataStore settings, JSON stores, repositories, operation queue
  ui/         Compose screens, navigation, theme, dialogs, components
```

See `ARCHITECTURE.md` for the full design.

## Documentation

- `ARCHITECTURE.md` — layers, modules and design decisions
- `SECURITY.md` — threat model and hardening
- `ROOT_ACCESS.md` — how root mode works and its limits
- `TESTING.md` — test strategy and how to run tests
- `RELEASE.md` — signing and publishing checklist
- `PRIVACY.md` — privacy policy
- `CHANGELOG.md` — release history
- `FINAL_REQUIREMENTS_AUDIT.md` — requirement-by-requirement status

## License

Original code © NEXARQ contributors. Third-party libraries are used under their own
licenses (see `SECURITY.md` / About screen).
