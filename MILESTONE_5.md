# Milestone 5 — Images, Camera, PDF Pages & Annotation

Version: **0.5.0-m5** (`versionCode 6`)

This milestone adds reference-media objects to the handwritten canvas while preserving the local-first/offline architecture.

## Implemented

### Image import and camera

- **Insert → Image** uses Android's picker and copies the selected file into the note's app-private attachment folder.
- **Insert → Take photo** uses a `FileProvider` camera target; no permanent camera storage permission is required.
- Imported media remains available offline even if the original gallery/document provider later disappears.
- Image aspect ratio is preserved on initial placement.

### Editable canvas objects

Image and PDF-page objects are persisted inside `CanvasDocument` with:

- x/y position
- width/height
- rotation
- opacity
- lock state
- object z-index
- normalized crop rectangle
- owning structured section
- optional caption
- original file name
- PDF source page number when applicable

The **Objects** panel can jump to or edit any reference object in the note.

Object editing supports:

- resize smaller/larger
- rotate 90°
- opacity slider
- lock/unlock
- crop
- bring forward / send behind
- caption
- delete from note

All object changes participate in the editor's existing undo/redo and pause-based autosave flow.

### Lasso + object movement

The existing lasso tool now selects both vector strokes and reference objects.

- Lasso around an image/PDF excerpt to select it.
- Drag a selection with the stylus to move it.
- Locked objects can be selected but are not moved.
- Annotation strokes always render above image/PDF objects.

### PDF workflow

- **Insert → PDF page / excerpt** copies the source PDF into app-private storage.
- Internal PDF picker shows page count and renders one page at a time using Android `PdfRenderer`.
- Navigate Previous/Next without leaving the note.
- Insert a full PDF page onto the infinite canvas.
- Enable **Crop excerpt** and set normalized left/top/right/bottom crop bounds before insertion.
- The original PDF remains unchanged; handwriting is stored separately as vector ink over the inserted page/excerpt.
- From a PDF object, **Open source PDF** reopens the internal page picker so additional pages/excerpts can be inserted from the same source.

### Structured-section integration

- New media objects inherit the current handwritten section.
- If a large inserted object would collide with the next structured section, the current section grows automatically and downstream content shifts safely.
- Reordering/growing sections already shifts `AttachmentItem` objects along with handwriting.
- Collapsed sections hide their associated image/PDF objects as well as ink.

### Persistence

No database schema migration is required: media-object metadata is serialized inside the existing `canvas_json` payload. The new fields have defaults for backward compatibility with Milestone 4 data.

Actual imported files are stored under:

`filesDir/journal_attachments/<note-id>/`

The later full-backup milestone must include this directory together with SQLite data.

## Tablet test sequence

1. Create/open a Topic and its Master Summary.
2. Insert an image from Files/Gallery.
3. Write on top of it and close/reopen the app; verify image and ink persist.
4. Open **Objects**, rotate the image, reduce opacity, crop it, resize it and add a caption.
5. Use Lasso to select and move it; lock it and verify it no longer moves.
6. Take a photo with the system camera and verify it appears in the note.
7. Insert a multi-page PDF, navigate pages, insert a full page and then a cropped excerpt from another page.
8. Handwrite over the PDF page/excerpt and verify annotations remain independent of the source PDF.
9. Reopen the source PDF from the object editor and insert another page.
10. Reorder/grow structured sections and verify images/PDF objects move with their sections.
11. Force-close/reopen and verify object geometry, crop, opacity, rotation, lock state and captions persist.

## Validation performed in this environment

- `AttachmentCanvasOps` was compiled with a model stub and smoke-tested for add, growth, resize, crop normalization and object sanitization.
- Kotlin parser checks found no syntax-level errors in the modified Compose/editor/ink source files.

A full Android build and camera/PDF hardware test still requires Android Studio/SDK and a physical or emulated Android 10+ device.
