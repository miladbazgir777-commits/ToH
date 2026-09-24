# Milestone 10 — Production Hardening & Release Candidate

Version: **0.10.0-m10** (`versionCode 11`)

This milestone does not add another major journal feature. It hardens the Milestone 9 feature set for long handwritten notes, unexpected process death, stylus-heavy tablet use, and release builds.

## 1. Large-note performance

### Vertical-band canvas index
`CanvasSpatialIndex` indexes strokes, media and typed objects in document-coordinate vertical bands. The handwriting renderer and eraser now query the visible/nearby bands instead of scanning all strokes on every frame.

This is specifically suited to Tome of Healing's fixed-width, vertically growing note canvas.

### Stylus sample compaction
`StrokePointReducer` removes redundant high-frequency stylus samples at stroke commit while preserving endpoints, geometry and significant pressure changes. Straight/simple strokes can shrink substantially while curved/pressure-varying strokes retain more points.

Benefits:
- smaller note JSON payloads,
- faster SQLite autosave,
- less memory pressure,
- faster redraw and history snapshots.

### Media cache pressure handling
Decoded image/PDF bitmaps remain in an LRU cache, but active ink surfaces are now registered weakly. Android memory-pressure callbacks can evict all decoded reference media without touching vector handwriting or source files.

## 2. Stylus/tablet hardening

- Runtime detection of generic stylus devices and Samsung hardware.
- Existing stylus-only ink / finger-navigation separation remains intact.
- Barrel-button / eraser-tool temporary eraser support remains intact.
- Added stylus hover cursor/tool-size preview where the Android input stack exposes hover events.
- Generic Android APIs remain the baseline; there is no mandatory Samsung SDK dependency.

## 3. Local crash recovery

`CrashRecoveryManager` is entirely local and does not transmit telemetry.

It now:
- marks foreground editing sessions,
- detects a previous foreground session that did not close normally,
- records the latest uncaught exception stack locally,
- shows a one-time recovery notice on the next launch,
- keeps normal note autosave/checkpoint behavior unchanged.

A normal `onStop` performs a repository checkpoint and marks the session clean.

## 4. Health / diagnostics screen

The Field Library now exposes **Health**. It shows:
- app/version information,
- Android/device/stylus mode,
- Field/Topic, Note and history counts,
- database size,
- attachment storage size,
- manual SQLite `PRAGMA quick_check(1)`,
- local crash-report preview and clear action.

No diagnostic information is uploaded.

## 5. SQLite reliability

- Write-ahead logging (WAL) enabled.
- `synchronous=NORMAL` used to reduce handwriting-write stalls while retaining WAL crash-safety characteristics.
- Manual quick-check exposed through Health.
- Existing logical backup/restore remains independent of the physical database file.

## 6. Release build preparation

The Android module now contains:
- explicit Java/Kotlin 17 compatibility,
- release build type,
- lint-on-release configuration,
- ProGuard/R8 rules prepared for later shrinking,
- optional release signing from `keystore.properties`,
- `keystore.properties.example`,
- `build_release.sh`,
- Android manifest hardening (`allowBackup=false`, cleartext disabled),
- Gradle JVM/caching settings.

The release build intentionally keeps minification disabled for this first release candidate. R8 can be enabled after physical-device regression testing.

## 7. Added JVM regression tests

`CanvasPerformanceTest` verifies:
- stylus point compaction preserves stroke endpoints,
- the spatial index returns only the appropriate vertical canvas objects.

The pure Kotlin performance layer was also smoke-compiled in the project-generation environment.

## Validation performed here

Passed:
- pure Kotlin compile/test of `StrokePointReducer` and `CanvasSpatialIndex`,
- XML parse checks,
- source/ZIP integrity checks.

Not possible in this environment:
- Android Gradle Plugin sync,
- Compose/Android compilation,
- emulator execution,
- real S Pen latency/hover testing,
- signed APK generation.

The environment contains a JDK and Kotlin compiler but no Android SDK and no installed Gradle distribution. Therefore the next physical step is to open this project in Android Studio, sync it, run the tests, install the debug build on the target tablet, and then configure a release keystore for `assembleRelease`.

## Tablet regression sequence

Before treating the build as release-ready, test at least:

1. Upgrade over the Milestone 9 install without clearing app data.
2. Open a note with several thousand strokes and scroll/zoom rapidly.
3. Write continuously for 10–15 minutes; verify no visible stroke deformation from point reduction.
4. Test pen pressure, palm rejection, hover and barrel-button eraser.
5. Import several large photos and multi-page PDFs; scroll until caches churn and verify they reload.
6. Background/foreground during active writing and confirm data persists.
7. Force-stop after an autosave, reopen and verify note persistence.
8. Trigger **Health → Run database check** and confirm `ok`.
9. Create a full backup, uninstall/reinstall a test build, and restore it.
10. Exercise Trash/history restore after the import.
11. Run portrait/landscape rotation through Field, Topic and Note screens.
12. Run `lintRelease` and `testReleaseUnitTest` before signing.

## Release signing

Copy:

`keystore.properties.example` → `keystore.properties`

Then supply your private `.jks` path/password/alias. Never include the real keystore or passwords in a shared ZIP.

When Gradle is available:

```bash
./build_release.sh
```

or in Android Studio use **Build → Generate Signed App Bundle / APK → APK**.
