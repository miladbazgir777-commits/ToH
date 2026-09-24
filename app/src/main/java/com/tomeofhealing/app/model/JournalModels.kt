package com.tomeofhealing.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class NodeType { FIELD, TOPIC }

@Serializable
enum class SortMode {
    MANUAL,
    ALPHABETICAL,
    NEWEST_MODIFIED,
    OLDEST_MODIFIED,
    NEWEST_CREATED,
    OLDEST_CREATED
}

@Serializable
enum class NoteTemplate {
    DISEASE,
    DRUG,
    PROCEDURE,
    EMERGENCY,
    ECG,
    RADIOLOGY,
    ANATOMY,
    DIFFERENTIAL_DIAGNOSIS,
    BLANK
}

@Serializable
data class EmblemDefinition(
    val base: String = "open_tome",
    val centralSymbol: String = "rod_of_asclepius",
    val secondarySymbol: String = "arcane_halo",
    val frame: String = "silver_filigree",
    val primaryColor: Long = 0xFF2B114A,
    val secondaryColor: Long = 0xFFC9D0DA,
    val accentColor: Long = 0xFF102B59,
    val glow: Float = 0.22f,
    val banner: String? = null,
    val initials: String? = null
)

@Serializable
data class JournalNode(
    val id: String,
    val parentId: String? = null,
    val type: NodeType,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val pinned: Boolean = false,
    val deletionLocked: Boolean = false,
    val deletedAt: Long? = null,
    /** Sort mode used for this node's child Topics. Root Fields use a repository-level mode. */
    val sortMode: SortMode = SortMode.MANUAL,
    /** Sort mode used for Notes when this node is a Topic. */
    val noteSortMode: SortMode = SortMode.MANUAL,
    val manualOrder: Int = 0,
    val emblem: EmblemDefinition = EmblemDefinition()
)

@Serializable
data class JournalNote(
    val id: String,
    val topicId: String,
    val title: String,
    val isMasterSummary: Boolean,
    val createdAt: Long,
    val modifiedAt: Long,
    val pinned: Boolean = false,
    val deletedAt: Long? = null,
    val paperPreset: String = "aged_parchment",
    val manualOrder: Int = 0,
    val sections: List<NoteSection> = emptyList(),
    val canvas: CanvasDocument = CanvasDocument()
)

@Serializable
data class NoteVersion(
    val id: String,
    val noteId: String,
    val createdAt: Long,
    val sourceModifiedAt: Long,
    val label: String = "Automatic checkpoint",
    val sections: List<NoteSection> = emptyList(),
    val canvas: CanvasDocument = CanvasDocument()
)

/**
 * A handwritten section is a vertical zone on the infinite page.
 * verticalAnchor is the top of the zone in document dp. The next section anchor is the
 * bottom boundary. The final section extends to the canvas bottom.
 */
@Serializable
data class NoteSection(
    val id: String,
    val title: String,
    val orderIndex: Int,
    val collapsed: Boolean = false,
    val verticalAnchor: Float = 0f
)

@Serializable
data class CanvasDocument(
    val widthDp: Float = 900f,
    val heightDp: Float = 2400f,
    val items: List<CanvasItem> = emptyList()
)

@Serializable
sealed interface CanvasItem {
    val id: String
    /** Explicit ownership keeps section moves/collapse deterministic. Older documents may be null. */
    val sectionId: String?
}

@Serializable
data class InkPoint(val x: Float, val y: Float, val pressure: Float = 1f)

@Serializable
data class StrokeItem(
    override val id: String,
    val points: List<InkPoint>,
    val colorArgb: Long,
    val width: Float,
    val opacity: Float = 1f,
    val tool: String = "pen",
    override val sectionId: String? = null
) : CanvasItem

@Serializable
enum class TextObjectKind {
    TEXT,
    LABEL,
    MEDICATION,
    TABLE,
    CHECKLIST,
    LINK
}

@Serializable
data class ChecklistEntry(
    val id: String,
    val text: String,
    val checked: Boolean = false
)

/**
 * Movable typed content object layered into the handwritten canvas.
 *
 * The generic metadata map keeps medication/link payloads extensible without schema churn:
 * medication keys: name, dose, route, frequency, duration, note
 * link keys: target, destinationLabel
 */
@Serializable
data class TextItem(
    override val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val text: String,
    override val sectionId: String? = null,
    val height: Float = 120f,
    val kind: TextObjectKind = TextObjectKind.TEXT,
    val fontSizeSp: Float = 18f,
    val textColorArgb: Long = 0xFF2A2431,
    val backgroundColorArgb: Long = 0xFFF6F0E7,
    val borderColorArgb: Long = 0xFF8A78A5,
    val locked: Boolean = false,
    val zIndex: Int = 0,
    val metadata: Map<String, String> = emptyMap(),
    val tableRows: List<List<String>> = emptyList(),
    val checklist: List<ChecklistEntry> = emptyList()
) : CanvasItem

@Serializable
data class AttachmentItem(
    override val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** App-private source path or durable URI. */
    val uri: String,
    /** image | pdf_page */
    val kind: String,
    override val sectionId: String? = null,
    /** Visual/object state is persisted with the canvas and remains fully editable. */
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
    val locked: Boolean = false,
    val zIndex: Int = 0,
    /** Normalized source crop rectangle. */
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    /** Zero-based page when kind == pdf_page. */
    val sourcePage: Int? = null,
    val caption: String? = null,
    val originalName: String? = null
) : CanvasItem
