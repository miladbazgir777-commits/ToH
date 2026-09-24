package com.tomeofhealing.app.content

import com.tomeofhealing.app.model.CanvasDocument
import com.tomeofhealing.app.model.ChecklistEntry
import com.tomeofhealing.app.model.TextItem
import com.tomeofhealing.app.model.TextObjectKind
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Pure transforms for typed/structured canvas objects. */
object ContentCanvasOps {
    fun items(document: CanvasDocument): List<TextItem> =
        document.items.filterIsInstance<TextItem>().sortedWith(compareBy<TextItem> { it.zIndex }.thenBy { it.id })

    fun nextZIndex(document: CanvasDocument): Int =
        document.items.filterIsInstance<TextItem>().maxOfOrNull { it.zIndex }?.plus(1) ?: 0

    fun addText(
        document: CanvasDocument,
        x: Float,
        y: Float,
        width: Float,
        text: String,
        sectionId: String?,
        kind: TextObjectKind = TextObjectKind.TEXT,
        metadata: Map<String, String> = emptyMap(),
        tableRows: List<List<String>> = emptyList(),
        checklist: List<ChecklistEntry> = emptyList()
    ): CanvasDocument {
        val height = defaultHeight(kind, text, tableRows, checklist)
        val item = TextItem(
            id = UUID.randomUUID().toString(),
            x = x,
            y = y,
            width = width,
            height = height,
            text = text,
            sectionId = sectionId,
            kind = kind,
            zIndex = nextZIndex(document),
            metadata = metadata,
            tableRows = tableRows,
            checklist = checklist,
            fontSizeSp = if (kind == TextObjectKind.LABEL) 16f else 18f,
            backgroundColorArgb = when (kind) {
                TextObjectKind.LABEL -> 0xFFEAE3F4
                TextObjectKind.MEDICATION -> 0xFFE8EEF8
                TextObjectKind.TABLE -> 0xFFF7F3ED
                TextObjectKind.CHECKLIST -> 0xFFF3F0E9
                TextObjectKind.LINK -> 0xFFE9EFF9
                else -> 0xFFF6F0E7
            }
        )
        val safe = sanitize(item, document.widthDp)
        return document.copy(
            heightDp = max(document.heightDp, safe.y + safe.height + 240f),
            items = document.items + safe
        )
    }

    fun update(document: CanvasDocument, id: String, transform: (TextItem) -> TextItem): CanvasDocument =
        document.copy(items = document.items.map { item ->
            if (item is TextItem && item.id == id) sanitize(transform(item), document.widthDp) else item
        })

    fun remove(document: CanvasDocument, id: String): CanvasDocument =
        document.copy(items = document.items.filterNot { it.id == id })

    fun scale(document: CanvasDocument, id: String, factor: Float): CanvasDocument = update(document, id) {
        it.copy(
            width = (it.width * factor).coerceIn(160f, document.widthDp),
            height = (it.height * factor).coerceIn(72f, 1200f),
            fontSizeSp = (it.fontSizeSp * factor).coerceIn(11f, 42f)
        )
    }

    fun bringForward(document: CanvasDocument, id: String): CanvasDocument = update(document, id) {
        it.copy(zIndex = nextZIndex(document))
    }

    fun sendBackward(document: CanvasDocument, id: String): CanvasDocument {
        val minZ = document.items.filterIsInstance<TextItem>().minOfOrNull { it.zIndex } ?: 0
        return update(document, id) { it.copy(zIndex = minZ - 1) }
    }

    fun toggleChecklistEntry(document: CanvasDocument, itemId: String, entryId: String): CanvasDocument =
        update(document, itemId) { item ->
            item.copy(checklist = item.checklist.map { entry ->
                if (entry.id == entryId) entry.copy(checked = !entry.checked) else entry
            })
        }

    fun medicationText(item: TextItem): String {
        val m = item.metadata
        return buildList {
            m["name"]?.takeIf { it.isNotBlank() }?.let { add(it) }
            val dosing = listOfNotNull(
                m["dose"]?.takeIf { it.isNotBlank() },
                m["route"]?.takeIf { it.isNotBlank() },
                m["frequency"]?.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            if (dosing.isNotBlank()) add(dosing)
            m["duration"]?.takeIf { it.isNotBlank() }?.let { add("Duration: $it") }
            m["note"]?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("\n")
    }

    private fun defaultHeight(
        kind: TextObjectKind,
        text: String,
        tableRows: List<List<String>>,
        checklist: List<ChecklistEntry>
    ): Float = when (kind) {
        TextObjectKind.LABEL -> 72f
        TextObjectKind.MEDICATION -> 190f
        TextObjectKind.TABLE -> max(150f, 54f + tableRows.size * 52f)
        TextObjectKind.CHECKLIST -> max(120f, 48f + checklist.size * 44f)
        TextObjectKind.LINK -> 86f
        TextObjectKind.TEXT -> max(110f, 76f + text.length / 45f * 28f)
    }

    private fun sanitize(item: TextItem, pageWidth: Float): TextItem {
        val width = item.width.coerceIn(120f, pageWidth)
        val height = item.height.coerceIn(56f, 1200f)
        return item.copy(
            x = min(max(0f, item.x), max(0f, pageWidth - width)),
            y = item.y.coerceAtLeast(0f),
            width = width,
            height = height,
            fontSizeSp = item.fontSizeSp.coerceIn(10f, 48f)
        )
    }
}
