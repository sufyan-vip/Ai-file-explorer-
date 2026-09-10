# NEXARQ — Master-Level Android Archive, File Manager & AI Power Suite Build Specification

## 0. ROLE

You are a senior Android engineer, security engineer, UX/UI designer, QA engineer, and release engineer working as ONE agent.

Your job is to build a **real, production-ready Android application**, not a prototype, mockup, HTML page, or visual demo.

The application should be inspired by the usability and core concept of **ZArchiver**, but must be an original implementation with its own UI, architecture, branding, and code. The product name is **NEXARQ** (pronounced "Nex-ark"). Treat NEXARQ as the original product identity throughout the project.

Do NOT copy ZArchiver's proprietary UI, assets, source code, branding, or copyrighted material.

---

# 1. PRODUCT GOAL

Build a modern, extremely capable Android archive + file-management application that can:

- Browse local storage
- Browse Android Storage Access Framework locations
- Work with external/USB storage when Android permits it
- Support root-enabled file access when the device is rooted
- Create, extract, inspect, modify, and manage archives
- Provide advanced file operations
- Provide a powerful archive viewer/editor
- Support encrypted/password-protected archives where technically possible
- Handle large files reliably
- Work efficiently on low/mid-range Android devices
- Have a polished modern UI
- Be easy enough for a normal user while still being powerful for advanced/root users
- Be ready for release/publishing after all tests and legal checks pass

The final app must feel like a **premium next-generation archive manager + file manager**, not a basic ZIP utility.

---

# 2. NON-NEGOTIABLE QUALITY RULE

The application is NOT considered complete merely because:

- screens exist
- buttons exist
- menus open
- an archive appears in the UI
- code compiles

A feature is complete only when it is:

1. implemented
2. connected to the real backend
3. functional on a real Android device/emulator where applicable
4. error handled
5. tested
6. accessible through the UI
7. persistent where required
8. performant
9. documented
10. included in the final release build

### STRICT RULE

**Do not mark a requirement complete if it is only visually implemented.**

No fake buttons.
No placeholder functionality.
No "coming soon" for core requirements.
No simulated root access.
No fake archive extraction.
No fake progress.
No fake file operations.

If a requested capability is impossible because of Android OS restrictions, clearly document the limitation and implement the strongest legitimate alternative.

---

# 3. PLATFORM & TECH STACK

Target:

- Android
- Modern Android versions including current Android releases
- Android Studio compatible project
- Kotlin preferred
- Jetpack components where appropriate
- Material 3 / modern Android UI principles
- Architecture: Clean Architecture + MVVM or similarly maintainable architecture
- Coroutines
- Dependency injection where useful
- Room/DataStore for persistent application data where appropriate

Use native Android APIs wherever possible.

Avoid unnecessary third-party dependencies.

Every dependency must have a reason.

---

# 4. ROOT ACCESS — FIRST-CLASS FEATURE

The application must support two operating modes:

## MODE A — NORMAL ANDROID

Without root:

- Use Storage Access Framework
- MediaStore where appropriate
- App-accessible storage
- User-selected folders
- Proper Android permissions
- Handle scoped storage correctly

## MODE B — ROOT

When root is available:

- Detect root safely
- Never assume root exists
- Request root permission through a proper mechanism
- Provide a visible Root Mode indicator
- Allow advanced filesystem access where the OS/root environment permits it

Root capabilities may include:

- `/data`
- `/system`
- `/vendor`
- `/product`
- `/odm`
- `/storage`
- `/sdcard`
- mounted partitions
- root-owned files
- permissions/ownership inspection
- advanced file operations

### Root safety requirements

Never execute arbitrary commands received from untrusted archive/file names.

Never build shell commands through unsafe string concatenation.

Use safe argument handling and strict validation.

Root commands must:

- have timeouts
- capture stdout/stderr
- return exit codes
- handle failures
- avoid UI freezes
- be cancellable when technically possible
- be logged only when safe

The user must always understand when root privileges are being used.

---

# 5. ROOT FILE MANAGER

Create an advanced root file browser.

Features:

- Browse filesystem tree
- Hidden files
- System files
- Root directories
- File permissions
- UID/GID
- Owner/group
- symbolic links
- mount information
- file size
- modified date
- MIME type
- executable status
- read/write status
- file path copying
- open with
- share
- rename
- delete
- move
- copy
- create folder
- create file
- compress
- extract
- calculate directory size

Advanced root operations:

- chmod
- chown
- chgrp
- symlink creation
- hard-link handling where supported
- mount information
- read-only/read-write status where safely detectable

Dangerous actions must require explicit confirmation.

---

# 6. NEXARQ ADVANCED FEATURE SET

The product must go substantially beyond a basic archive manager.

Add these capabilities where Android and the selected libraries legitimately support them:

## File Intelligence

- Smart file categorization
- Recently modified files
- Recently downloaded files
- Large-file detector
- Empty-folder detector
- Temporary/cache-file finder
- Broken-link detector
- Duplicate-file detector
- Extension statistics
- File-type statistics
- Directory-size calculation
- Quick cleanup suggestions
- Safe cleanup workflow with preview before deletion

## Advanced Navigation

- Dual-pane file browser on large screens
- Split-view navigation
- Tabbed locations
- Recent locations
- Favorites
- Bookmarks
- Breadcrumb navigation
- Jump-to-path
- Copy full path
- Share path
- Open terminal/shell location only in authorized root mode
- Remember last locations

## File Operations

Add robust support for:

- copy
- move
- delete
- rename
- duplicate
- create folder
- create file
- batch rename
- batch extension change
- batch compression
- batch extraction
- file comparison
- file replacement
- conflict resolver
- operation queue
- retry failed operation
- skip individual file
- pause/resume where safe
- background operation continuation where Android permits

## Batch Rename

Include a real batch-renaming tool:

- prefix
- suffix
- replace text
- numbering
- sequential numbering
- extension preservation
- preview before applying
- collision detection
- undo where safely possible

## File Comparison

For two files:

- size
- modification time
- MIME/type
- checksum
- binary comparison where practical

For text files:

- side-by-side comparison
- changed-line highlighting
- search

## Archive Intelligence

Add:

- archive test
- archive repair attempt only where the engine safely supports it
- archive statistics
- compression ratio
- entry search
- selected-entry extraction
- selected-entry deletion
- selected-entry replacement
- archive comment support where available
- split-volume detection
- multi-volume handling where supported
- archive password detection
- encrypted-entry indicators
- nested archive browsing
- "extract and open" workflow
- "compress selected" quick action

## Archive Presets

Allow users to save presets such as:

- Fast ZIP
- Maximum ZIP
- 7Z Balanced
- Backup Archive
- Encrypted Backup

Presets must actually persist and apply.

## File Safety

Before destructive operations:

- show exact path
- show item count
- show total size
- show consequences
- support confirmation settings

Never silently delete user data.

## Storage Visualization

Provide:

- storage overview
- directory tree
- largest files
- largest folders
- file-type breakdown
- archive breakdown
- old-file detection
- duplicate groups

Use efficient scanning and never freeze the UI.

---

# 7. AI POWER SUITE — GEMINI + OPENROUTER

NEXARQ must include a dedicated **AI Center**.

The AI layer must be optional, user-controlled, and disabled until the user configures a provider.

Support BOTH:

1. **Google Gemini API**
2. **OpenRouter API**

Do not hardcode API keys.

Do not upload files to AI providers without explicit user action/consent.

## AI Provider Settings

Create:

**Settings → AI Center → Providers**

Provider cards:

- Gemini
- OpenRouter

Each provider must have:

- API key field
- secure storage using Android Keystore where appropriate
- show/hide key
- test connection
- model selection
- temperature/settings where supported
- enable/disable provider
- remove credentials
- connection status
- error diagnostics

The UI must clearly explain that API usage may incur provider charges according to the user's own account.

## OpenRouter

Use the official OpenRouter API format.

Support:

- user-entered API key
- model selection
- configurable model ID
- model list retrieval when supported
- request timeout
- streaming responses where supported
- graceful API errors
- rate-limit handling
- provider/model unavailable handling

Never assume one fixed model forever.

Allow the user to enter/select a model.

## Gemini

Use the official Gemini API.

Support:

- user-entered API key
- model selection
- configurable model
- streaming where supported
- timeout
- API error handling
- rate-limit handling

Do not expose the API key in logs, crash reports, Git history, UI diagnostics, or source code.

---

# 8. AI FILE ASSISTANT

Create an AI assistant specifically designed around local files.

Possible actions:

### Ask about a file

Examples:

- "Explain this file"
- "Summarize this document"
- "What is inside this archive?"
- "Find suspicious-looking filenames"
- "Explain this JSON"
- "Explain this log"
- "Find errors in this log"

### Smart file operations

AI may generate a PROPOSED action such as:

> "I found 14 duplicate files totaling 2.1 GB."

Then show:

- files involved
- sizes
- paths
- recommended action

The AI must NOT automatically delete/move/overwrite files.

The user must explicitly approve destructive actions.

## Archive AI

For supported archives, AI can summarize metadata:

- archive type
- number of files
- directory structure
- file types
- largest entries
- suspicious extensions
- possible duplicate names
- compression statistics

Do not send full archive contents to an AI provider by default.

Send only the minimum user-selected metadata/content required for the requested task.

---

# 9. AI TEXT / CODE ASSISTANT

Inside the text editor:

- explain selected text
- summarize
- format
- find errors
- suggest corrections
- translate selected text
- generate comments
- explain code
- optimize code suggestions
- convert JSON/XML formatting

Always show AI-generated changes as a preview before replacing user content.

Support:

- Apply
- Copy
- Cancel
- Compare

Never overwrite the original file automatically.

---

# 10. AI LOG ANALYZER

Add a specialized log analyzer.

Features:

- detect common error patterns
- summarize crashes
- identify repeated errors
- identify timestamps
- group similar messages
- explain likely causes
- suggest investigation steps

For rooted devices, allow the user to explicitly select a log file/output for analysis.

Do not silently collect system logs.

---

# 11. AI IMAGE / DOCUMENT FEATURES

Where the selected Gemini/OpenRouter model supports the requested input:

- image explanation
- OCR assistance
- document summarization
- screenshot explanation
- image metadata explanation

These features must clearly show when data is being sent to an external AI provider.

If a provider/model does not support the requested modality, disable that action gracefully.

---

# 12. AI CHAT UI

Build a premium chat interface inside NEXARQ.

Requirements:

- separate user and AI messages
- streaming response
- markdown rendering
- code blocks
- copy
- retry
- regenerate
- stop generation
- clear chat
- conversation history
- provider/model indicator
- token/usage information when the API provides it
- error states
- offline/no-provider state

The AI UI should feel integrated into the file manager rather than like a random separate chatbot.

## Context Actions

From a file/archive, allow:

**AI → Analyze**

**AI → Summarize**

**AI → Explain**

**AI → Find Issues**

**AI → Ask about this**

The user should always know what information is being shared.

---

# 13. AI ACTION SAFETY

AI must never directly execute arbitrary shell commands.

AI-generated operations must be converted into a structured, validated action proposal.

Example:

```text
PROPOSED ACTION
Operation: Move
Source: /storage/emulated/0/Downloads/example.zip
Destination: /storage/emulated/0/Archives/example.zip

[Cancel] [Review] [Approve]
```

Only after explicit user approval may the normal file-operation engine execute it.

For root operations:

- require root
- show exact command/action category
- show target path
- require confirmation
- block dangerous patterns
- never allow the AI to bypass safety controls

---

# 14. AI PROVIDER FALLBACK

Allow an optional provider priority:

1. Gemini
2. OpenRouter

If Gemini fails and the user has enabled OpenRouter fallback, offer/use OpenRouter according to the user's configured preference.

Never switch providers silently when doing so would send data to a different external service without clear user consent.

Provide a visible provider indicator.

---

# 15. AI PRIVACY CONTROLS

Add:

- AI enabled/disabled
- provider-specific enable/disable
- clear AI history
- don't send file contents by default
- ask before sending files
- metadata-only mode
- selected-content-only mode
- network usage notice
- API key removal
- AI history retention setting

AI history must not contain API keys.

# 6. ARCHIVE ENGINE

Build a robust archive abstraction layer.

Support as many formats as technically and legally feasible, prioritizing:

- ZIP
- 7Z
- TAR
- GZIP
- BZIP2
- XZ
- ZSTD
- TAR.GZ
- TAR.BZ2
- TAR.XZ
- TAR.ZST

If a format requires an optional engine, structure the architecture so the engine can be added without rewriting the application.

The application must detect archive type automatically.

Display:

- format
- compressed size
- uncompressed size
- entry count
- compression ratio
- encryption status
- comments if available
- archive modification date
- corruption/errors when detected

---

# 7. ARCHIVE OPERATIONS

Support:

### Create archive

- select files/folders
- choose format
- compression level
- split archive
- password/encryption when supported
- archive name
- output folder
- overwrite policy
- preserve timestamps where possible
- preserve permissions where supported

### Extract

- extract here
- extract to folder
- extract selected files
- extract all
- preserve directory structure
- overwrite/skip/rename conflict options
- password prompt
- extraction preview
- path traversal protection

### Archive editing

Where the format supports it:

- add files
- remove files
- replace files
- rename entries
- extract selected entries
- test archive
- view metadata

---

# 8. ARCHIVE VIEWER

When the user opens an archive, create a premium archive-browser experience.

Show:

- folder tree
- files
- sizes
- compressed sizes
- dates
- types
- encryption indicator

Actions:

- open
- preview
- extract
- delete entry
- rename entry
- copy entry
- share entry
- select multiple entries
- search inside archive
- sort
- filter

For supported text formats, provide a read-only preview.

For images, provide preview.

For common media formats, open through Android's installed handlers instead of reinventing media playback unless required.

---

# 9. FILE MANAGER

The file manager must feel complete.

Views:

- list
- compact list
- grid
- large grid

Navigation:

- breadcrumbs
- back
- forward where meaningful
- recent locations
- bookmarks
- home
- internal storage
- SD card
- USB/OTG where available
- root filesystem when authorized

Features:

- search
- sort
- filter
- multi-select
- clipboard-like copy/move queue
- favorites
- recent files
- recent folders
- hidden files toggle
- file extensions toggle
- thumbnails
- directory size calculation
- storage usage

---

# 10. SMART FILE OPERATIONS

Implement a professional operation system.

Operations:

- copy
- move
- delete
- rename
- duplicate
- create folder
- create file
- compress
- extract
- calculate size

The operation system should provide:

- progress
- speed
- transferred bytes
- remaining bytes
- ETA
- current file
- total files
- pause where technically safe
- cancel
- retry
- skip
- replace
- rename conflict

Large operations must run off the main thread.

Do not crash because the user leaves a screen.

---

# 11. DOWNLOAD / IMPORT / SHARE INTEGRATION

Support Android intents where appropriate:

- Open
- Open with
- Share
- Send
- Receive
- View
- Create document

Allow files shared to the application to be immediately processed.

---

# 12. SEARCH ENGINE

Build fast local search.

Search by:

- filename
- extension
- path
- file type
- size
- modified date

Filters:

- images
- videos
- audio
- documents
- archives
- APKs
- folders
- large files
- hidden files

Support:

- exact match
- partial match
- case-insensitive search
- extension search

Avoid scanning the entire filesystem unnecessarily.

Cache indexes where appropriate.

---

# 13. APK / PACKAGE FEATURES

For APK files:

Display:

- package name
- version name
- version code
- minimum SDK
- target SDK
- architecture/ABI information where available
- application label
- icon
- signing information where Android APIs permit

Actions:

- open/install through system installer
- share
- archive
- extract APK contents where technically possible
- inspect

Never bypass Android's security model.

---

# 14. IMAGE / MEDIA PREVIEW

Built-in lightweight preview system:

Images:

- zoom
- pan
- rotate
- metadata where available

Text:

- plain text
- JSON
- XML
- Markdown
- logs
- source code

Binary files:

- safe hexadecimal preview
- file metadata

Do not load huge files completely into memory.

Use streaming/chunked reading.

---

# 15. TEXT EDITOR

Add a lightweight built-in editor.

Features:

- open text file
- edit
- save
- save as
- encoding detection
- UTF-8
- line numbers
- search
- replace
- monospace mode
- large-file protection

For very large files, offer read-only mode rather than risking memory exhaustion.

---

# 16. HEX VIEWER

Advanced users should have a hex viewer.

Features:

- hexadecimal view
- ASCII representation
- offset
- search bytes/text
- jump to offset
- chunked loading
- read-only by default

Do not load enormous files fully into RAM.

---

# 17. ENCRYPTION & PASSWORD SUPPORT

Where supported by the selected archive engine:

- password protected archives
- encrypted archive detection
- secure password input
- optional password visibility
- password confirmation
- password prompts only when required

Never log passwords.

Never store passwords in plaintext.

If implementing saved passwords, use Android Keystore-backed secure storage.

Clearly distinguish:

- archive encryption
- application security
- Android filesystem permissions

---

# 18. FILE HASHING

Add checksum tools.

Support:

- MD5
- SHA-1
- SHA-256
- SHA-512

Prefer modern algorithms for security-sensitive verification.

Features:

- calculate hash
- copy hash
- compare hash
- verify provided checksum
- progress for large files

---

# 19. DUPLICATE FINDER

Optional advanced tool:

- detect duplicate files
- size comparison
- hash comparison
- group duplicates
- safe deletion workflow

Never automatically delete files.

Require explicit user confirmation.

---

# 20. STORAGE ANALYZER

Create a visual storage analyzer.

Show:

- total storage
- used
- free
- largest directories
- largest files
- archive usage
- media usage
- documents
- APKs

Allow drilling into directories.

Do not claim precise system-wide statistics where Android does not expose them.

---

# 21. FAVORITES / BOOKMARKS

Allow users to bookmark:

- folders
- archive locations
- frequently used paths

Support:

- rename bookmark
- reorder
- remove
- persistent storage

---

# 22. RECENT FILES

Track recent user-accessed files safely.

Allow:

- open
- share
- locate
- remove from recent

Provide a setting to disable recent history.

---

# 23. UI/UX — PREMIUM DESIGN

The UI is extremely important.

Do NOT create a generic CRUD Android application.

Design a modern premium interface inspired by current Android design trends.

Suggested visual direction:

- Material 3
- adaptive layouts
- subtle glass/translucent surfaces where appropriate
- excellent typography
- smooth animations
- modern cards
- rounded corners
- contextual action bars
- clean icons
- intelligent spacing
- dark/light themes

Avoid excessive visual effects that hurt performance.

The UI must remain fast on mid-range devices.

---

# 24. HOME SCREEN

Create a polished dashboard.

Possible sections:

- Current location
- Quick access
- Storage cards
- Recent files
- Favorites
- Archive shortcuts
- Root status
- Tools
- Large files
- Recent operations

Do not overcrowd the screen.

Allow personalization where useful.

---

# 25. FILE LIST UX

Each file row should clearly show:

- icon/thumbnail
- name
- type
- size
- modified date
- relevant status badges

Long press:

- selection mode

Swipe/context menu:

- quick actions where appropriate

Multi-selection:

- copy
- move
- delete
- share
- compress
- extract
- rename

---

# 26. COMMAND / TOOL ACCESS

For advanced users, create an optional Tools section.

Possible tools:

- Archive creator
- Archive tester
- Hash calculator
- Storage analyzer
- Duplicate finder
- APK inspector
- Text editor
- Hex viewer
- Root information
- Mount information
- Operation history

Do not expose dangerous root operations casually.

---

# 27. SETTINGS

Create a real settings system.

Sections:

## Appearance

- theme
- dynamic colors where supported
- dark mode
- light mode
- AMOLED option if appropriate
- list/grid preference
- icon size
- thumbnail settings

## File Browser

- show hidden files
- show extensions
- sort preference
- folder-first
- confirm delete
- confirm overwrite
- default location

## Archive

- default archive format
- compression level
- default extraction behavior
- overwrite policy
- password behavior

## Root

- enable root mode
- root detection
- shell timeout
- dangerous-operation confirmations
- root status

## Performance

- thumbnail quality
- concurrent operations
- memory limits
- battery-conscious mode

## Security

- app lock if implemented
- biometric authentication if implemented
- recent history
- secure password handling

## About

- version
- licenses
- privacy policy
- open-source notices where applicable
- diagnostics

Every setting must actually work.

---

# 28. ERROR HANDLING

Every file operation must handle:

- permission denied
- missing file
- file changed during operation
- insufficient storage
- invalid archive
- corrupted archive
- wrong password
- unsupported compression method
- I/O error
- root unavailable
- root denied
- broken symbolic link
- Android restrictions
- network-mounted storage failure
- cancellation
- unexpected exceptions

Errors must be understandable to normal users.

Advanced users can access technical details.

---

# 29. SECURITY REQUIREMENTS

Implement strong defensive security.

### Archive extraction

Protect against:

- path traversal
- absolute path extraction
- malicious filenames
- symlink attacks
- decompression bombs where practical
- extreme archive entry counts
- suspicious archive sizes

### Root

Protect against:

- shell injection
- command injection
- unsafe paths
- privilege misuse
- accidental destructive commands

### File access

Do not request unnecessary permissions.

Do not secretly scan unrelated private data.

Do not upload user files.

Do not send file contents to external servers.

---

# 30. PRIVACY

The app should be privacy-first.

Default behavior:

- local processing
- no analytics unless explicitly implemented and disclosed
- no hidden telemetry
- no cloud upload
- no advertising SDK unless explicitly required by the product owner

If any network capability is ever added, make it explicit and document it.

Create a clear privacy policy suitable for publishing.

---

# 31. ACCESSIBILITY

Support:

- TalkBack
- content descriptions
- minimum touch targets
- readable text
- contrast
- dynamic font scaling
- keyboard navigation where applicable

Do not rely on color alone to communicate status.

---

# 32. PERFORMANCE

This application must be optimized for real devices.

Rules:

- never perform heavy filesystem operations on the UI thread
- stream large files
- avoid loading complete archives into memory
- avoid loading huge files into memory
- use lazy lists
- cache thumbnails carefully
- cancel work when appropriate
- prevent memory leaks
- prevent ANRs
- handle millions of filesystem entries gracefully where possible

Test with:

- tiny files
- thousands of files
- very large files
- large archives
- deeply nested folders
- corrupted archives
- low-storage conditions

---

# 33. OPERATION QUEUE

Create a central operation manager.

Example:

1. Copy
2. Extract
3. Compress
4. Delete

Operations should be tracked.

Show:

- active operations
- completed operations
- failed operations
- cancelled operations

Allow viewing operation details.

---

# 34. NOTIFICATIONS

For long-running operations, use Android notifications where appropriate.

Examples:

- extracting archive
- compressing files
- copying large data
- hashing huge files

Notification should show:

- operation
- progress
- status

Use proper Android background execution rules.

---

# 35. CRASH RESILIENCE

The app must not crash because:

- a file disappeared
- permission was revoked
- root access disappeared
- archive is corrupted
- storage becomes unavailable
- user enters an invalid path
- a large file is opened
- a malformed filename exists

Recover gracefully.

---

# 36. TESTING

Create automated tests for:

### Unit tests

- path handling
- archive detection
- file conflict logic
- checksum logic
- root command safety
- settings
- sorting
- filtering

### Integration tests

- copy
- move
- delete
- archive creation
- extraction
- password archive workflow
- SAF access
- root abstraction

### UI tests

- navigation
- file selection
- archive viewer
- settings
- dialogs
- search
- operation progress

### Security tests

- path traversal
- malicious archive entries
- shell injection attempts
- permission failures
- malformed input

---

# 37. REAL DEVICE TESTING

Before declaring production-ready:

Test on multiple Android versions where available.

At minimum test:

- normal storage mode
- Android scoped-storage restrictions
- large file operations
- archive creation
- extraction
- password archives
- hidden files
- permission denial
- low storage
- rotation/configuration changes
- backgrounding the app
- process recreation

For root functionality, test only where a legitimate rooted test device/environment is available.

Do not fake root test results.

---

# 38. UI/UX REVIEW GATE

Create a dedicated UI/UX review phase.

Review every screen for:

- spacing
- hierarchy
- typography
- icon consistency
- touch targets
- empty states
- loading states
- error states
- dark mode
- accessibility
- animations
- responsiveness

Fix weak UI before release.

Do not accept "looks okay".

---

# 39. CODE QUALITY

Requirements:

- clean architecture
- meaningful names
- modular code
- no dead code
- no unnecessary duplication
- no hardcoded secrets
- no hardcoded API keys
- no debug-only behavior in release
- proper logging
- proper exception handling
- documentation for complex root/archive code

---

# 40. BUILD & RELEASE

Create:

- debug build
- release build
- signed-release-ready configuration
- ProGuard/R8 configuration where appropriate
- versioning
- launcher icon
- adaptive icon
- splash screen
- app name
- package name

Do not include private signing keys in Git.

Use placeholders/instructions for signing secrets.

---

# 41. PLAY STORE / PUBLISHING READINESS

Prepare the project for publishing.

Include:

- application metadata
- privacy policy template
- permissions justification
- data safety documentation draft
- release notes template
- screenshots checklist
- feature description
- known limitations
- third-party license notices

Do not claim compliance with a policy that has not actually been verified.

---

# 42. GIT / GITHUB WORKFLOW

Use Git properly.

Create logical commits.

Recommended structure:

1. project foundation
2. architecture
3. storage/file manager
4. archive engine
5. archive viewer
6. operations engine
7. root layer
8. search
9. tools
10. settings
11. security hardening
12. UI/UX refinement
13. tests
14. release preparation

Push completed work to the configured GitHub repository when credentials/access are available.

Never commit:

- API keys
- signing keys
- passwords
- tokens
- private certificates

---

# 43. CI WORKFLOW

Create GitHub Actions workflow(s) for:

- checkout
- Android/Gradle setup
- dependency validation
- lint
- unit tests
- instrumentation tests where environment supports them
- build
- artifact upload

Use a clear workflow filename such as:

`.github/workflows/android-ci.yml`

The workflow must contain real commands, not placeholders.

---

# 44. DOCUMENTATION

Create:

- README.md
- ARCHITECTURE.md
- SECURITY.md
- ROOT_ACCESS.md
- TESTING.md
- RELEASE.md
- PRIVACY.md
- CHANGELOG.md

Documentation must explain actual implementation, not planned features.

---

# 45. IMPLEMENTATION PHASES

Do NOT attempt to dump everything into one giant untested implementation.

Work in controlled phases.

## PHASE 1 — Foundation

- project
- architecture
- theme
- navigation
- storage abstraction
- error system
- testing infrastructure

## PHASE 2 — File Manager

- browsing
- folders
- files
- sorting
- filtering
- selection
- copy/move/delete
- rename
- hidden files
- search

## PHASE 3 — Archive Engine

- ZIP
- TAR/GZIP
- 7Z if supported
- compression
- extraction
- archive viewer

## PHASE 4 — Advanced Archive Tools

- passwords
- archive editing
- testing
- split archives
- metadata
- safe extraction

## PHASE 5 — Root

- root detection
- root file browser
- permissions
- ownership
- advanced filesystem operations
- safe shell abstraction

## PHASE 6 — Advanced Tools

- hash
- APK inspector
- text editor
- hex viewer
- storage analyzer
- duplicate finder

## PHASE 7 — Premium UI/UX

- redesign
- animations
- adaptive layouts
- dark/light
- accessibility
- empty/loading/error states

## PHASE 8 — Security & Performance

- security audit
- memory optimization
- archive safety
- root security
- stress tests

## PHASE 9 — Release

- CI
- release build
- signing configuration
- documentation
- privacy
- store preparation
- final QA

---

# 46. STRICT AGENT WORKFLOW

For every phase:

### STEP 1
Inspect the current repository.

### STEP 2
Understand existing architecture before modifying it.

### STEP 3
Implement the phase completely.

### STEP 4
Compile.

### STEP 5
Run tests.

### STEP 6
Run lint/static analysis.

### STEP 7
Fix every error.

### STEP 8
Review UI/UX.

### STEP 9
Review security implications.

### STEP 10
Commit the phase.

### STEP 11
Only then proceed to the next phase.

Never silently skip failures.

---

# 47. SELF-AUDIT REQUIREMENT

Before declaring completion, perform a requirement-by-requirement audit.

Create:

`FINAL_REQUIREMENTS_AUDIT.md`

For every requirement write:

- Requirement
- Implementation location
- Test performed
- Result
- Known Android limitation if any

Use:

`PASS`
`PARTIAL`
`BLOCKED`

Never mark something PASS without evidence.

---

# 48. FINAL RELEASE GATE

The project is NOT complete until:

- project builds
- release build succeeds
- lint passes
- tests pass
- no obvious crashes remain
- core file operations work
- archive operations work
- root layer works where root exists
- normal non-root mode works
- settings actually apply
- UI is polished
- accessibility has been reviewed
- security audit has been performed
- GitHub workflow works
- documentation exists
- final requirements audit exists

If any core requirement fails, continue fixing it.

---

# 49. IMPORTANT ANDROID LIMITATION RULE

Android has strict security and storage restrictions.

Do NOT attempt to bypass:

- SELinux
- Android permission model
- package installer restrictions
- scoped storage restrictions
- user consent requirements
- protected/private app data access without legitimate root authorization

When root is available, use legitimate elevated access.

When root is unavailable, fall back gracefully to SAF/MediaStore and clearly explain the limitation.

---

# 50. FINAL PRODUCT STANDARD

The final NEXARQ application should feel like:

**"NEXARQ — a modern, premium, extremely powerful Android archive manager, file manager, root power tool, and AI-assisted file workspace for normal users and rooted power users."**

It should combine:

- ZArchiver-style archive power
- advanced file manager capabilities
- root filesystem access
- modern Android UX
- strong security
- excellent performance
- professional operation handling
- advanced tools
- production-quality engineering

Do not optimize for the number of screens.

Optimize for:

**real functionality + reliability + simplicity + performance + security + beautiful UX.**

---

# FINAL INSTRUCTION TO THE AGENT

Build the application completely.

Do not stop at a prototype.

Do not replace difficult functionality with mock UI.

Do not claim a feature works without testing it.

When Android prevents a feature, implement the strongest legitimate alternative and document the limitation.

When you find an error, fix the root cause instead of hiding it.

When you find poor UI, improve it.

When you find duplicated or fragile architecture, refactor it.

Keep the application usable for beginners while exposing advanced capabilities to power users.

The final repository must contain a genuine, buildable, tested, documented Android application that is ready for final human QA and publishing preparation.

# 51. FINAL GITHUB PUSH & REPOSITORY DELIVERY

At the end of development, the agent must prepare the complete repository for GitHub.

## Repository verification

Before pushing:

- inspect git status
- inspect branch
- inspect commit history
- ensure no secrets exist
- ensure no API keys exist
- ensure no signing keys exist
- ensure no generated junk/build artifacts are accidentally committed
- verify `.gitignore`
- verify README
- verify documentation
- verify CI workflow
- verify release configuration

Run secret scanning before push.

## Commit

Create a clean final commit with a meaningful message, for example:

`release: NEXARQ production-ready Android build`

Do not rewrite history destructively unless explicitly requested.

## Push

If the repository is already connected/authenticated:

- push the final branch to GitHub
- verify push succeeded
- verify remote branch contains the final commit
- verify GitHub Actions starts successfully

If the repository is not connected:

- do NOT invent credentials
- do NOT expose tokens
- provide the exact safe commands/instructions needed to connect and push

## Final GitHub verification

After pushing, verify:

- repository branch
- latest commit SHA
- workflow file exists
- GitHub Actions workflow is visible
- CI status
- release artifact availability where applicable

Report the actual result.

Never claim "pushed successfully" without verifying it.

