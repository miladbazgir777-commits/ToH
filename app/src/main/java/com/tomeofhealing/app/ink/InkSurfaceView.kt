package com.tomeofhealing.app.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tomeofhealing.app.model.AttachmentItem
import com.tomeofhealing.app.model.CanvasDocument
import com.tomeofhealing.app.model.InkPoint
import com.tomeofhealing.app.model.NoteSection
import com.tomeofhealing.app.model.SectionLayoutEngine
import com.tomeofhealing.app.model.StrokeItem
import com.tomeofhealing.app.model.TextItem
import com.tomeofhealing.app.model.TextObjectKind
import com.tomeofhealing.app.performance.CanvasSpatialIndex
import com.tomeofhealing.app.performance.StrokePointReducer
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Low-latency stylus-first vector handwriting surface.
 *
 * Document coordinates are density-independent units. The page width is fixed while the
 * vertical extent grows in chunks as the user approaches the bottom. Viewport zoom/scroll
 * are intentionally transient; ink geometry, pressure and page height live in CanvasDocument.
 */
class InkSurfaceView(context: Context) : View(context) {
    var document: CanvasDocument = CanvasDocument()
        set(value) {
            field = value
            if (!editingGesture) {
                workingDocument = value
                rebuildStrokeBounds()
                rebuildAttachmentBounds()
                rebuildTextBounds()
                rebuildSpatialIndex()
                val validIds = value.items.mapTo(mutableSetOf()) { it.id }
                val selectionBefore = selectedStrokeIds.size + selectedAttachmentIds.size + selectedTextIds.size
                selectedStrokeIds.retainAll(validIds)
                selectedAttachmentIds.retainAll(validIds)
                selectedTextIds.retainAll(validIds)
                val selectionAfter = selectedStrokeIds.size + selectedAttachmentIds.size + selectedTextIds.size
                if (selectionBefore != selectionAfter) {
                    onSelectionChanged?.invoke(selectionAfter)
                }
                clampViewport()
                invalidate()
            }
        }

    var toolConfig: InkToolConfig = InkToolConfig()
        set(value) {
            val toolChanged = field.tool != value.tool
            field = value
            if (toolChanged && value.tool != InkTool.LASSO) clearSelection()
            invalidate()
        }

    var paperPreset: String = "aged_parchment"
        set(value) {
            field = value
            invalidate()
        }

    var sections: List<NoteSection> = emptyList()
        set(value) {
            field = SectionLayoutEngine.normalizedSections(value)
            invalidate()
        }

    var onDocumentCommitted: ((CanvasDocument) -> Unit)? = null
    var onDocumentMetaChanged: ((CanvasDocument) -> Unit)? = null
    var onTemporaryEraserChanged: ((Boolean) -> Unit)? = null
    var onSelectionChanged: ((Int) -> Unit)? = null
    var onSectionGrowthRequested: ((String, Float) -> Unit)? = null
    var onLinkActivated: ((String) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private var workingDocument: CanvasDocument = document
    private val strokeBounds = mutableMapOf<String, RectF>()
    private val attachmentBounds = mutableMapOf<String, RectF>()
    private val textBounds = mutableMapOf<String, RectF>()
    private val attachmentBitmapCache = AttachmentBitmapCache(context)
    private val spatialIndex = CanvasSpatialIndex()
    private val itemsById = mutableMapOf<String, com.tomeofhealing.app.model.CanvasItem>()

    private val activeStroke = mutableListOf<InkPoint>()
    private var editingGesture = false
    private var stylusPointerId = MotionEvent.INVALID_POINTER_ID
    private var eraserChanged = false
    private var temporaryEraser = false

    private val selectedStrokeIds = linkedSetOf<String>()
    private val selectedAttachmentIds = linkedSetOf<String>()
    private val selectedTextIds = linkedSetOf<String>()
    private var movingSelection = false
    private var selectionMoveChanged = false
    private var lastSelectionPoint: InkPoint? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var fingerPanning = false
    private var fingerTravelPx = 0f
    private var fingerGestureHadMultiplePointers = false

    // User zoom multiplies an automatic fit-width base scale.
    private var userScale = 1f
    private var scrollYDoc = 0f
    private var scrollXDoc = 0f

    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.argb(190, 116, 91, 190)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val sectionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = AndroidColor.argb(130, 80, 62, 126)
    }
    private val sectionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(54, 40, 79)
        textSize = 14f * density
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
    }
    private val collapsedSectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.argb(58, 73, 53, 105)
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = !isStylusActive()

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isStylusActive()) return false
                val focusDocBefore = screenToDocument(detector.focusX, detector.focusY)
                val oldScale = effectiveScale()
                userScale = (userScale * detector.scaleFactor).coerceIn(0.45f, 4f)
                val newScale = effectiveScale()
                if (oldScale != newScale) {
                    val pageLeft = pageLeftPx(newScale)
                    scrollXDoc = focusDocBefore.x - (detector.focusX - pageLeft) / newScale
                    scrollYDoc = focusDocBefore.y - detector.focusY / newScale
                    clampViewport()
                    invalidate()
                }
                return true
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = effectiveScale()
        val pageLeft = pageLeftPx(scale)
        val pageRight = pageLeft + workingDocument.widthDp * scale
        val pageBottom = (workingDocument.heightDp - scrollYDoc) * scale

        canvas.drawColor(AndroidColor.rgb(42, 37, 52))
        pagePaint.color = paperColor(paperPreset)
        canvas.drawRect(pageLeft, 0f, pageRight, max(height.toFloat(), pageBottom), pagePaint)
        drawPaperGuides(canvas, pageLeft, pageRight, scale)
        drawAttachments(canvas, pageLeft, pageRight, scale)
        drawTextObjects(canvas, pageLeft, pageRight, scale)
        drawSectionGuides(canvas, pageLeft, pageRight, scale)
        drawStylusHover(canvas, pageLeft, scale)

        val viewport = RectF(
            scrollXDoc - 24f,
            scrollYDoc - 24f,
            scrollXDoc + width / scale + 24f,
            scrollYDoc + height / scale + 24f
        )

        canvas.save()
        canvas.clipRect(pageLeft, 0f, pageRight, height.toFloat())
        val visibleIds = spatialIndex.query(viewport.left, viewport.top, viewport.right, viewport.bottom)
        visibleIds.asSequence()
            .mapNotNull { itemsById[it] as? StrokeItem }
            .forEach { stroke ->
                if (isStrokeHiddenByCollapsedSection(stroke)) return@forEach
                if (stroke.id in selectedStrokeIds) drawSelectionHalo(canvas, stroke, scale, pageLeft)
                drawStroke(canvas, stroke, scale, pageLeft)
            }

        if (activeStroke.size >= 2) {
            when (effectiveTool()) {
                InkTool.LASSO -> drawLasso(canvas, activeStroke, scale, pageLeft)
                InkTool.ERASER -> Unit
                else -> {
                    val preview = StrokeItem(
                        id = "active",
                        points = geometryPointsForTool(effectiveTool(), activeStroke),
                        colorArgb = toolConfig.color.value.toLong(),
                        width = currentBaseWidth(),
                        opacity = opacityForTool(effectiveTool()),
                        tool = effectiveTool().name.lowercase(),
                        sectionId = activeStroke.firstOrNull()?.let { sectionAtY(it.y)?.id }
                    )
                    drawStroke(canvas, preview, scale, pageLeft)
                }
            }
        }
        canvas.restore()
    }

    private var hoverPoint: PointF? = null

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(event.actionIndex.coerceAtLeast(0))
        val stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        if (!stylus) return super.onHoverEvent(event)
        hoverPoint = when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> screenToDocument(event.x, event.y)
            MotionEvent.ACTION_HOVER_EXIT -> null
            else -> hoverPoint
        }
        invalidate()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val stylusIndex = firstStylusPointerIndex(event)
        if (stylusIndex >= 0 || isStylusActive()) {
            return handleStylusEvent(event, stylusIndex)
        }

        // Finger input is navigation only. A palm/finger never creates ink.
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            fingerPanning = false
            fingerGestureHadMultiplePointers = true
        }
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fingerPanning = true
                fingerTravelPx = 0f
                fingerGestureHadMultiplePointers = false
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!fingerPanning || event.pointerCount != 1) return true
                val scale = effectiveScale()
                val dy = event.y - lastTouchY
                val dx = event.x - lastTouchX
                fingerTravelPx += hypot(dx.toDouble(), dy.toDouble()).toFloat()
                scrollYDoc -= dy / scale
                // Horizontal pan is available only when zoomed beyond fit-width.
                if (userScale > 1.05f) scrollXDoc -= dx / scale
                lastTouchX = event.x
                lastTouchY = event.y
                maybeExtendDocument()
                clampViewport()
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val lifted = event.actionIndex
                val remaining = (0 until event.pointerCount).firstOrNull { it != lifted }
                if (remaining != null && event.pointerCount - 1 == 1) {
                    lastTouchX = event.getX(remaining)
                    lastTouchY = event.getY(remaining)
                    fingerPanning = true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP && !fingerGestureHadMultiplePointers && fingerTravelPx < 12f * density) {
                    handleContentTap(screenToDocument(event.x, event.y))
                }
                fingerPanning = false
                fingerTravelPx = 0f
                fingerGestureHadMultiplePointers = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun handleStylusEvent(event: MotionEvent, suppliedStylusIndex: Int): Boolean {
        val actionIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val actionPointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val downToolType = event.getToolType(actionIndex)
                val actionIsStylus = downToolType == MotionEvent.TOOL_TYPE_STYLUS ||
                    downToolType == MotionEvent.TOOL_TYPE_ERASER
                if (!actionIsStylus) return true // palm/finger while pen is present

                stylusPointerId = actionPointerId
                editingGesture = true
                eraserChanged = false
                selectionMoveChanged = false
                updateTemporaryEraser(event, downToolType)
                val point = eventPoint(event, actionIndex)
                if (sectionAtY(point.y)?.collapsed == true) {
                    stylusPointerId = MotionEvent.INVALID_POINTER_ID
                    editingGesture = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }

                when (effectiveTool()) {
                    InkTool.ERASER -> eraseAt(point)
                    InkTool.LASSO -> beginLasso(point)
                    else -> {
                        activeStroke.clear()
                        activeStroke += point
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val activeIndex = event.findPointerIndex(stylusPointerId)
                if (activeIndex < 0) return true
                updateTemporaryEraser(event, event.getToolType(activeIndex))

                when (effectiveTool()) {
                    InkTool.ERASER -> {
                        for (h in 0 until event.historySize) eraseAt(historicalEventPoint(event, activeIndex, h))
                        eraseAt(eventPoint(event, activeIndex))
                    }
                    InkTool.LASSO -> {
                        if (movingSelection) {
                            moveSelectionTo(eventPoint(event, activeIndex))
                        } else {
                            for (h in 0 until event.historySize) activeStroke += historicalEventPoint(event, activeIndex, h)
                            activeStroke += eventPoint(event, activeIndex)
                        }
                    }
                    else -> {
                        for (h in 0 until event.historySize) activeStroke += historicalEventPoint(event, activeIndex, h)
                        activeStroke += eventPoint(event, activeIndex)
                        maybeExtendForPoint(activeStroke.last())
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked != MotionEvent.ACTION_CANCEL &&
                    actionPointerId != stylusPointerId && stylusPointerId != MotionEvent.INVALID_POINTER_ID
                ) return true

                val effective = effectiveTool()
                when (effective) {
                    InkTool.ERASER -> if (eraserChanged) commitWorkingDocument()
                    InkTool.LASSO -> finishLasso(event.actionMasked != MotionEvent.ACTION_CANCEL)
                    else -> if (activeStroke.isNotEmpty() && event.actionMasked != MotionEvent.ACTION_CANCEL) {
                        commitActiveStroke(effective)
                    }
                }

                activeStroke.clear()
                movingSelection = false
                lastSelectionPoint = null
                stylusPointerId = MotionEvent.INVALID_POINTER_ID
                editingGesture = false
                if (temporaryEraser) {
                    temporaryEraser = false
                    onTemporaryEraserChanged?.invoke(false)
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return suppliedStylusIndex >= 0
    }

    private fun commitActiveStroke(tool: InkTool) {
        val rawPoints = geometryPointsForTool(tool, activeStroke)
        val committedPoints = when (tool) {
            InkTool.RULER, InkTool.SHAPE -> rawPoints
            else -> StrokePointReducer.reduce(rawPoints)
        }
        val owningSection = committedPoints.firstOrNull()?.let { sectionAtY(it.y) }
        val item = StrokeItem(
            id = UUID.randomUUID().toString(),
            points = committedPoints,
            colorArgb = toolConfig.color.value.toLong(),
            width = currentBaseWidth(),
            opacity = opacityForTool(tool),
            tool = tool.name.lowercase(),
            sectionId = owningSection?.id
        )
        workingDocument = workingDocument.copy(items = workingDocument.items + item)
        strokeBounds[item.id] = boundsFor(item)
        itemsById[item.id] = item
        spatialIndex.put(item)
        commitWorkingDocument()
        maybeRequestSectionGrowth(item, owningSection)
    }

    private fun beginLasso(point: InkPoint) {
        val selectionBounds = selectedBounds()
        if (selectionBounds != null && selectionBounds.contains(point.x, point.y)) {
            movingSelection = true
            lastSelectionPoint = point
            activeStroke.clear()
        } else {
            movingSelection = false
            clearSelection()
            activeStroke.clear()
            activeStroke += point
        }
    }

    private fun moveSelectionTo(point: InkPoint) {
        val previous = lastSelectionPoint ?: point
        val dx = point.x - previous.x
        val dy = point.y - previous.y
        if (abs(dx) < 0.001f && abs(dy) < 0.001f) return

        val nextItems = workingDocument.items.map { item ->
            when {
                item is StrokeItem && item.id in selectedStrokeIds -> {
                    val moved = item.copy(points = item.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) })
                    strokeBounds[moved.id] = boundsFor(moved)
                    moved
                }
                item is AttachmentItem && item.id in selectedAttachmentIds && !item.locked -> {
                    val moved = item.copy(
                        x = (item.x + dx).coerceIn(0f, max(0f, workingDocument.widthDp - item.width)),
                        y = (item.y + dy).coerceAtLeast(0f)
                    )
                    attachmentBounds[moved.id] = boundsForAttachment(moved)
                    moved
                }
                item is TextItem && item.id in selectedTextIds && !item.locked -> {
                    val moved = item.copy(
                        x = (item.x + dx).coerceIn(0f, max(0f, workingDocument.widthDp - item.width)),
                        y = (item.y + dy).coerceAtLeast(0f)
                    )
                    textBounds[moved.id] = boundsForText(moved)
                    moved
                }
                else -> item
            }
        }
        workingDocument = workingDocument.copy(items = nextItems)
        rebuildSpatialIndex()
        selectionMoveChanged = true
        lastSelectionPoint = point
        maybeExtendForPoint(point)
    }

    private fun finishLasso(commit: Boolean) {
        if (movingSelection) {
            if (commit && selectionMoveChanged) commitWorkingDocument()
            return
        }
        if (!commit || activeStroke.size < 3) {
            clearSelection()
            return
        }

        val polygon = activeStroke.toList()
        val polygonBounds = boundsForPoints(polygon)
        val selectedStrokes = workingDocument.items.filterIsInstance<StrokeItem>().filter { stroke ->
            if (isStrokeHiddenByCollapsedSection(stroke)) return@filter false
            val bounds = strokeBounds[stroke.id] ?: boundsFor(stroke)
            RectF.intersects(bounds, polygonBounds) && strokeIntersectsPolygon(stroke, polygon)
        }.mapTo(linkedSetOf()) { it.id }
        val selectedAttachments = workingDocument.items.filterIsInstance<AttachmentItem>().filter { item ->
            if (isAttachmentHiddenByCollapsedSection(item)) return@filter false
            val bounds = attachmentBounds[item.id] ?: boundsForAttachment(item)
            if (!RectF.intersects(bounds, polygonBounds)) return@filter false
            val centerX = (bounds.left + bounds.right) * 0.5f
            val centerY = (bounds.top + bounds.bottom) * 0.5f
            pointInPolygon(centerX, centerY, polygon) || polygon.any { bounds.contains(it.x, it.y) }
        }.mapTo(linkedSetOf()) { it.id }
        val selectedTexts = workingDocument.items.filterIsInstance<TextItem>().filter { item ->
            if (isTextHiddenByCollapsedSection(item)) return@filter false
            val bounds = textBounds[item.id] ?: boundsForText(item)
            if (!RectF.intersects(bounds, polygonBounds)) return@filter false
            val centerX = (bounds.left + bounds.right) * 0.5f
            val centerY = (bounds.top + bounds.bottom) * 0.5f
            pointInPolygon(centerX, centerY, polygon) || polygon.any { bounds.contains(it.x, it.y) }
        }.mapTo(linkedSetOf()) { it.id }
        selectedStrokeIds.clear()
        selectedStrokeIds.addAll(selectedStrokes)
        selectedAttachmentIds.clear()
        selectedAttachmentIds.addAll(selectedAttachments)
        selectedTextIds.clear()
        selectedTextIds.addAll(selectedTexts)
        onSelectionChanged?.invoke(selectedStrokeIds.size + selectedAttachmentIds.size + selectedTextIds.size)
    }

    private fun strokeIntersectsPolygon(stroke: StrokeItem, polygon: List<InkPoint>): Boolean {
        if (stroke.points.any { pointInPolygon(it.x, it.y, polygon) }) return true
        if (stroke.points.isNotEmpty()) {
            val middle = stroke.points[stroke.points.size / 2]
            if (pointInPolygon(middle.x, middle.y, polygon)) return true
        }
        // Also catch a polygon crossing a sparse stroke between sampled points.
        for (lassoPoint in polygon) {
            for (i in 1 until stroke.points.size) {
                val a = stroke.points[i - 1]
                val b = stroke.points[i]
                if (distanceToSegment(lassoPoint.x, lassoPoint.y, a.x, a.y, b.x, b.y) <= 5f) return true
            }
        }
        return false
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<InkPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val crosses = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > 0.00001f } ?: 0.00001f) + xi)
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    private fun clearSelection() {
        if (selectedStrokeIds.isEmpty() && selectedAttachmentIds.isEmpty() && selectedTextIds.isEmpty()) return
        selectedStrokeIds.clear()
        selectedAttachmentIds.clear()
        selectedTextIds.clear()
        onSelectionChanged?.invoke(0)
        invalidate()
    }

    private fun selectedBounds(): RectF? {
        val selected = buildList {
            addAll(selectedStrokeIds.mapNotNull { strokeBounds[it] })
            addAll(selectedAttachmentIds.mapNotNull { attachmentBounds[it] })
            addAll(selectedTextIds.mapNotNull { textBounds[it] })
        }
        if (selected.isEmpty()) return null
        val result = RectF(selected.first())
        selected.drop(1).forEach { result.union(it) }
        result.inset(-12f, -12f)
        return result
    }

    private fun commitWorkingDocument() {
        document = workingDocument
        onDocumentCommitted?.invoke(workingDocument)
    }

    private fun eraseAt(point: InkPoint) {
        val radius = toolConfig.eraserRadiusDp * density / effectiveScale()
        val erasedIds = spatialIndex.query(
            point.x - radius, point.y - radius, point.x + radius, point.y + radius
        ).asSequence()
            .mapNotNull { itemsById[it] as? StrokeItem }
            .filter { stroke -> !isStrokeHiddenByCollapsedSection(stroke) }
            .filter { stroke -> strokeIntersects(stroke, point.x, point.y, radius) }
            .map { it.id }
            .toSet()
        if (erasedIds.isEmpty()) return

        workingDocument = workingDocument.copy(items = workingDocument.items.filterNot { it.id in erasedIds })
        erasedIds.forEach { id ->
            strokeBounds.remove(id)
            spatialIndex.remove(id)
            itemsById.remove(id)
        }
        if (selectedStrokeIds.removeAll(erasedIds)) onSelectionChanged?.invoke(selectedStrokeIds.size + selectedAttachmentIds.size + selectedTextIds.size)
        eraserChanged = true
        invalidate()
    }

    private fun strokeIntersects(stroke: StrokeItem, x: Float, y: Float, radius: Float): Boolean {
        if (stroke.points.isEmpty()) return false
        if (stroke.points.size == 1) {
            val p = stroke.points.first()
            return hypot((p.x - x).toDouble(), (p.y - y).toDouble()) <= radius.toDouble()
        }
        for (i in 1 until stroke.points.size) {
            val a = stroke.points[i - 1]
            val b = stroke.points[i]
            if (distanceToSegment(x, y, a.x, a.y, b.x, b.y) <= radius) return true
        }
        return false
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

    private fun drawStroke(canvas: Canvas, item: StrokeItem, scale: Float, pageLeft: Float) {
        if (item.points.size < 2) return
        strokePaint.color = Color(item.colorArgb.toULong()).toArgb()
        strokePaint.alpha = (item.opacity.coerceIn(0f, 1f) * 255f).toInt()

        for (i in 1 until item.points.size) {
            val a = item.points[i - 1]
            val b = item.points[i]
            val pressure = ((a.pressure + b.pressure) * 0.5f).coerceIn(0.12f, 1f)
            val pressureFactor = when (item.tool) {
                "highlighter", "marker", "ruler", "shape" -> 1f
                "pencil" -> 0.30f + pressure * 0.75f
                "ballpoint" -> 0.75f + pressure * 0.30f
                "fountain" -> if (toolConfig.pressureEnabled) 0.30f + pressure * 1.05f else 1f
                else -> if (toolConfig.pressureEnabled) 0.42f + pressure * 0.88f else 1f
            }
            strokePaint.strokeWidth = max(0.75f, item.width * pressureFactor * scale)
            canvas.drawLine(
                pageLeft + (a.x - scrollXDoc) * scale,
                (a.y - scrollYDoc) * scale,
                pageLeft + (b.x - scrollXDoc) * scale,
                (b.y - scrollYDoc) * scale,
                strokePaint
            )
        }
    }

    private fun drawSelectionHalo(canvas: Canvas, item: StrokeItem, scale: Float, pageLeft: Float) {
        if (item.points.size < 2) return
        selectionPaint.strokeWidth = max(3f, item.width * scale + 6f)
        for (i in 1 until item.points.size) {
            val a = item.points[i - 1]
            val b = item.points[i]
            canvas.drawLine(
                pageLeft + (a.x - scrollXDoc) * scale,
                (a.y - scrollYDoc) * scale,
                pageLeft + (b.x - scrollXDoc) * scale,
                (b.y - scrollYDoc) * scale,
                selectionPaint
            )
        }
    }

    private fun drawLasso(canvas: Canvas, points: List<InkPoint>, scale: Float, pageLeft: Float) {
        selectionPaint.strokeWidth = max(2f, 1.6f * scale)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            canvas.drawLine(
                pageLeft + (a.x - scrollXDoc) * scale,
                (a.y - scrollYDoc) * scale,
                pageLeft + (b.x - scrollXDoc) * scale,
                (b.y - scrollYDoc) * scale,
                selectionPaint
            )
        }
    }

    private fun drawTextObjects(canvas: Canvas, pageLeft: Float, pageRight: Float, scale: Float) {
        val viewport = RectF(
            scrollXDoc - 48f,
            scrollYDoc - 48f,
            scrollXDoc + width / scale + 48f,
            scrollYDoc + height / scale + 48f
        )
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

        spatialIndex.query(viewport.left, viewport.top, viewport.right, viewport.bottom).asSequence()
            .mapNotNull { itemsById[it] as? TextItem }
            .sortedWith(compareBy<TextItem> { it.zIndex }.thenBy { it.id })
            .forEach { item ->
                if (isTextHiddenByCollapsedSection(item)) return@forEach
                val bounds = textBounds[item.id] ?: boundsForText(item)
                if (!RectF.intersects(bounds, viewport)) return@forEach

                val left = pageLeft + (item.x - scrollXDoc) * scale
                val top = (item.y - scrollYDoc) * scale
                val right = left + item.width * scale
                val bottom = top + item.height * scale
                val rect = RectF(left, top, right, bottom)
                if (right < pageLeft || left > pageRight || bottom < 0f || top > height) return@forEach

                fill.color = Color(item.backgroundColorArgb.toULong()).toArgb()
                fill.alpha = 235
                border.color = Color(item.borderColorArgb.toULong()).toArgb()
                border.strokeWidth = max(1f, 1.4f * scale)

                canvas.save()
                canvas.clipRect(pageLeft, 0f, pageRight, height.toFloat())
                canvas.drawRoundRect(rect, 10f * scale, 10f * scale, fill)
                canvas.drawRoundRect(rect, 10f * scale, 10f * scale, border)
                if (item.id in selectedTextIds) {
                    val selected = Paint(selectionPaint).apply { strokeWidth = max(2f, 3f * scale) }
                    canvas.drawRoundRect(rect, 12f * scale, 12f * scale, selected)
                }

                when (item.kind) {
                    TextObjectKind.TABLE -> drawTableObject(canvas, item, rect, scale)
                    TextObjectKind.CHECKLIST -> drawChecklistObject(canvas, item, rect, scale)
                    else -> {
                        val display = when (item.kind) {
                            TextObjectKind.MEDICATION -> medicationDisplayText(item)
                            else -> item.text
                        }
                        textPaint.color = Color(item.textColorArgb.toULong()).toArgb()
                        textPaint.textSize = max(9f * density, item.fontSizeSp * scale)
                        textPaint.isFakeBoldText = item.kind == TextObjectKind.LABEL || item.kind == TextObjectKind.MEDICATION
                        textPaint.isUnderlineText = item.kind == TextObjectKind.LINK
                        val padding = 14f * scale
                        val available = max(1, (rect.width() - padding * 2f).toInt())
                        val layout = StaticLayout.Builder.obtain(display, 0, display.length, textPaint, available)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setIncludePad(false)
                            .setLineSpacing(0f, 1.08f)
                            .setMaxLines(12)
                            .build()
                        canvas.save()
                        canvas.clipRect(rect.left + padding, rect.top + padding, rect.right - padding, rect.bottom - padding)
                        canvas.translate(rect.left + padding, rect.top + padding)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                }
                canvas.restore()
            }
    }

    private fun drawTableObject(canvas: Canvas, item: TextItem, rect: RectF, scale: Float) {
        val rows = item.tableRows.ifEmpty { listOf(listOf(item.text.ifBlank { "Table" })) }
        val cols = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color(item.borderColorArgb.toULong()).toArgb()
            strokeWidth = max(1f, scale)
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(item.textColorArgb.toULong()).toArgb()
            textSize = max(8f * density, item.fontSizeSp * 0.78f * scale)
        }
        val rowH = rect.height() / rows.size.coerceAtLeast(1)
        val colW = rect.width() / cols
        for (r in rows.indices) {
            for (c in 0 until cols) {
                val cell = RectF(rect.left + c * colW, rect.top + r * rowH, rect.left + (c + 1) * colW, rect.top + (r + 1) * rowH)
                canvas.drawRect(cell, line)
                val value = rows[r].getOrNull(c).orEmpty()
                val maxChars = max(4, (colW / max(5f, tp.textSize * 0.52f)).toInt())
                val shown = if (value.length <= maxChars) value else value.take(maxChars - 1) + "…"
                canvas.drawText(shown, cell.left + 8f * scale, cell.centerY() - (tp.ascent() + tp.descent()) / 2f, tp)
            }
        }
    }

    private fun drawChecklistObject(canvas: Canvas, item: TextItem, rect: RectF, scale: Float) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(item.textColorArgb.toULong()).toArgb()
            textSize = max(9f * density, item.fontSizeSp * 0.88f * scale)
        }
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color(item.borderColorArgb.toULong()).toArgb()
            strokeWidth = max(1.2f, 1.5f * scale)
        }
        val startY = rect.top + 18f * scale
        val rowH = 34f * scale
        item.checklist.forEachIndexed { index, entry ->
            val y = startY + index * rowH
            if (y + rowH > rect.bottom) return@forEachIndexed
            val b = RectF(rect.left + 14f * scale, y + 4f * scale, rect.left + 32f * scale, y + 22f * scale)
            canvas.drawRect(b, box)
            if (entry.checked) {
                canvas.drawLine(b.left + 3f * scale, b.centerY(), b.centerX(), b.bottom - 3f * scale, box)
                canvas.drawLine(b.centerX(), b.bottom - 3f * scale, b.right - 2f * scale, b.top + 3f * scale, box)
            }
            val label = if (entry.checked) "${entry.text} ✓" else entry.text
            canvas.drawText(label.take(80), rect.left + 42f * scale, y + 20f * scale, tp)
        }
    }

    private fun medicationDisplayText(item: TextItem): String {
        val m = item.metadata
        return buildList {
            m["name"]?.takeIf { it.isNotBlank() }?.let { add(it) }
            listOfNotNull(
                m["dose"]?.takeIf { it.isNotBlank() },
                m["route"]?.takeIf { it.isNotBlank() },
                m["frequency"]?.takeIf { it.isNotBlank() }
            ).joinToString(" • ").takeIf { it.isNotBlank() }?.let { add(it) }
            m["duration"]?.takeIf { it.isNotBlank() }?.let { add("Duration: $it") }
            m["note"]?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("\n")
    }

    private fun handleContentTap(point: PointF) {
        val hit = workingDocument.items.filterIsInstance<TextItem>()
            .filterNot(::isTextHiddenByCollapsedSection)
            .sortedWith(compareByDescending<TextItem> { it.zIndex }.thenByDescending { it.id })
            .firstOrNull { (textBounds[it.id] ?: boundsForText(it)).contains(point.x, point.y) }
            ?: return

        when (hit.kind) {
            TextObjectKind.LINK -> hit.metadata["target"]?.takeIf { it.isNotBlank() }?.let { onLinkActivated?.invoke(it) }
            TextObjectKind.CHECKLIST -> {
                val relativeY = point.y - hit.y - 18f
                val index = (relativeY / 34f).toInt()
                val entry = hit.checklist.getOrNull(index) ?: return
                val changed = hit.copy(checklist = hit.checklist.map {
                    if (it.id == entry.id) it.copy(checked = !it.checked) else it
                })
                workingDocument = workingDocument.copy(items = workingDocument.items.map { if (it.id == hit.id) changed else it })
                itemsById[changed.id] = changed
                spatialIndex.put(changed)
                commitWorkingDocument()
                invalidate()
            }
            else -> Unit
        }
    }

    private fun drawAttachments(canvas: Canvas, pageLeft: Float, pageRight: Float, scale: Float) {
        val attachmentPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1.5f, 2f * scale)
            color = AndroidColor.argb(210, 116, 91, 190)
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(220, 50, 45, 58)
            textSize = max(10f * density, 12f * scale)
        }
        val viewport = RectF(
            scrollXDoc - 48f,
            scrollYDoc - 48f,
            scrollXDoc + width / scale + 48f,
            scrollYDoc + height / scale + 48f
        )
        spatialIndex.query(viewport.left, viewport.top, viewport.right, viewport.bottom).asSequence()
            .mapNotNull { itemsById[it] as? AttachmentItem }
            .sortedWith(compareBy<AttachmentItem> { it.zIndex }.thenBy { it.id })
            .forEach { item ->
                if (isAttachmentHiddenByCollapsedSection(item)) return@forEach
                val bounds = attachmentBounds[item.id] ?: boundsForAttachment(item)
                if (!RectF.intersects(bounds, viewport)) return@forEach
                val bitmap = attachmentBitmapCache.bitmap(item) ?: return@forEach

                val left = pageLeft + (item.x - scrollXDoc) * scale
                val top = (item.y - scrollYDoc) * scale
                val right = left + item.width * scale
                val bottom = top + item.height * scale
                val dest = RectF(left, top, right, bottom)

                val cropLeft = (item.cropLeft.coerceIn(0f, 0.99f) * bitmap.width).toInt()
                val cropTop = (item.cropTop.coerceIn(0f, 0.99f) * bitmap.height).toInt()
                val cropRight = (item.cropRight.coerceIn(item.cropLeft + 0.01f, 1f) * bitmap.width).toInt()
                val cropBottom = (item.cropBottom.coerceIn(item.cropTop + 0.01f, 1f) * bitmap.height).toInt()
                val src = Rect(
                    cropLeft.coerceIn(0, bitmap.width - 1),
                    cropTop.coerceIn(0, bitmap.height - 1),
                    cropRight.coerceIn(cropLeft + 1, bitmap.width),
                    cropBottom.coerceIn(cropTop + 1, bitmap.height)
                )

                canvas.save()
                canvas.clipRect(pageLeft, 0f, pageRight, height.toFloat())
                canvas.rotate(item.rotationDegrees, dest.centerX(), dest.centerY())
                attachmentPaint.alpha = (item.opacity.coerceIn(0.10f, 1f) * 255f).toInt()
                canvas.drawBitmap(bitmap, src, dest, attachmentPaint)
                if (item.id in selectedAttachmentIds) canvas.drawRect(dest, borderPaint)
                canvas.restore()

                item.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                    canvas.drawText(caption.take(80), left, bottom + 18f * scale, captionPaint)
                }
            }
    }

    private fun drawStylusHover(canvas: Canvas, pageLeft: Float, scale: Float) {
        val point = hoverPoint ?: return
        val x = pageLeft + (point.x - scrollXDoc) * scale
        val y = (point.y - scrollYDoc) * scale
        if (x < pageLeft || x > pageLeft + workingDocument.widthDp * scale || y < 0f || y > height) return
        val preview = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, 1.2f * density)
            color = AndroidColor.argb(145, 116, 91, 190)
        }
        val radius = when (effectiveTool()) {
            InkTool.ERASER -> toolConfig.eraserRadiusDp * density
            InkTool.HIGHLIGHTER, InkTool.MARKER -> max(5f * density, currentBaseWidth() * scale * 0.5f)
            else -> max(2.5f * density, currentBaseWidth() * scale * 0.8f)
        }
        canvas.drawCircle(x, y, radius, preview)
    }

    private fun drawPaperGuides(canvas: Canvas, pageLeft: Float, pageRight: Float, scale: Float) {
        val preset = paperPreset.lowercase()
        if (preset == "blank" || preset == "aged_parchment" || preset == "dark_parchment") return
        val spacingDp = when {
            preset.contains("ecg") -> 10f
            preset.contains("grid") -> 28f
            preset.contains("dotted") -> 24f
            else -> 32f
        }
        val spacing = spacingDp * scale
        rulePaint.color = when {
            preset.contains("ecg") -> AndroidColor.argb(75, 175, 80, 95)
            else -> AndroidColor.argb(55, 70, 84, 112)
        }

        var y = ((-scrollYDoc * scale) % spacing)
        while (y < height) {
            canvas.drawLine(pageLeft, y, pageRight, y, rulePaint)
            y += spacing
        }
        if (preset.contains("grid") || preset.contains("ecg") || preset.contains("dotted")) {
            var x = pageLeft + ((-scrollXDoc * scale) % spacing)
            while (x < pageRight) {
                if (preset.contains("dotted")) {
                    var dotY = ((-scrollYDoc * scale) % spacing)
                    while (dotY < height) {
                        canvas.drawCircle(x, dotY, max(1f, scale), rulePaint)
                        dotY += spacing
                    }
                } else {
                    canvas.drawLine(x, 0f, x, height.toFloat(), rulePaint)
                }
                x += spacing
            }
        }
    }

    private fun paperColor(preset: String): Int = when (preset.lowercase()) {
        "blank" -> AndroidColor.rgb(247, 244, 236)
        "dark_parchment" -> AndroidColor.rgb(72, 65, 70)
        "stone_tablet" -> AndroidColor.rgb(188, 187, 182)
        "arcane_manuscript" -> AndroidColor.rgb(225, 220, 235)
        else -> AndroidColor.rgb(233, 223, 200)
    }

    private fun currentBaseWidth(): Float = when (effectiveTool()) {
        InkTool.HIGHLIGHTER -> toolConfig.highlighterWidthDp
        InkTool.MARKER -> toolConfig.highlighterWidthDp * 0.55f
        InkTool.BALLPOINT -> toolConfig.penWidthDp * 0.75f
        InkTool.PENCIL -> toolConfig.penWidthDp * 0.85f
        InkTool.FOUNTAIN -> toolConfig.penWidthDp * 1.15f
        else -> toolConfig.penWidthDp
    }

    private fun geometryPointsForTool(tool: InkTool, source: List<InkPoint>): List<InkPoint> {
        if (source.isEmpty()) return emptyList()
        if (source.size == 1) {
            val p = source.first()
            return listOf(p, p.copy(x = p.x + 0.01f, y = p.y + 0.01f))
        }
        val first = source.first()
        val last = source.last()
        return when (tool) {
            InkTool.RULER -> listOf(first, last)
            InkTool.SHAPE -> listOf(
                first,
                first.copy(x = last.x),
                last,
                first.copy(y = last.y),
                first
            )
            else -> source.toList()
        }
    }

    private fun opacityForTool(tool: InkTool): Float = when (tool) {
        InkTool.HIGHLIGHTER -> 0.30f
        InkTool.PENCIL -> 0.72f
        InkTool.MARKER -> 0.88f
        else -> 1f
    }

    private fun effectiveTool(): InkTool = if (temporaryEraser) InkTool.ERASER else toolConfig.tool

    private fun updateTemporaryEraser(event: MotionEvent, toolType: Int) {
        val buttonPressed = event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        val next = toolType == MotionEvent.TOOL_TYPE_ERASER || buttonPressed
        if (temporaryEraser != next) {
            temporaryEraser = next
            onTemporaryEraserChanged?.invoke(next)
        }
    }

    private fun eventPoint(event: MotionEvent, index: Int): InkPoint {
        val p = screenToDocument(event.getX(index), event.getY(index))
        return InkPoint(p.x, p.y, event.getPressure(index).coerceIn(0.05f, 1.2f))
    }

    private fun historicalEventPoint(event: MotionEvent, index: Int, historyIndex: Int): InkPoint {
        val p = screenToDocument(event.getHistoricalX(index, historyIndex), event.getHistoricalY(index, historyIndex))
        return InkPoint(p.x, p.y, event.getHistoricalPressure(index, historyIndex).coerceIn(0.05f, 1.2f))
    }

    fun scrollToDocumentY(y: Float) {
        scrollYDoc = (y - 28f).coerceAtLeast(0f)
        clampViewport()
        invalidate()
    }

    fun currentViewportCenterDocumentY(): Float =
        scrollYDoc + if (height > 0) height / effectiveScale() * 0.5f else 0f

    private fun sectionAtY(y: Float): NoteSection? = SectionLayoutEngine.sectionAtY(sections, y)

    private fun sectionForStroke(stroke: StrokeItem): NoteSection? {
        val explicit = stroke.sectionId?.let { id -> sections.firstOrNull { it.id == id } }
        if (explicit != null) return explicit
        val y = stroke.points.firstOrNull()?.y ?: return null
        return sectionAtY(y)
    }

    private fun isStrokeHiddenByCollapsedSection(stroke: StrokeItem): Boolean =
        sectionForStroke(stroke)?.collapsed == true

    private fun isAttachmentHiddenByCollapsedSection(item: AttachmentItem): Boolean {
        val explicit = item.sectionId?.let { id -> sections.firstOrNull { it.id == id } }
        val section = explicit ?: sectionAtY(item.y)
        return section?.collapsed == true
    }

    private fun isTextHiddenByCollapsedSection(item: TextItem): Boolean {
        val explicit = item.sectionId?.let { id -> sections.firstOrNull { it.id == id } }
        val section = explicit ?: sectionAtY(item.y)
        return section?.collapsed == true
    }

    private fun maybeRequestSectionGrowth(item: StrokeItem, owningSection: NoteSection?) {
        val section = owningSection ?: return
        val ordered = SectionLayoutEngine.normalizedSections(sections)
        val index = ordered.indexOfFirst { it.id == section.id }
        if (index < 0 || index == ordered.lastIndex) return
        val nextAnchor = ordered[index + 1].verticalAnchor
        val bottom = item.points.maxOfOrNull { it.y } ?: return
        if (nextAnchor - bottom < 72f) {
            onSectionGrowthRequested?.invoke(section.id, SectionLayoutEngine.GROWTH_CHUNK_DP)
        }
    }

    private fun drawSectionGuides(canvas: Canvas, pageLeft: Float, pageRight: Float, scale: Float) {
        val ordered = SectionLayoutEngine.normalizedSections(sections)
        ordered.forEachIndexed { index, section ->
            val y = (section.verticalAnchor - scrollYDoc) * scale
            if (y > height || y < -80f * scale) return@forEachIndexed
            sectionLinePaint.strokeWidth = max(1f, 1.2f * scale)
            canvas.drawLine(pageLeft + 18f * scale, y, pageRight - 18f * scale, y, sectionLinePaint)
            sectionTextPaint.textSize = max(11f * density, 14f * scale)
            canvas.drawText(section.title, pageLeft + 26f * scale, y + 24f * scale, sectionTextPaint)

            if (section.collapsed) {
                val end = if (index < ordered.lastIndex) ordered[index + 1].verticalAnchor else workingDocument.heightDp
                val topPx = (section.verticalAnchor - scrollYDoc + 34f) * scale
                val bottomPx = (end - scrollYDoc) * scale
                if (bottomPx > 0f && topPx < height) {
                    canvas.drawRect(pageLeft, max(0f, topPx), pageRight, min(height.toFloat(), bottomPx), collapsedSectionPaint)
                    sectionTextPaint.textSize = max(10f * density, 12f * scale)
                    canvas.drawText("Collapsed — open from Sections", pageLeft + 26f * scale, max(48f, topPx + 26f * scale), sectionTextPaint)
                }
            }
        }
    }

    private fun screenToDocument(xPx: Float, yPx: Float): PointF {
        val scale = effectiveScale()
        val pageLeft = pageLeftPx(scale)
        return PointF(
            scrollXDoc + (xPx - pageLeft) / scale,
            scrollYDoc + yPx / scale
        )
    }

    private fun firstStylusPointerIndex(event: MotionEvent): Int {
        for (i in 0 until event.pointerCount) {
            val type = event.getToolType(i)
            if (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER) return i
        }
        return -1
    }

    private fun isStylusActive(): Boolean = stylusPointerId != MotionEvent.INVALID_POINTER_ID

    private fun fitScale(): Float {
        if (width <= 0) return density
        val horizontalMargin = 16f * density
        return min(
            density,
            (width - horizontalMargin * 2f).coerceAtLeast(1f) / workingDocument.widthDp
        ).coerceAtLeast(0.15f)
    }

    private fun effectiveScale(): Float = fitScale() * userScale

    private fun pageLeftPx(scale: Float): Float {
        val pageWidthPx = workingDocument.widthDp * scale
        return ((width - pageWidthPx) * 0.5f).coerceAtMost(16f * density)
    }

    private fun maxScrollYDoc(): Float {
        val visibleDocHeight = if (height > 0) height / effectiveScale() else 0f
        return max(0f, workingDocument.heightDp - visibleDocHeight)
    }

    private fun maxScrollXDoc(): Float {
        val visibleWidth = if (width > 0) width / effectiveScale() else workingDocument.widthDp
        return max(0f, workingDocument.widthDp - visibleWidth)
    }

    private fun clampViewport() {
        scrollYDoc = scrollYDoc.coerceIn(0f, maxScrollYDoc())
        scrollXDoc = scrollXDoc.coerceIn(0f, maxScrollXDoc())
    }

    private fun maybeExtendDocument() {
        val visibleBottom = scrollYDoc + if (height > 0) height / effectiveScale() else 0f
        if (visibleBottom > workingDocument.heightDp - 420f) {
            workingDocument = workingDocument.copy(heightDp = workingDocument.heightDp + 1200f)
            document = workingDocument
            onDocumentMetaChanged?.invoke(workingDocument)
        }
    }

    private fun maybeExtendForPoint(point: InkPoint) {
        if (point.y > workingDocument.heightDp - 360f) {
            workingDocument = workingDocument.copy(heightDp = workingDocument.heightDp + 1200f)
        }
    }

    private fun rebuildSpatialIndex() {
        itemsById.clear()
        workingDocument.items.forEach { itemsById[it.id] = it }
        spatialIndex.rebuild(workingDocument.items)
    }

    /** Called on Android memory pressure; vector data remains intact and local bitmaps can reload. */
    fun trimCaches() {
        attachmentBitmapCache.clear()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        InkSurfaceMemoryRegistry.register(this)
    }

    override fun onDetachedFromWindow() {
        InkSurfaceMemoryRegistry.unregister(this)
        attachmentBitmapCache.clear()
        super.onDetachedFromWindow()
    }

    private fun rebuildStrokeBounds() {
        strokeBounds.clear()
        workingDocument.items.filterIsInstance<StrokeItem>().forEach { stroke ->
            strokeBounds[stroke.id] = boundsFor(stroke)
        }
    }

    private fun rebuildAttachmentBounds() {
        attachmentBounds.clear()
        workingDocument.items.filterIsInstance<AttachmentItem>().forEach { item ->
            attachmentBounds[item.id] = boundsForAttachment(item)
        }
    }

    private fun rebuildTextBounds() {
        textBounds.clear()
        workingDocument.items.filterIsInstance<TextItem>().forEach { item ->
            textBounds[item.id] = boundsForText(item)
        }
    }

    private fun boundsForText(item: TextItem): RectF =
        RectF(item.x, item.y, item.x + item.width, item.y + item.height)

    private fun boundsForAttachment(item: AttachmentItem): RectF {
        // Axis-aligned bounds deliberately ignore rotation. This keeps lasso/move hit testing
        // predictable while rotated rendering remains visually accurate.
        return RectF(item.x, item.y, item.x + item.width, item.y + item.height)
    }

    private fun boundsFor(stroke: StrokeItem): RectF {
        val bounds = boundsForPoints(stroke.points)
        val padding = max(2f, stroke.width * 1.5f)
        bounds.inset(-padding, -padding)
        return bounds
    }

    private fun boundsForPoints(points: List<InkPoint>): RectF {
        if (points.isEmpty()) return RectF()
        var left = points.first().x
        var top = points.first().y
        var right = left
        var bottom = top
        points.drop(1).forEach { p ->
            left = min(left, p.x)
            top = min(top, p.y)
            right = max(right, p.x)
            bottom = max(bottom, p.y)
        }
        return RectF(left, top, right, bottom)
    }
}
