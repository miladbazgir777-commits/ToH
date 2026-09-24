# Milestone 6 — Typed Clinical Objects and Links

Version: **0.6.0-m6** (`versionCode 7`)

This milestone extends the handwriting canvas with optional structured typed objects. Handwriting remains the primary workflow; typed content behaves like movable reference cards layered into the same infinite note.

## Implemented

### Typed objects

- Freeform movable text boxes
- Compact labels / callouts
- Medication blocks with separate fields for:
  - medication name
  - dose
  - route
  - frequency
  - duration
  - note
- Small tables entered as rows/cells and rendered as an on-canvas grid
- Checklists entered one item per line
- Finger tap toggles checklist items without entering drawing mode

### Links

- Web-link canvas objects
- URL normalization for links entered without a scheme
- Finger tap opens a web link through Android's browser intent
- Internal-link picker searches all active Fields, Topics, and Notes
- Internal link objects navigate directly to the selected journal destination
- Internal links use normal Android/Compose back navigation after opening

### Editing and placement

- New `Content` object list in the note editor
- Jump to any typed object
- Edit existing objects in place
- Resize typed objects
- Lock/unlock against lasso movement
- Bring forward / send backward within typed-object layering
- Delete with the editor's normal undo history
- Typed objects participate in section ownership, collapse, reorder and section growth
- Lasso selects/moves ink, media and typed objects together

### Search

- `Find` searches typed content in the current handwritten note across:
  - text boxes and labels
  - medication fields
  - table cells
  - checklist text
  - link labels/metadata
- Handwritten vector ink is deliberately **not OCR searched**, matching the handwriting-first design.

### Persistence

No SQLite table migration is required. Rich content objects are serialized inside the existing `CanvasDocument` JSON payload. New fields have defaults so documents from Milestone 5 remain readable.

## Main implementation files

- `model/JournalModels.kt` — richer `TextItem`, `TextObjectKind`, checklist entries
- `content/ContentCanvasOps.kt` — pure add/update/remove/scale/z-order/checklist transforms
- `ink/InkSurfaceView.kt` — typed-object rendering, lasso integration, finger link/checklist taps
- `ui/components/CanvasContentDialogs.kt` — create/edit/search/internal-link UI
- `ui/screens/NoteEditorScreen.kt` — insertion workflow and object inspector
- `MainActivity.kt` — internal journal-link destinations

## Tablet test sequence

1. Create/open a Topic and Master Summary.
2. Handwrite several lines normally; verify stylus/finger behavior remains unchanged.
3. Tap **Insert → Text box**, type a paragraph, save it, and verify it appears on the canvas.
4. Select **Lasso**, encircle the text object and move it with the stylus.
5. Insert a label and a medication block; verify the medication fields render as a compact card.
6. Insert a 2–3 column table and verify cell grid/text rendering.
7. Insert a checklist, then use a finger tap on individual boxes to toggle checked state.
8. Insert a web link and tap it with a finger; verify the browser opens.
9. Insert an internal link to another Field/Topic/Note and tap it; verify navigation and Back behavior.
10. Open **Content**, edit an object, resize it, lock it, and verify lasso no longer moves it while locked.
11. Use **Find** to locate a medication/table/checklist phrase and jump to the result.
12. Collapse/reorder the containing medical section and verify typed objects hide/move with the section.
13. Force-close and reopen the app; verify all typed content, checked states, links and positions persist.
14. Undo/redo insertion, deletion, movement and checklist toggles.

## Validation performed here

A JVM smoke test compiled and executed `ContentCanvasOps` against model stubs, validating object creation, checklist toggling and medication payload rendering. The container does not include a configured Android SDK/Gradle installation, so the Compose/native-view build must still be compiled in Android Studio before sideloading.

## Next milestone

Milestone 7 should implement the **emblem maker and visual identity layer**: layered inherited Field/Topic emblems, the Tome of Healing main crest, fantasy-modern card frames, default Deep Purple / Moon Silver / Deep Blue theme, and subtle launch/navigation glow effects.
