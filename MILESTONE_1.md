# Milestone 1 — Core Journal Workflow

Status: source implementation complete for the in-memory prototype.

## Included

- Field library
- Field → Topic navigation
- Nested Topic/Subtopic navigation
- Topic → Notes list
- Automatic Master Summary for every new Topic
- Selectable starter Master Summary template
- Create / rename / duplicate / move / delete workflow
- Deep duplication of Field/Topic trees
- Topic reparenting with cycle prevention
- Manual earlier/later ordering
- Pinning at Field, Topic/Subtopic, and Note level
- Per-container sorting by manual order, name, creation date, or modification date
- Created / modified timestamps surfaced in cards and action dialogs
- In-memory Trash semantics with quick Undo for delete
- Master Summary protection
- New normal Notes
- Breadcrumb path passed into the note editor
- Pause-based ink autosave and save on editor exit

## Validation performed here

The pure Kotlin data model/repository was compiled locally with `kotlinc` using a serialization annotation stub and the bundled coroutines runtime. A smoke test exercised:

- Field creation
- Topic creation
- Automatic Master Summary creation
- Nested subtopic creation
- Note creation/rename
- Deep Topic duplication
- Delete + restore
- Sort-mode updates
- Hierarchy path construction

The smoke test completed successfully.

## Not validated here

The full Android/Compose project could not be Gradle-built because this execution environment does not have a configured Android SDK/Gradle installation. Android Studio should be used for the next build/sync verification.

## Next milestone

Replace the in-memory repository with Room/SQLite persistence so Fields, Topics, Notes, metadata, and canvas documents survive app/process restarts.
