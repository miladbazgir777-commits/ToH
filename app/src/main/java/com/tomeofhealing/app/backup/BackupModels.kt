package com.tomeofhealing.app.backup

import com.tomeofhealing.app.model.JournalNode
import com.tomeofhealing.app.model.JournalNote
import com.tomeofhealing.app.model.NoteVersion
import com.tomeofhealing.app.model.SortMode
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersion: String,
    val createdAt: Long,
    val fieldCount: Int,
    val topicCount: Int,
    val noteCount: Int,
    val versionCount: Int,
    val attachmentFileCount: Int
)

@Serializable
data class BackupVisualSettings(
    val themeName: String,
    val primary: Long,
    val secondary: Long,
    val accent: Long,
    val background: Long,
    val surface: Long,
    val parchment: Long,
    val ink: Long,
    val material: String,
    val textureStrength: Float,
    val glowStrength: Float,
    val animationScale: Float,
    val libraryLayout: String,
    val toolbarDock: String? = null,
    val toolbarToolOrder: String? = null,
    val toolbarLegacyVisibleTools: List<String> = emptyList(),
    val toolbarFavoriteColors: String? = null
)

@Serializable
data class BackupPayload(
    val formatVersion: Int = 1,
    val createdAt: Long,
    val rootSortMode: SortMode,
    val nodes: List<JournalNode>,
    val notes: List<JournalNote>,
    val versions: List<NoteVersion>,
    val visual: BackupVisualSettings
)

enum class ImportMode { REPLACE, MERGE }
enum class ConflictPolicy { KEEP_BOTH, REPLACE_EXISTING, KEEP_NEWEST }

data class BackupInspection(
    val uriText: String,
    val manifest: BackupManifest
)

data class BackupResult(
    val fileName: String,
    val bytesWritten: Long,
    val deletedOldBackups: Int
)

data class ImportResult(
    val importedNodes: Int,
    val importedNotes: Int,
    val importedVersions: Int,
    val copiedAttachmentFiles: Int,
    val remappedConflicts: Int,
    val appearanceRestored: Boolean
)
