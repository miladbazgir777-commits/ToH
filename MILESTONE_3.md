# Milestone 3 — Core Handwriting Engine

Status: source implementation complete.

## Goal

Replace the early Compose drawing prototype with a tablet-oriented handwriting engine that behaves like the selected Tome of Healing design: fixed-width/infinite vertical paper, stylus-only ink, finger navigation, vector storage, pressure, erasing, lasso movement, undo/redo and a minimal edge toolbar.

## Implemented

### Stylus/vector engine

- Dedicated `InkSurfaceView` backed by Android `MotionEvent` input
- Stylus and eraser tool-type detection
- Vector strokes stored as point + pressure data
- Pressure-sensitive segment rendering
- Historical/coalesced MotionEvent points captured during fast strokes
- Persistent color, width, opacity and tool type per stroke
- Low-opacity highlighter strokes
- Whole-stroke eraser with geometric segment hit testing
- S Pen / generic stylus primary-button temporary eraser where Android exposes the button state
- The selected **double-tap → previous tool** shortcut remains a device/manufacturer integration target; this milestone does not emulate it with tip taps, so single-tap dots/decimal points remain reliable

### Navigation and palm handling

- Finger drawing disabled
- One-finger vertical scroll
- Limited horizontal pan only when zoomed beyond fit-width
- Two-finger pinch zoom
- Pinch focus remains anchored while scale changes
- Finger/palm input ignored while a stylus gesture is active
- Page auto-expands vertically in 1200-unit chunks near the bottom
- Fixed document width retained at all zoom levels

### Lasso

- Lasso selection of vector strokes
- Selected strokes are visually highlighted
- Drag inside a selection to move the selected strokes as a group
- Lasso moves commit as one undoable edit
- Selection is cleared when leaving the lasso tool

### Undo/redo and autosave

- 50-step in-editor undo stack
- 50-step redo behavior (bounded indirectly by edit history)
- Each completed stroke, erase gesture or lasso move is one history step
- Pause-based autosave remains ~900 ms
- Routine autosave is dispatched to `Dispatchers.IO`
- SQLite handwriting save now updates only the current note canvas + owning Topic timestamp instead of rewriting the complete journal snapshot
- A final save still occurs when leaving the editor

### Rendering performance

- Per-stroke bounding boxes are cached
- Offscreen strokes are culled before drawing
- Ink data is not pushed through Compose during an active stroke; Compose updates only after the gesture commits
- Page-height-only expansion updates do not pollute the undo stack

### Toolbar

- Edge-docked toolbar, hidden/collapsed by default
- Dock choices: left / right / bottom
- Dock preference persists
- Tool visibility persists
- Tool order is customizable and persists
- Pen / fountain pen / ballpoint / pencil / marker / highlighter / ruler line / rectangle shape / eraser / lasso
- Thickness cycling
- Medical preset colors
- Custom ARGB color entry
- Up to 8 saved custom favorite colors
- Undo / redo controls
- Auto-collapse after 5 seconds

## Data model change

`CanvasDocument` now includes a persisted `heightDp` with a default of `2400f`.

Because Kotlin serialization uses defaults, Milestone 2 canvas JSON without this property remains readable and receives the default height automatically. The SQLite table schema itself does not change, so the database version remains 1.

## Important device test

Use a physical Android tablet with its real stylus when possible.

1. Open a Master Summary.
2. Write slowly and quickly; verify pressure variation is visible.
3. Rest the palm on the display while writing; confirm no finger ink appears.
4. Scroll with one finger.
5. Pinch with two fingers and verify zoom remains centered around the pinch focus.
6. Scroll near the bottom repeatedly and confirm the page keeps expanding.
7. Select the highlighter and draw across pen strokes.
8. Select the eraser and scrub across several strokes; whole touched strokes should disappear.
9. Select Lasso, circle several strokes, then drag inside the selected area to move them.
10. Undo and redo each operation.
11. Hold the stylus button while writing to test temporary eraser support on hardware that exposes `BUTTON_STYLUS_PRIMARY`.
12. Verify single stylus taps create dots/decimal points rather than being swallowed as gesture commands.
13. Force-stop and reopen the app; committed handwriting must persist.

## Not validated in this execution environment

The container does not provide an Android SDK/Gradle installation or physical stylus hardware, so the Android app cannot be compiled or latency-tested here. The source has been reviewed for API/data-flow compatibility with the Milestone 2 project.

## Next milestone

Implement the structured handwritten-note layer: true section canvas regions, collapse/expand, reordering, section navigation from both the top-bar button and edge swipe, and direct jumps to section anchors.
