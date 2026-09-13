# NEXARQ — Architecture

## Principles

- **Clean layering.** UI → domain (core) → data, with no circular dependencies.
- **No DI framework.** A hand-rolled `AppContainer` (`data/AppContainer.kt`) wires the
  graph; it is explicit and easy to audit.
- **Everything heavy is off the main thread.** File, archive and scan work runs on
  `Dispatchers.IO` with coroutine cancellation and granular progress callbacks.
- **Manual navigation.** `ui/AppState.kt` uses a `Navigator` back-stack of sealed
  `Screen`s instead of URL routes — this avoids route-encoding problems with
  arbitrary filesystem paths.

## Layers

### `core/`
- `FileSystem` — safe, cancellable filesystem primitives: normalize/safeJoin
  (path-traversal protection), list/copy/move/delete/rename/measure, conflict
  policies, storage statistics.
- `Models` — `FileItem`, `ArchiveEntry`, operation records, bookmarks, recents,
  presets, chat messages.
- `FileType` — extension → category (icon, filter, MIME).
- `Format` — bytes/speed/ETA/ratio formatting.
- `Intents` — FileProvider-based open-with / share / install helpers.

### `archive/`
- `ArchiveEngine` — single abstraction over Apache Commons Compress (ZIP, 7Z, TAR,
  GZIP, BZIP2, XZ, ZSTD), zip4j (AES/ZipCrypto ZIP passwords), and the XZ/Zstandard
  codecs. Operations: `list`, `preview`, `extract`, `create`, `test`,
  `deleteEntries`, `renameEntry`, `addFiles`.
- `ArchiveSecurity` — traversal/absolute-path checks, entry-count and ratio
  (decompression-bomb) heuristics, safe-entry filtering.

### `root/`
- `RootManager` — passive root detection, `su`-based execution with individual
  argument quoting, a verb allow-list, deny-patterns, timeouts, and captured
  stdout/stderr/exit codes. Typed helpers (`ls -la` parsing, `cat`, `mount`,
  `chmod`, `chown`, `ln -s`).

### `data/`
- `SettingsRepository` — Jetpack DataStore-backed settings (every UI setting is real).
- `SecureStore` — Android Keystore AES/GCM encryption for AI keys / saved secrets.
- `JsonStore` — atomic JSON persistence for small lists.
- `RecentsRepository`, `BookmarkRepository`, `PresetRepository` — persistent lists.
- `OperationManager` — active-operation progress + persisted history.

### `ai/`
- `AiRepository` — Gemini (`generateContent` / `streamGenerateContent`) and
  OpenRouter (`chat/completions`) over OkHttp with SSE streaming, provider priority
  and optional fallback, usage reporting, encrypted keys, chat history.

### `search/`, `tools/`
- `SearchEngine` — bounded, rooted, filterable local search.
- `tools/` — `Hashing`, `ApkInspector`, `TextFile`, `HexViewer`, `DuplicateFinder`,
  `StorageAnalyzer`, `BatchRenamer`, `FileCompare`, `LogAnalyzer`.

### `ui/`
- Single-activity Compose app. Screens in `ui/screens/`, shared components in
  `ui/components/`, archive dialogs in `ui/dialogs/`, Material 3 theme in
  `ui/theme/`.

## Data flow (example: copy operation)

1. `BrowserScreen` calls `FileSystem.copy(...)` in a coroutine.
2. Progress is reported through `OperationManager.report(...)`.
3. `OperationsScreen` observes `OperationManager.active` / `history()`.
4. Settings (confirmations, conflict defaults) come from `SettingsRepository`.

## Extension points

- **New archive format** — implement a `listX`/`extractX` branch in `ArchiveEngine`
  and add the `ArchiveFormat` enum entry. No UI changes required.
- **New AI provider** — add an `AiProvider` case and a request/stream method in
  `AiRepository`.
