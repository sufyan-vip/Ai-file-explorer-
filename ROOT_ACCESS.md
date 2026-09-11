# NEXARQ — Root Access

NEXARQ operates in two modes. Root is an optional, first-class feature — never a
requirement and never assumed.

## Mode A — Normal Android (default)

Without root, NEXARQ uses the platform storage model:

- Direct `java.io.File` access to storage the OS grants the app (shared external
  storage as permitted).
- **Storage Access Framework** is the intended path for user-selected folders the
  app cannot reach directly; NEXARQ can open files received via `ACTION_VIEW`/`SEND`
  intents through `ShareReceiverActivity`.
- MediaStore is used by the OS for media scans; NEXARQ does not bypass scoped storage.

> Android scoped storage restricts direct access to other apps' private data. NEXARQ
> respects those restrictions and documents them rather than working around them.

## Mode B — Root (when available)

When a root binary is detected, the **Root browser** and advanced operations unlock:

- Browse `/`, `/data`, `/system`, `/vendor`, `/product`, `/storage`, mounted
  partitions, etc.
- View permissions, owner/group, symlinks, sizes, mount info.
- `chmod`, `chown`, `chgrp`, `ln -s` (with confirmation).
- Read root-only files (`cat` with a size cap).

## Safety model

- Detection is passive (checks known `su` paths + `PATH`). It is refreshed on demand.
- Execution goes through `RootManager.execRoot`, which:
  1. verifies root availability,
  2. checks the verb against an allow-list,
  3. blocks dangerous patterns,
  4. shell-quotes each argument,
  5. runs `su` with a timeout and captures output/exit code.
- The UI always shows a visible root indicator; dangerous actions require explicit
  confirmation.
- The app never executes arbitrary strings from archive/file names.

## Legitimate testing

Root features are only tested on a legitimate rooted device or emulator environment.
NEXARQ does not attempt to bypass SELinux, the permission model, or the package
installer, and it does not fake root results.
