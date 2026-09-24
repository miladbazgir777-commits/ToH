# Tome of Healing — Definitive Product Specification

## Product identity

**Name:** Tome of Healing  
**Platform:** Android tablet APK, sideloaded directly  
**Primary interaction:** stylus-first handwritten medical knowledge journal  
**Visual direction:** fantasy-modern hybrid inspired by classic high-fantasy RTS interfaces, without copying protected game assets  
**Data model:** local-first, fully offline, with manual backup/export and optional manual cloud-folder backup through Android's Storage Access Framework

## Information architecture

The journal uses a hybrid hierarchy:

**Field → Topic → optional Subtopic(s) → Notes**

Topics can contain further topics at any depth when needed. The default use stays simple, but the data model supports nesting.

Every Topic automatically creates a **Master Summary**. A Topic can also contain unlimited secondary notes. The Master Summary is always pinned above ordinary notes.

Fields, Topics/Subtopics, and Notes can all be pinned. Pinned items remain inside their original hierarchy and appear at the top of the relevant screen.

No Archive state is used. Deleted material goes to Trash.

## Home and navigation

The application opens with a 1–2 second crest intro, then goes directly to the **Field Library**.

Navigation is full-screen drill-down rather than multi-pane:

**Field Library → Topic/Subtopic list → Note list → Note canvas**

The Back action moves one level up. While inside a note, the top bar shows a compact breadcrumb, for example:

**Neurology › Headache › Migraine › Master Summary**

Each breadcrumb level is tappable.

## Library presentation

The Field Library and hierarchy screens support switchable layouts:

- Grid
- List
- Tome/card
- RTS command-panel

The chosen layout can be saved as the default.

Field/Topic cards display the emblem, title, pin state, and **Last modified** date. Full item details show both **Created** and **Last modified**.

Topic opening screen goes directly to the notes list. There is no separate decorative cover page.

The note list uses medium density and displays:

- Topic emblem
- Note title
- Last modified date
- Pin status

No handwriting thumbnail is shown.

## Sorting and actions

Every hierarchy level supports either manual drag ordering or automatic ordering by:

- Alphabetical
- Newest modified
- Oldest modified
- Newest created
- Oldest created

The chosen mode is remembered independently per parent location.

Long-press context menu for Fields/Topics:

- Open
- Rename
- Edit emblem
- Move
- Duplicate
- Pin / unpin
- Delete

After Delete, Move, Rename, Duplicate, or Reorder, a temporary **Undo** snackbar appears.

## Emblem system

Every Field and Topic has a layered emblem.

Subtopics are **parent-derived**: creation starts from the parent emblem and the user modifies selected layers.

Emblem layers include:

- Base shape
- Central symbol
- Secondary symbol
- Border/frame
- Banner/ribbon
- Background texture
- Primary/secondary/accent colors
- Glow/aura
- Optional initials/short text

Emblems can be saved as reusable presets.

### Main Tome of Healing crest

- Base: open tome
- Central symbol: Rod of Asclepius
- Secondary motif: arcane rune circle / halo
- Frame: ornate silver filigree
- Palette: Deep Purple / Moon Silver / Deep Blue
- Animation: subtle ambient glow, rune shimmer, silver highlight sweep, gentle halo pulse

## Theme system

Visual style is a **fantasy-modern hybrid**: immersive shell, highly readable writing surface.

The app ships with polished theme presets and an advanced editor for:

- Background texture/image
- Panel material (stone, wood, metal, parchment)
- Accent colors
- Border style
- Button style
- Glow intensity
- Display/header typography
- Body/UI typography
- Note-paper appearance
- Animation intensity

Custom themes can be saved as presets.

Typography uses **high-fantasy decorative headers** and a **clean medical UI body font**.

Transitions are subtle: panel/page slides, brief emblem illumination, restrained rune shimmer. Writing mode avoids decorative motion.

## Topic templates

Every new Topic automatically gets a Master Summary with starter sections. The user can choose a preset template or a custom reusable template.

Built-in template families:

- Disease
- Drug
- Procedure
- Emergency
- ECG
- Radiology
- Anatomy
- Differential diagnosis
- Blank

Example Disease sections:

- Overview
- Clinical Features
- Differential Diagnosis
- Investigations
- Treatment
- Drug Doses
- Red Flags
- Pearls

Example Drug sections:

- Mechanism
- Indications
- Dose
- Contraindications
- Adverse Effects
- Interactions
- Special Populations
- Pearls

Example Emergency sections:

- Presentation
- Immediate Assessment
- Red Flags
- Investigations
- Treatment
- Disposition

## Handwritten note model

The note surface is a **fixed-width infinite vertical page**.

It is not page-based and does not expand horizontally. The user scrolls vertically and uses ordinary two-finger pinch zoom.

Primary ink is stored as **vector strokes**, preserving:

- Point path
- Pressure
- Width
- Tool type
- Color
- Opacity

This allows lossless zoom, lasso selection, movement, scaling, and stroke erasure.

The app is handwriting-first, but the canvas can also contain movable typed text boxes, captions, small tables, medication/dose blocks, links, images, and PDF excerpts.

## Stylus and touch behavior

- Stylus: draws/writes
- One finger: vertical navigation
- Two fingers: pinch zoom
- Finger drawing: disabled
- Palm contacts: rejected while the stylus is active

Default stylus shortcuts, where hardware exposes them:

- Hold stylus button → eraser
- Double tap → previous tool

When a manufacturer does not expose a shortcut, the on-screen toolbar remains the fallback.

The app uses universal Android stylus APIs as the baseline and opportunistically uses device/manufacturer capabilities such as pressure, hover, button events, and improved palm rejection.

## Handwriting toolbar

Advanced tools:

- Pen
- Fountain pen
- Ballpoint
- Pencil
- Marker
- Highlighter
- Eraser
- Lasso
- Shape tool
- Straight-line/ruler tool
- Undo/redo
- Thickness control
- Pressure sensitivity
- Color presets
- Custom color picker
- Saved favorite colors

The toolbar is customizable and reorderable.

In writing mode it is **edge-docked and hidden by default**. It can dock left, right, or bottom and remembers the user's location.

The overall writing UI is minimal: nearly full-screen canvas, thin top bar, breadcrumb, save state, and transient tool/navigation controls.

## Structured handwritten sections

Sections are true canvas regions, not text form fields.

Each section can be:

- Collapsed / expanded
- Renamed
- Reordered
- Navigated to directly

The section navigator is available by both a top-bar button and an inward edge swipe.

Users may still place freeform writing between sections.

## Paper/background system

Available paper families:

**Basic:** blank, ruled, grid, dotted  
**Medical:** ECG grid, SOAP-style, prescription-style, anatomy sketch, clinical checklist  
**Fantasy:** aged parchment, dark parchment, arcane manuscript, stone tablet, leather-bound page, elven manuscript

Adjustable properties:

- Line/grid spacing
- Background opacity
- Paper tint
- Texture strength
- Decorative margins

## Images and reference objects

Image objects can be:

- Imported from gallery/camera/file picker
- Moved and resized
- Cropped
- Rotated
- Locked
- Opacity-adjusted
- Layered front/back
- Captioned
- Duplicated
- Zoomed
- Written over

This is intended for ECGs, radiographs, dermatology photographs, diagrams, and reference screenshots.

## PDF support

PDFs can be attached to a Topic/Note and annotated.

Capabilities:

- Handwrite/highlight on PDF pages
- Bookmark pages
- Insert blank annotation space
- Extract one or more pages into a handwritten note
- Crop a region of a PDF page and place it in the note
- Annotate the extracted object
- Tap an excerpt to reopen the original attached PDF

The original PDF remains preserved.

## Links

Canvas objects can contain:

- Web links
- Links to Fields
- Links to Topics/Subtopics
- Links to specific Notes

Internal links navigate directly inside Tome of Healing while preserving Back navigation.

## Search

Search is intentionally metadata-focused because handwritten content is not OCR-dependent.

Search indexes:

- Field names
- Topic/Subtopic names
- Note titles

No mandatory handwriting recognition or full-text OCR is part of v1.

## Autosave and history

Autosave is **pause-based**:

- Save after a brief pause in writing
- Save on leaving the note

Visible status: Saving… / Saved.

Each note retains the **last 10 versions**. A History view can restore an older version.

Created and Last Modified timestamps are maintained for Fields, Topics, and Notes.

## Delete and Trash

Delete sends content to Trash.

- Trash never auto-expires
- Restore is available
- Permanent deletion requires confirmation
- Empty Trash is manual
- Deleting a parent warns that all nested children are included
- Important Fields/Topics can be locked against deletion

## Backup / restore

A full-app backup is a single restore package containing:

- Journal hierarchy
- Notes
- Vector ink
- Section structure
- Images and attachments
- PDFs and PDF metadata
- Emblems and emblem layers
- Themes and presets
- Version history
- Preferences

Backup is manual only through **Back Up Now**.

On first use, the user selects a destination through Android's Storage Access Framework. The app remembers that folder.

Backups are timestamped and rotated. The newest **5** are retained; the oldest is deleted when a sixth backup is successfully written.

Backup encryption is **off**.

Import always asks whether to:

- Replace current journal
- Merge with current journal

On duplicate conflicts:

- Keep both
- Replace existing
- Keep newest

## Security

No app lock, PIN, biometrics, or authentication gate is required.

## Language and orientation

- UI language: English only
- Tablet orientation: portrait and landscape
- Layout adapts automatically to orientation

## Android technical architecture

Recommended implementation stack:

- Kotlin
- Jetpack Compose
- Material 3 as the accessibility/layout substrate, visually replaced by the custom fantasy theme
- Room/SQLite for metadata and journal structure
- Kotlin serialization for vector-canvas payloads and emblem/theme documents
- Android Storage Access Framework for backups/imports
- Android PdfRenderer for PDF page rendering, plus persistent annotation metadata
- Custom Compose Canvas / low-level pointer input for vector ink
- Coroutines + Flow for autosave and reactive UI
- Navigation Compose for drill-down navigation

Minimum Android version: **Android 10 / API 29**.

The architecture should separate:

**UI layer → ViewModels → Repository → Room + attachment storage**

Large binary assets (images/PDFs) should be stored as app-private files with database references rather than as database BLOBs.

## Core database model

### JournalNode

Represents Field, Topic, or Subtopic.

Key properties: id, parentId, nodeType, title, createdAt, modifiedAt, pinned, deletionLock, deletedAt, sortMode, manualOrder, emblemDefinition.

### Note

Key properties: id, topicId, title, isMasterSummary, createdAt, modifiedAt, pinned, deletedAt, paperDefinition, canvasDocument.

### NoteSection

Key properties: id, noteId, title, orderIndex, collapsed, verticalAnchor.

### NoteVersion

Key properties: id, noteId, createdAt, serializedCanvasSnapshot. Retention: newest 10 versions.

### Attachment

Key properties: id, noteId/topicId, kind, internalPath, originalName, mimeType, createdAt, sourceMetadata.

### ThemePreset

Contains colors, textures, typography style, ornament style, animation intensity, and paper defaults.

### EmblemDefinition

Serialized layered emblem document containing base, symbols, frame, texture, colors, aura, banner, and text.

## Performance requirements

The writing surface must prioritize low-latency pen input. UI updates that are not needed during a stroke should be deferred until the stroke ends. Vector content should be spatially indexed or chunked vertically so very long notes do not require redrawing every stroke on every frame.

Images/PDF excerpts should use downsampled display caches while preserving the original source file.

Autosave must never block pen rendering.

## Initial implementation milestones

**Milestone 1 — Journal shell**  
Field Library, hierarchy, Topic note list, pinning, sorting, timestamps, Trash, basic themes.

**Milestone 2 — Ink engine**  
Infinite fixed-width canvas, vector strokes, pressure, eraser, lasso, undo/redo, pinch zoom, finger-scroll/stylus-write separation.

**Milestone 3 — Structured notes**  
Master Summary, custom templates, collapsible/reorderable sections, section navigator, breadcrumbs.

**Milestone 4 — Media**  
Images, layers, typed text objects, internal/web links, PDF attachments, PDF excerpts/annotation.

**Milestone 5 — Customization**  
Layered emblem builder, inherited emblems, theme editor, paper presets, crest intro and subtle transitions.

**Milestone 6 — Reliability**  
Pause autosave, 10-version history, full backup/import, merge conflict handling, five-file rotation.

**Milestone 7 — Tablet polish**  
Portrait/landscape adaptation, manufacturer stylus enhancements, performance profiling, crash recovery, direct signed APK build.

## Acceptance criteria for v1

A v1 build is acceptable when a user can create a Field, create nested Topics, automatically receive a Master Summary from a selected template, handwrite with a stylus on an infinite vertical vector canvas, add sections/images/PDF excerpts, navigate with breadcrumbs, recover older note versions, delete/restore items through Trash, customize emblems/themes, and successfully back up and restore the entire journal from a single package.
