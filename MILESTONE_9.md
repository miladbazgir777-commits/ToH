# Milestone 9 — Full Backup, Import & Restore

Version: **0.9.0-m9** (`versionCode 10`)

## Goal

Implement the selected **manual, local-first full backup workflow** without passwords/encryption:

- remember one Android backup folder,
- create a timestamped full-journal backup only when **Back Up Now** is pressed,
- keep the newest **5** backups,
- import a backup with **Replace** or **Merge**,
- resolve merge conflicts with **Keep both / Replace existing / Keep newest**.

## Backup format

Backups use the extension:

`TomeOfHealing_YYYY-MM-DD_HHMMSS.tohbackup`

The file is a ZIP-compatible application backup container with:

- `manifest.json` — format/app version, creation time, object counts, attachment count,
- `payload.json` — complete logical journal state,
- `attachments/<noteId>/...` — app-private image/PDF bytes required by each Note and its retained history.

No encryption/password layer is added, matching the selected design.

### Logical state included

- Fields and unlimited Topic/Subtopic hierarchy,
- node/note IDs and parent relationships,
- titles, pinning, deletion locks, created/modified timestamps,
- manual ordering and sort modes,
- Trash state,
- inherited/custom emblem definitions,
- all Notes and Master Summaries,
- structured/collapsible section metadata,
- vector handwriting,
- typed clinical objects, tables, checklists and links,
- image/PDF canvas objects,
- retained **10-version** note history,
- current visual theme,
- library layout mode,
- handwriting toolbar dock/order/favorite-color preferences.

The remembered backup folder itself is deliberately **not** restored from a backup because its SAF URI is device-specific.

## Android Storage Access Framework

The root Field Library now has a **Backup** action.

The Backup screen supports:

1. **Choose folder** using Android's Storage Access Framework.
2. The selected tree URI is remembered and persistable access is requested.
3. **Back Up Now** writes directly to the remembered destination.
4. The screen displays the latest backup filename/time.
5. No automatic cloud/background schedule is created.

The chosen destination can be any writable provider exposed through Android's folder picker, such as local storage, SD card, USB storage, or a compatible cloud-backed document provider.

## Five-backup rotation

After a successful backup, Tome of Healing enumerates its own timestamped `.tohbackup` files in the remembered directory and retains only the newest **5**.

Older matching Tome of Healing backups are deleted automatically. Unrelated files are untouched.

## Import modes

Selecting **Import Backup** first validates and inspects the backup, then displays its creation date and counts before any current data is changed.

### Replace current journal

- The imported journal becomes the complete active journal.
- Imported root sorting is restored.
- Imported note history is restored.
- Current attachment storage is swapped only after the backup payload has passed validation.
- File changes use a rollback directory while the SQLite state is replaced.
- Theme/layout restoration is enabled by default, but can be unchecked.

### Merge with current journal

The current root sort mode remains in place. Imported items are merged by their persistent internal IDs.

Conflict policies:

- **Keep both** — conflicting imported Node/Note IDs are assigned new UUIDs. Parent relationships and internal Field/Topic/Note links are remapped to the new IDs. Imported media is copied into the remapped Note directories.
- **Replace existing** — imported objects replace current objects that have the same IDs; non-conflicting imported objects are added.
- **Keep newest** — `modifiedAt` determines whether the current or imported Field/Topic/Note survives an ID conflict. Imported media/history is installed only for imported Notes that win the comparison.

For merge, restoring the imported theme/layout is optional and defaults off.

## Attachment safety

Backup collection does not assume an attachment physically lives under the current Note directory. It collects:

- every file already owned by the Note's attachment directory,
- every image/PDF path referenced by the active canvas,
- every image/PDF path referenced by retained history versions.

This also protects older/duplicated Notes whose canvas may still reference media created under an earlier Note directory.

During restore, every media path is rewritten to the destination device's current app-private attachment path.

ZIP extraction uses canonical-path validation to reject path traversal entries.

## SQLite integration

`PersistentJournalRepository` now exposes a stable `RepositoryState` for backup/import and a synchronized `replaceAllState` operation.

`JournalSqliteStore.replaceAll(...)` replaces:

- nodes,
- notes,
- note versions,
- root sort setting

inside one SQLite transaction before StateFlow values are updated.

No schema bump is required for Milestone 9; the database remains schema **v2**.

## Validation performed in this environment

### Pure Kotlin merge-engine compilation

`BackupModels.kt`, `BackupMergeEngine.kt`, and the complete journal model compiled with `kotlinc` using only a narrow serialization annotation stub.

Smoke tests passed for:

- Keep-both ID remapping,
- Topic/Note relationship remapping,
- internal Note-link remapping,
- attachment destination path rewriting,
- Replace-existing semantics,
- Keep-newest semantics and media winner selection.

### Backup-service syntax/type smoke compilation

`BackupService.kt` was compiled with narrow Android/SAF/coroutine/serialization/theme/repository stubs. This catches Kotlin syntax and contract mismatches in the backup orchestration while an Android SDK is unavailable here.

### Still requires Android Studio / physical tablet validation

A real Android build is still required to verify provider-specific SAF behavior and Compose rendering.

## Android Studio test sequence

1. Install Milestone 9 over Milestone 8 without clearing app data.
2. Confirm the existing journal/history/Trash survives the upgrade.
3. Open root **Backup** → **Choose folder** and choose a writable local or cloud-backed document folder.
4. Tap **Back Up Now** and verify a timestamped `.tohbackup` file appears.
5. Make journal changes and create five more backups; verify only the newest 5 Tome of Healing backups remain.
6. Add handwriting, one image, one PDF excerpt, one internal link, and a history checkpoint before creating another backup.
7. Delete/alter that content, then import the backup using **Replace current journal**.
8. Verify hierarchy, handwriting, image/PDF, history, Trash, theme/layout, and internal link targets restore.
9. Create a second journal state and import the same backup with **Merge → Keep both**. Confirm conflicting copies coexist and imported internal links open the imported/remapped destination.
10. Repeat with **Merge → Replace existing**.
11. Repeat with **Merge → Keep newest**, changing timestamps so one side is newer in each test.
12. Force-stop/reopen after each import and confirm the merged/restored state persists.
13. Move the backup to a different tablet/emulator and verify restored media paths work despite a different app-private root directory.

## Next milestone

Recommended Milestone 10: **production hardening and device validation** — S Pen/manufacturer-specific enhancements, performance profiling for very long notes, error/recovery polish, build/signing configuration, and producing the first installable release APK once an Android SDK/build environment is available.
