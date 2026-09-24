package com.tomeofhealing.app.performance

import com.tomeofhealing.app.model.AttachmentItem
import com.tomeofhealing.app.model.CanvasItem
import com.tomeofhealing.app.model.StrokeItem
import com.tomeofhealing.app.model.TextItem
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Lightweight document-coordinate bounds used without Android graphics dependencies. */
data class DocBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean =
        this.left <= right && this.right >= left && this.top <= bottom && this.bottom >= top
}

/**
 * Vertical-band spatial index for large handwritten notes.
 *
 * Tome of Healing's canvas is fixed-width and vertically unbounded, so a banded index gives
 * most of the benefit of a 2-D tree with much less bookkeeping. Render/eraser/lasso queries
 * only inspect objects in nearby bands instead of scanning thousands of off-screen strokes.
 */
class CanvasSpatialIndex(private val bandHeightDp: Float = 480f) {
    private val bands = HashMap<Int, MutableSet<String>>()
    private val boundsById = HashMap<String, DocBounds>()

    fun rebuild(items: List<CanvasItem>) {
        bands.clear()
        boundsById.clear()
        items.forEach(::put)
    }

    fun put(item: CanvasItem) {
        remove(item.id)
        val bounds = boundsOf(item) ?: return
        boundsById[item.id] = bounds
        firstBand(bounds.top).rangeTo(lastBand(bounds.bottom)).forEach { band ->
            bands.getOrPut(band) { linkedSetOf() }.add(item.id)
        }
    }

    fun remove(id: String) {
        val old = boundsById.remove(id) ?: return
        firstBand(old.top).rangeTo(lastBand(old.bottom)).forEach { band ->
            bands[band]?.let { ids ->
                ids.remove(id)
                if (ids.isEmpty()) bands.remove(band)
            }
        }
    }

    fun query(left: Float, top: Float, right: Float, bottom: Float): Set<String> {
        if (bottom < top || right < left) return emptySet()
        val candidates = linkedSetOf<String>()
        firstBand(top).rangeTo(lastBand(bottom)).forEach { band -> bands[band]?.let(candidates::addAll) }
        return candidates.filterTo(linkedSetOf()) { id ->
            boundsById[id]?.intersects(left, top, right, bottom) == true
        }
    }

    fun bounds(id: String): DocBounds? = boundsById[id]

    private fun firstBand(y: Float): Int = floor(max(0f, y) / bandHeightDp).toInt()
    private fun lastBand(y: Float): Int = floor(max(0f, y) / bandHeightDp).toInt()

    private fun boundsOf(item: CanvasItem): DocBounds? = when (item) {
        is StrokeItem -> {
            if (item.points.isEmpty()) null else {
                var left = item.points.first().x
                var top = item.points.first().y
                var right = left
                var bottom = top
                item.points.drop(1).forEach { p ->
                    left = min(left, p.x)
                    top = min(top, p.y)
                    right = max(right, p.x)
                    bottom = max(bottom, p.y)
                }
                val pad = max(2f, item.width * 1.5f)
                DocBounds(left - pad, top - pad, right + pad, bottom + pad)
            }
        }
        is AttachmentItem -> DocBounds(item.x, item.y, item.x + item.width, item.y + item.height)
        is TextItem -> DocBounds(item.x, item.y, item.x + item.width, item.y + item.height)
    }
}
