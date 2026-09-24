# Milestone 4 — Structured Handwritten Sections

This milestone adds the structured medical-section system to the handwriting editor while keeping handwriting as the primary note format.

## Implemented

### Section-aware handwritten canvas

- Master Summary templates render their medical sections directly on the infinite handwritten page.
- Section headers are drawn as persistent in-canvas anchors.
- New strokes are assigned to the section in which they are written.
- Older Milestone 3 strokes without an explicit section ID are inferred from their vertical position when structural operations are performed.
- Collapsed sections hide their handwritten strokes and block accidental writing/erasing inside the hidden zone.
- Collapse is intentionally non-destructive: the zone keeps its geometric height so expanding it restores the exact original coordinates.

### Section navigator

The handwriting editor now has a **Sections** control in the top bar. The same navigator can be opened by swiping inward from the left edge.

From the navigator you can:

- Tap a section to jump directly to its anchor.
- Collapse / reopen a section.
- Move a section up or down.
- Rename a section.
- Add a custom section.
- Add more vertical writing space to a section.

### Reorder with handwriting

Section reordering moves the section's owned canvas content with it. The transform handles vector strokes now and also supports the persisted typed-text and attachment object types so later milestones can reuse the same layout engine.

### Auto-growth

For non-final sections, writing close to the next section boundary requests another vertical writing chunk. Later sections and their content are shifted downward automatically. The final section continues to use the normal infinite-page growth behavior.

### Unified undo/redo

The editor undo stack now stores a combined snapshot of:

- vector canvas document
- section metadata/layout

This means section move/collapse/rename/space operations participate in the same 50-step undo/redo history as handwriting edits.

### Persistence

SQLite hot-path note persistence now saves both:

- `canvas_json`
- `sections_json`

in one transaction, while also updating the Topic modified timestamp.

No database schema migration is required because both JSON columns already existed in Milestone 2.

## Data-model evolution

Every canvas object now supports an optional `sectionId`.

This is backward-compatible with earlier saved JSON because the field has a default `null` value. The layout engine can infer ownership for older strokes when necessary.

## Physical tablet test sequence

1. Open a Topic Master Summary.
2. Open **Sections** from the top bar and tap `Treatment`; confirm the canvas jumps to that heading.
3. Repeat using the left-edge swipe to open the navigator.
4. Write several lines under `Treatment` and `Drug Doses`.
5. Collapse `Treatment`; verify its handwriting disappears and stylus input in that zone is blocked.
6. Reopen it; verify handwriting returns unchanged.
7. Move `Drug Doses` above `Treatment`; verify its handwriting moves with the section.
8. Tap **+ Space** for a middle section; confirm subsequent sections move downward without losing ink.
9. Write close to the bottom boundary of a middle section; verify it automatically gains more space.
10. Rename a section and add a custom section.
11. Use Undo/Redo across handwriting and section operations.
12. Force-close and reopen the app; verify section names, order, collapse state, anchors and handwriting persist.

## Environment note

The source-level section model/layout engine was compiled with `kotlinc` in this environment, and move/grow transforms were smoke-tested. This environment still has no Android SDK/Gradle installation, so the full Android UI build and physical stylus behavior must be verified in Android Studio/on the target tablet.

App version: **0.4.0-m4** (`versionCode 5`).
