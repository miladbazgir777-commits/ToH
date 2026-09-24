# Tome of Healing

**Tome of Healing** is a tablet-first Android medical handwriting journal with a fantasy-modern visual identity. It is designed around stylus input, hierarchical medical knowledge, offline-first storage, structured handwritten sections, media/PDF annotation, version history, and full-journal backup/restore.

Current release-candidate milestone: **0.10.0-m10** (`versionCode 11`).

## Core features

- Field → Topic/Subtopic → Note hierarchy with unlimited nesting
- Automatic Master Summaries and reusable medical templates
- Typed searchable titles, pinning, sorting, created/modified dates
- Local SQLite persistence
- Stylus-first infinite vertical vector handwriting
- Pen, fountain pen, ballpoint, pencil, marker, highlighter, eraser, lasso, ruler and shapes
- Pressure sensitivity, palm rejection, finger scrolling and pinch zoom
- Structured collapsible/reorderable medical sections
- Images, camera photos and PDF page/crop annotation
- Typed text, labels, medication blocks, tables and checklists
- Web links and internal journal links
- Layered inherited emblems and fantasy-modern theme system
- Last 10 note versions
- Persistent Trash and deletion locks
- Manual full-journal `.tohbackup` export/import
- Replace/Merge with Keep both / Replace existing / Keep newest
- Five rotating timestamped backups
- Large-note spatial indexing and stroke compaction
- Local crash recovery and Health diagnostics

## Visual identity

The default **Tome of Healing** identity uses an open tome, Rod of Asclepius, arcane rune halo and silver filigree with **Deep Purple / Moon Silver / Deep Blue** styling. The UI uses fantasy-inspired headers and frames while keeping the handwriting workspace clean and readable.

## Build requirements

- Android Studio with Android SDK 35, or GitHub Actions
- Android 10+ device (`minSdk 29`)
- JDK 17
- Gradle 8.11.1
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21 / Jetpack Compose

### Local build

If Gradle is installed:

```bash
gradle assembleDebug
```

The debug APK will be generated under:

```text
app/build/outputs/apk/debug/
```

For a signed direct-install release APK, copy `keystore.properties.example` to `keystore.properties`, point it at your private `.jks`, and use Android Studio's **Generate Signed App Bundle / APK** flow or the included `build_release.sh`.

> Never commit `keystore.properties`, `.jks`, `.keystore`, passwords, or other signing secrets. They are excluded by `.gitignore`.

## GitHub Actions

Two workflows are included:

- **Android CI** — runs unit tests, Android lint, builds a debug APK on pushes/PRs to `main`, and uploads the APK as a GitHub Actions artifact.
- **Build APK manually** — can be started from **Actions → Build APK manually → Run workflow** and uploads an installable debug APK artifact.

This allows the project to be compiled in GitHub even if your local computer does not yet have an Android SDK configured.

## Project documentation

- `Tome_of_Healing_App_Spec.md` — definitive product specification
- `MILESTONE_1.md` through `MILESTONE_10.md` — implementation history
- `MILESTONE_10.md` — current hardening and tablet regression plan

## Validation status

The pure Kotlin performance/index layer and repository logic were smoke-tested during development, and XML/resources were sanity-checked. The next validation stage is a full Android/Compose build through GitHub Actions followed by physical-tablet and stylus testing.

## Repository status

This repository is currently a release-candidate development project. A production `v1.0.0` tag should be created only after the GitHub Actions build is green and physical tablet regression testing passes.
