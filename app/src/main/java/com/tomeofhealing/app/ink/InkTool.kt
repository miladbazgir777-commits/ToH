package com.tomeofhealing.app.ink

import androidx.compose.ui.graphics.Color

enum class InkTool(val label: String) {
    PEN("Pen"),
    FOUNTAIN("Fountain"),
    BALLPOINT("Ballpoint"),
    PENCIL("Pencil"),
    MARKER("Marker"),
    HIGHLIGHTER("Highlighter"),
    RULER("Ruler"),
    SHAPE("Shape"),
    ERASER("Eraser"),
    LASSO("Lasso")
}

enum class ToolbarDock {
    LEFT,
    RIGHT,
    BOTTOM
}

data class InkToolConfig(
    val tool: InkTool = InkTool.PEN,
    val color: Color = Color(0xFF182033),
    val penWidthDp: Float = 3.2f,
    val highlighterWidthDp: Float = 16f,
    val eraserRadiusDp: Float = 18f,
    val pressureEnabled: Boolean = true
)

val MedicalInkPalette = listOf(
    Color(0xFF182033), // charcoal
    Color(0xFF173E74), // clinical blue
    Color(0xFF8E2430), // red
    Color(0xFF25643D), // green
    Color(0xFF5A3475), // purple
    Color(0xFFB96C22), // orange
    Color(0xFF5F6570)  // gray
)
