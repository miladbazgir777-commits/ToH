# Milestone 8 — Version History, Trash & Recovery

Version: **0.8.0-m8** (`versionCode 9`)

## Goal

Make destructive edits recoverable and make deletion explicit, inspectable, and safe while retaining the user's selected **last 10 note versions** and **Trash never auto-deletes** behavior.

## Implemented

### Note version history

Every note now has a persistent version timeline stored separately from the active note row.

- Keeps the latest **10 versions per note**.
- Handwriting autosave does **not** create a history row after every stroke.
- While a note is changing, the previous durable state is checkpointed at most once per **five-minute edit window**.
- **History** is available directly from the note editor top bar.
- **Create checkpoint now** captures the current note on demand.
- Each checkpoint stores:
  - vector canvas and all canvas objects,
  - structured/collapsible section metadata,
  - checkpoint time,
  - source modified time,
  - checkpoint label.
- Restoring an older version first protects the current state with a **Before history restore** checkpoint, then makes the selected version active.
- Individual historical checkpoints can be deleted without changing the active note.

The history table is intentionally separate from the active `journal_notes` table so normal hierarchy snapshot writes do not destroy version history.

### SQLite migration

Database schema is now version **2**.

A non-destructive migration from schema v1 creates:

- `note_versions`
- index `idx_versions_note_created`

Existing Fields, Topics, Notes, handwriting, attachments, emblems, and preferences are retained.

### Dedicated Trash

The root Field Library now exposes **Trash** with a live count.

Trash shows:

- deleted Field/Topic tree roots,
- independently deleted Notes,
- original hierarchy path,
- deletion timestamp.

Actions:

- **Restore** a deleted tree or standalone Note.
- **Delete forever** with explicit confirmation.
- **Empty Trash** with explicit confirmation.

Trash has **no automatic expiry**. Items remain until manually restored or permanently erased.

When a Field/Topic is moved to Trash, descendants deleted as part of the same operation are grouped under the deletion root instead of cluttering the Trash list as separate entries.

### Deletion protection

Fields and Topics now support **Lock against deletion** from their long-press action menu.

- A locked Field/Topic cannot be deleted.
- A locked descendant also protects its ancestor branch: deleting a Field cannot silently trash a protected nested Topic.
- The library identifies deletion-locked nodes.
- If a branch delete is blocked, the UI explains that a protected item must be unlocked first.

### Permanent cleanup

Permanent deletion removes:

- hierarchy/note database rows,
- the note's stored version history,
- app-private image/PDF files owned by the permanently deleted Note.

Normal Trash operations do **not** delete attachment bytes, allowing full restore before permanent deletion.

### Existing immediate Undo retained

The earlier snackbar-based Undo behavior for Delete remains. Trash provides the longer-term recovery path after the Undo snackbar has disappeared or after the app restarts.

## Validation performed in this environment

### Repository/model compilation

The Milestone 8 model and `PersistentJournalRepository` were syntax-compiled with `kotlinc` using narrow Android/SQLite/Flow stubs.

A JVM smoke test exercised:

1. Field and Topic creation.
2. Master Summary creation.
3. Manual version checkpoint creation.
4. Version retrieval.
5. Deletion lock on a nested Topic.
6. Ancestor delete refusal while a protected descendant exists.
7. Unlock and tree deletion.
8. Trash-root discovery.
9. Tree restore.
10. Master Summary independent-delete protection.

Result: **passed**.

### Still requires Android Studio/device validation

This container does not have a configured Android SDK/Gradle wrapper, so the Compose UI and actual `SQLiteOpenHelper` migration must still be built on Android Studio/emulator or a physical tablet.

## Android Studio test sequence

1. Install/run Milestone 7 first and create a Field → Topic → handwritten Master Summary.
2. Upgrade the installed app to Milestone 8 without clearing app data.
3. Confirm the existing hierarchy and handwriting survive the v1→v2 database migration.
4. Open the note → **History** → **Create checkpoint now**.
5. Change handwriting/sections, wait for autosave, then restore the earlier version.
6. Confirm the previous current state appears as a recovery checkpoint.
7. Repeat edits/checkpoints until more than 10 are generated; confirm only the newest 10 remain.
8. Long-press a Topic → **Lock against deletion**.
9. Try deleting its parent Field and confirm the operation is blocked.
10. Unlock it, delete the Field, open root **Trash**, and confirm only the tree root is listed.
11. Force-stop/reopen and restore the tree from Trash.
12. Delete a normal secondary Note and confirm it appears separately in Trash.
13. Permanently delete that Note and confirm its version history is also removed.
14. Test **Empty Trash** confirmation.
15. Confirm there is no time-based automatic Trash deletion.

## Next milestone

Implement the full **backup/import/restore system**: remembered Android Storage Access Framework destination, five rotating timestamped backups, complete journal/media/theme payload, import with Replace/Merge modes, and duplicate conflict handling.
