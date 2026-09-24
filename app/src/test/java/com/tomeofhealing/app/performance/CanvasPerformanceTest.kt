package com.tomeofhealing.app.performance

import com.tomeofhealing.app.model.InkPoint
import com.tomeofhealing.app.model.StrokeItem
import com.tomeofhealing.app.model.TextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPerformanceTest {
    @Test
    fun straightStrokeIsCompactedWithoutMovingEndpoints() {
        val input = (0..2000).map { i -> InkPoint(i / 10f, i / 20f, 0.5f) }
        val output = StrokePointReducer.reduce(input)
        assertTrue(output.size < input.size / 4)
        assertEquals(input.first(), output.first())
        assertEquals(input.last(), output.last())
    }

    @Test
    fun indexReturnsOnlyObjectsInsideRequestedVerticalRegion() {
        val top = StrokeItem("top", listOf(InkPoint(10f, 10f), InkPoint(20f, 100f)), 0, 2f)
        val bottom = StrokeItem("bottom", listOf(InkPoint(10f, 1500f), InkPoint(20f, 1600f)), 0, 2f)
        val middle = TextItem("middle", 20f, 700f, 120f, "dose", height = 80f)
        val index = CanvasSpatialIndex(400f)
        index.rebuild(listOf(top, middle, bottom))

        assertEquals(setOf("top"), index.query(0f, 0f, 300f, 250f))
        assertEquals(setOf("middle"), index.query(0f, 650f, 300f, 850f))
        assertEquals(setOf("bottom"), index.query(0f, 1400f, 300f, 1700f))
    }
}
