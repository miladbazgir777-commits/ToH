package com.tomeofhealing.app.performance

import com.tomeofhealing.app.model.InkPoint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Reduces redundant stylus samples while preserving the visible stroke and pressure profile.
 * This keeps multi-hour notes responsive and materially reduces JSON/database size.
 */
object StrokePointReducer {
    fun reduce(points: List<InkPoint>, minDistanceDp: Float = 0.45f, toleranceDp: Float = 0.28f): List<InkPoint> {
        if (points.size <= 3) return points

        // First pass: remove oversampled neighbors, retaining meaningful pressure changes.
        val thinned = ArrayList<InkPoint>(points.size)
        thinned += points.first()
        for (i in 1 until points.lastIndex) {
            val previous = thinned.last()
            val current = points[i]
            val distance = hypot((current.x - previous.x).toDouble(), (current.y - previous.y).toDouble()).toFloat()
            val pressureDelta = abs(current.pressure - previous.pressure)
            if (distance >= minDistanceDp || pressureDelta >= 0.07f) thinned += current
        }
        thinned += points.last()
        if (thinned.size <= 3) return thinned

        // Second pass: iterative RDP geometry simplification. Pressure extrema survive because the
        // first pass preserved pressure changes and the effective tolerance is intentionally small.
        val keep = BooleanArray(thinned.size)
        keep[0] = true
        keep[thinned.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to thinned.lastIndex)
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            if (end <= start + 1) continue
            val a = thinned[start]
            val b = thinned[end]
            var farthestIndex = -1
            var farthestDistance = 0f
            for (i in start + 1 until end) {
                val p = thinned[i]
                val distance = distanceToSegment(p.x, p.y, a.x, a.y, b.x, b.y)
                val pressureImportance = abs(p.pressure - (a.pressure + b.pressure) * 0.5f) * 1.8f
                val score = distance + pressureImportance
                if (score > farthestDistance) {
                    farthestDistance = score
                    farthestIndex = i
                }
            }
            if (farthestIndex >= 0 && farthestDistance > toleranceDp) {
                keep[farthestIndex] = true
                stack.add(start to farthestIndex)
                stack.add(farthestIndex to end)
            }
        }
        return thinned.filterIndexed { index, _ -> keep[index] }
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        if (abs(dx) < 0.0001f && abs(dy) < 0.0001f) {
            return hypot((px - ax).toDouble(), (py - ay).toDouble()).toFloat()
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return hypot((px - cx).toDouble(), (py - cy).toDouble()).toFloat()
    }
}
