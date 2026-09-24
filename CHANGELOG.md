# Changelog

All notable changes to NEXARQ are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) with [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Trash / recycle bin: deletes move files to a restorable trash (app-private
  storage), with per-item restore, permanent delete, empty-trash, and automatic
  expiry after 7/30/90 days (configurable in Settings).
- File encryption: AES-256-GCM password-based encryption/decryption producing
  `.nxq` files, with streaming progress and wrong-password detection. Available
  from the browser selection bar for single files.
- Wi-Fi transfer: embedded dependency-free HTTP server — share a folder with any
  browser on the same network to browse, download and upload files (Tools menu).
- Built-in audio player: play MP3/AAC/OGG/FLAC/WAV/MIDI from the browser with
  seek slider, ±10-second skip and live position tracking.

## [1.0.0] — 2026-09-11

### Added

- File manager with list/grid views, multi-select, breadcrumb navigation, favorites,
  recent files, hidden-file toggle, copy/move clipboard, search.
- Archive engine: ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, TAR.ZSTD, GZIP, BZIP2, XZ,
  ZSTD — create, extract, list, test, preview; password-protected ZIP (AES).
- Archive viewer with entry search, preview, selected-entry extraction, metadata and
  compression ratio.
- Root mode: passive detection, root file browser, permissions/ownership, chmod/chown,
  symlink creation, mount info (safe, timeout-guarded shell abstraction).
- Tools: storage analyzer, duplicate finder, hash calculator, APK inspector, text
  editor, hex viewer, batch renamer, file comparison, log analyzer.
- AI Center: Gemini + OpenRouter providers, streaming chat, encrypted API keys,
  provider fallback, privacy controls.
- Settings: theme (light/dark/system, dynamic color), file browser, archive, root,
  and AI preferences — all functional.
- Operation queue with live progress and persisted history.
- Material 3 UI, adaptive launcher icon, splash screen, dark/light themes.
- Unit tests for path safety, checksums, formatting and batch rename.
- Documentation: README, ARCHITECTURE, SECURITY, ROOT_ACCESS, TESTING, RELEASE,
  PRIVACY, CHANGELOG, FINAL_REQUIREMENTS_AUDIT.
