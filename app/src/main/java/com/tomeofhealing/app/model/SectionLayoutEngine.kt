package com.tomeofhealing.app.model

/** Pure transformations for structured handwritten note zones. */
object SectionLayoutEngine {
    const val SECTION_SPACING_DP = 520f
    const val GROWTH_CHUNK_DP = 420f

    data class LayoutResult(
        val sections: List<NoteSection>,
        val canvas: CanvasDocument
    )

    fun normalizedSections(sections: List<NoteSection>): List<NoteSection> =
        sections.sortedWith(compareBy<NoteSection> { it.verticalAnchor }.thenBy { it.orderIndex })
            .mapIndexed { index, section -> section.copy(orderIndex = index) }

    fun sectionAtY(sections: List<NoteSection>, y: Float): NoteSection? {
        val ordered = normalizedSections(sections)
        if (ordered.isEmpty()) return null
        return ordered.lastOrNull { y >= it.verticalAnchor } ?: ordered.first()
    }

    fun ensureItemOwnership(canvas: CanvasDocument, sections: List<NoteSection>): CanvasDocument {
        if (sections.isEmpty()) return canvas
        val nextItems = canvas.items.map { item ->
            if (item.sectionId != null) item else withSectionId(item, sectionAtY(sections, representativeY(item))?.id)
        }
        return if (nextItems == canvas.items) canvas else canvas.copy(items = nextItems)
    }

    /** Insert vertical writing space after one section and shift all later zones/content downward. */
    fun growSection(
        sections: List<NoteSection>,
        canvas: CanvasDocument,
        sectionId: String,
        delta: Float = GROWTH_CHUNK_DP
    ): LayoutResult {
        if (delta <= 0f) return LayoutResult(normalizedSections(sections), canvas)
        val ordered = normalizedSections(sections)
        val index = ordered.indexOfFirst { it.id == sectionId }
        if (index < 0 || index == ordered.lastIndex) {
            return LayoutResult(ordered, canvas.copy(heightDp = canvas.heightDp + delta))
        }
        val boundary = ordered[index + 1].verticalAnchor
        val owned = ensureItemOwnership(canvas, ordered)
        val shiftedItems = owned.items.map { item ->
            val ownerIndex = ordered.indexOfFirst { it.id == item.sectionId }
            if (ownerIndex > index || (ownerIndex < 0 && representativeY(item) >= boundary)) shiftItem(item, delta) else item
        }
        val shiftedSections = ordered.mapIndexed { i, section ->
            if (i > index) section.copy(verticalAnchor = section.verticalAnchor + delta) else section
        }
        return LayoutResult(
            shiftedSections.mapIndexed { i, s -> s.copy(orderIndex = i) },
            owned.copy(heightDp = canvas.heightDp + delta, items = shiftedItems)
        )
    }

    /** Swap a section with its adjacent neighbor and move the handwriting zones with it. */
    fun moveSection(
        sections: List<NoteSection>,
        canvas: CanvasDocument,
        sectionId: String,
        direction: Int
    ): LayoutResult {
        val ordered = normalizedSections(sections)
        val from = ordered.indexOfFirst { it.id == sectionId }
        val to = from + direction
        if (from !in ordered.indices || to !in ordered.indices || kotlin.math.abs(direction) != 1) {
            return LayoutResult(ordered, canvas)
        }

        val upperIndex = minOf(from, to)
        val lowerIndex = maxOf(from, to)
        val upper = ordered[upperIndex]
        val lower = ordered[lowerIndex]
        val start = upper.verticalAnchor
        val middle = lower.verticalAnchor
        val end = if (lowerIndex < ordered.lastIndex) ordered[lowerIndex + 1].verticalAnchor else canvas.heightDp
        val upperHeight = (middle - start).coerceAtLeast(80f)
        val lowerHeight = (end - middle).coerceAtLeast(80f)
        val owned = ensureItemOwnership(canvas, ordered)

        val shiftedItems = owned.items.map { item ->
            when (item.sectionId) {
                upper.id -> shiftItem(item, lowerHeight)
                lower.id -> shiftItem(item, -upperHeight)
                else -> item
            }
        }

        val reordered = ordered.toMutableList()
        reordered[upperIndex] = lower.copy(verticalAnchor = start)
        reordered[lowerIndex] = upper.copy(verticalAnchor = start + lowerHeight)
        return LayoutResult(
            reordered.mapIndexed { index, section -> section.copy(orderIndex = index) },
            owned.copy(items = shiftedItems)
        )
    }

    /** Adds a custom zone at the bottom of the current structured note. */
    fun addSection(
        sections: List<NoteSection>,
        canvas: CanvasDocument,
        section: NoteSection
    ): LayoutResult {
        val ordered = normalizedSections(sections)
        val anchor = if (ordered.isEmpty()) 48f else {
            ordered.last().verticalAnchor + SECTION_SPACING_DP
        }
        val next = ordered + section.copy(orderIndex = ordered.size, verticalAnchor = anchor)
        val neededHeight = maxOf(canvas.heightDp, anchor + SECTION_SPACING_DP)
        return LayoutResult(next, canvas.copy(heightDp = neededHeight))
    }

    private fun representativeY(item: CanvasItem): Float = when (item) {
        is StrokeItem -> item.points.ifEmpty { listOf(InkPoint(0f, 0f)) }.map { it.y }.average().toFloat()
        is TextItem -> item.y
        is AttachmentItem -> item.y + item.height * 0.5f
    }

    private fun withSectionId(item: CanvasItem, id: String?): CanvasItem = when (item) {
        is StrokeItem -> item.copy(sectionId = id)
        is TextItem -> item.copy(sectionId = id)
        is AttachmentItem -> item.copy(sectionId = id)
    }

    private fun shiftItem(item: CanvasItem, dy: Float): CanvasItem = when (item) {
        is StrokeItem -> item.copy(points = item.points.map { it.copy(y = it.y + dy) })
        is TextItem -> item.copy(y = item.y + dy)
        is AttachmentItem -> item.copy(y = item.y + dy)
    }
}
