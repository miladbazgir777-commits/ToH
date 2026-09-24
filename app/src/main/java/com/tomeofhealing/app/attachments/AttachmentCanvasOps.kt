package com.tomeofhealing.app.attachments

import com.tomeofhealing.app.model.AttachmentItem
import com.tomeofhealing.app.model.CanvasDocument
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Deterministic canvas-object operations kept separate from Android rendering. */
object AttachmentCanvasOps {
    data class CropRect(
        val left: Float = 0f,
        val top: Float = 0f,
        val right: Float = 1f,
        val bottom: Float = 1f
    ) {
        fun normalized(): CropRect {
            val l = left.coerceIn(0f, 0.94f)
            val t = top.coerceIn(0f, 0.94f)
            val r = right.coerceIn(l + 0.05f, 1f)
            val b = bottom.coerceIn(t + 0.05f, 1f)
            return CropRect(l, t, r, b)
        }
    }

    fun attachments(document: CanvasDocument): List<AttachmentItem> =
        document.items.filterIsInstance<AttachmentItem>().sortedWith(compareBy<AttachmentItem> { it.zIndex }.thenBy { it.id })

    fun nextZIndex(document: CanvasDocument): Int =
        document.items.filterIsInstance<AttachmentItem>().maxOfOrNull { it.zIndex }?.plus(1) ?: 0

    fun add(
        document: CanvasDocument,
        sourcePath: String,
        kind: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        sectionId: String?,
        originalName: String?,
        sourcePage: Int? = null,
        crop: CropRect = CropRect()
    ): CanvasDocument {
        val safeCrop = crop.normalized()
        val item = AttachmentItem(
            id = UUID.randomUUID().toString(),
            x = x.coerceAtLeast(0f),
            y = y.coerceAtLeast(0f),
            width = width.coerceAtLeast(48f),
            height = height.coerceAtLeast(48f),
            uri = sourcePath,
            kind = kind,
            sectionId = sectionId,
            zIndex = nextZIndex(document),
            cropLeft = safeCrop.left,
            cropTop = safeCrop.top,
            cropRight = safeCrop.right,
            cropBottom = safeCrop.bottom,
            sourcePage = sourcePage,
            originalName = originalName
        )
        val neededHeight = max(document.heightDp, item.y + item.height + 240f)
        return document.copy(heightDp = neededHeight, items = document.items + item)
    }

    fun update(document: CanvasDocument, id: String, transform: (AttachmentItem) -> AttachmentItem): CanvasDocument =
        document.copy(items = document.items.map { item ->
            if (item is AttachmentItem && item.id == id) sanitize(transform(item), document.widthDp) else item
        })

    fun remove(document: CanvasDocument, id: String): CanvasDocument =
        document.copy(items = document.items.filterNot { it.id == id })

    fun bringForward(document: CanvasDocument, id: String): CanvasDocument = update(document, id) {
        it.copy(zIndex = nextZIndex(document))
    }

    fun sendBackward(document: CanvasDocument, id: String): CanvasDocument {
        val minZ = document.items.filterIsInstance<AttachmentItem>().minOfOrNull { it.zIndex } ?: 0
        return update(document, id) { it.copy(zIndex = minZ - 1) }
    }

    fun scale(document: CanvasDocument, id: String, factor: Float): CanvasDocument = update(document, id) {
        val nextW = (it.width * factor).coerceIn(72f, document.widthDp)
        val ratio = if (it.width > 0f) it.height / it.width else 1f
        it.copy(width = nextW, height = (nextW * ratio).coerceAtLeast(72f))
    }

    fun applyCrop(document: CanvasDocument, id: String, crop: CropRect): CanvasDocument = update(document, id) {
        val c = crop.normalized()
        it.copy(cropLeft = c.left, cropTop = c.top, cropRight = c.right, cropBottom = c.bottom)
    }

    private fun sanitize(item: AttachmentItem, pageWidth: Float): AttachmentItem {
        val crop = CropRect(item.cropLeft, item.cropTop, item.cropRight, item.cropBottom).normalized()
        val width = item.width.coerceIn(48f, pageWidth)
        val height = item.height.coerceAtLeast(48f)
        return item.copy(
            x = min(max(0f, item.x), max(0f, pageWidth - width)),
            y = item.y.coerceAtLeast(0f),
            width = width,
            height = height,
            rotationDegrees = ((item.rotationDegrees % 360f) + 360f) % 360f,
            opacity = item.opacity.coerceIn(0.10f, 1f),
            cropLeft = crop.left,
            cropTop = crop.top,
            cropRight = crop.right,
            cropBottom = crop.bottom
        )
    }
}
