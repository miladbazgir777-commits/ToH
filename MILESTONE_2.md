# Milestone 2 — SQLite Persistence

Status: source implementation complete.

## Goal

Make the Milestone 1 journal survive activity/process/app restarts without changing the UI workflow.

## Implemented

- Added a real on-device SQLite database: `tome_of_healing.db`
- Persisted Field/Topic/Subtopic hierarchy
- Persisted Notes and Master Summaries
- Persisted created/modified timestamps
- Persisted pin states
- Persisted manual order and per-container sort modes
- Persisted deletion/Trash timestamps
- Persisted inherited emblem definitions
- Persisted structured medical sections
- Persisted vector handwriting canvas documents, including pressure-bearing stroke points
- Persisted root Field sort mode in app settings
- First-launch database initialization
- Transactional snapshot writes so multi-object hierarchy operations cannot leave a half-written tree
- Lifecycle checkpoint when the app moves to the background
- Database close on Activity destruction
- Tree restore/Undo no longer relies on an in-memory deletion batch; it uses the shared deletion timestamp, so the deleted tree remains recoverable after process recreation

## Storage design

SQLite uses three tables:

- `journal_nodes` — Fields, Topics and Subtopics
- `journal_notes` — Notes, Master Summaries, sections and vector canvas JSON
- `app_settings` — root-level preferences such as Field sort mode

Hierarchy/searchable metadata stays in normal SQLite columns. Evolving compound objects are stored as Kotlin-serialization JSON in their row:

- emblem definition
- section list
- vector canvas document

This gives the current app stable persistence without forcing the handwriting schema to become rigid before the handwriting engine is finished.

## Durability behavior

Every repository mutation is committed to SQLite transactionally. The editor still uses the selected pause-based autosave, so handwriting is written to the repository after ~900 ms of inactivity and again when leaving the editor. An extra repository checkpoint is performed in `Activity.onStop()`.

## Validation performed here

- The persistence-aware repository and full data model were syntax-compiled with `kotlinc` using Android/persistence stubs.
- Repository API compatibility with Milestone 1 was retained, so the existing Compose screens do not need workflow rewrites.
- The database schema and serialization mappings were reviewed against every persisted model field.

## Not validated here

This environment does not contain an Android SDK/Gradle installation, so the actual `SQLiteOpenHelper` implementation and Compose app cannot be device/emulator-built here. Android Studio should be used to run the restart test below.

## Required Android Studio smoke test

1. Install/run the app.
2. Create a new Field and Topic.
3. Open the Topic's Master Summary and draw several stylus strokes.
4. Return to the Field Library.
5. Force-stop the app.
6. Reopen it.
7. Confirm hierarchy, note metadata and handwriting are unchanged.
8. Delete a Topic, relaunch, and confirm it remains deleted rather than reappearing.

## Next milestone

Upgrade the handwriting engine: true infinite vertical scrolling, pressure-sensitive rendering, eraser, undo/redo stack, customizable tool state and finger-navigation behavior.
