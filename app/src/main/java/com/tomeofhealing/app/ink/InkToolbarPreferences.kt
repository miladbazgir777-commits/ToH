package com.tomeofhealing.app.ink

import android.content.Context

/** Persistent UI preferences for the edge-docked handwriting toolbar. */
class InkToolbarPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ink_toolbar_preferences", Context.MODE_PRIVATE)

    fun loadDock(): ToolbarDock = runCatching {
        ToolbarDock.valueOf(prefs.getString(KEY_DOCK, ToolbarDock.RIGHT.name) ?: ToolbarDock.RIGHT.name)
    }.getOrDefault(ToolbarDock.RIGHT)

    fun saveDock(dock: ToolbarDock) {
        prefs.edit().putString(KEY_DOCK, dock.name).apply()
    }

    fun loadVisibleTools(): List<InkTool> {
        val ordered = prefs.getString(KEY_TOOL_ORDER, null)
            ?.split(",")
            ?.mapNotNull { name -> runCatching { InkTool.valueOf(name) }.getOrNull() }
            ?.distinct()
            .orEmpty()
        if (ordered.isNotEmpty()) return ordered

        // Compatibility with the earlier Set-based preference used during development.
        val legacy = prefs.getStringSet(KEY_VISIBLE_TOOLS_LEGACY, null)
            ?.mapNotNull { name -> runCatching { InkTool.valueOf(name) }.getOrNull() }
            .orEmpty()
        return if (legacy.isNotEmpty()) legacy else defaultTools()
    }

    fun saveVisibleTools(tools: List<InkTool>) {
        val normalized = tools.distinct().ifEmpty { defaultTools() }
        prefs.edit().putString(KEY_TOOL_ORDER, normalized.joinToString(",") { it.name }).apply()
    }

    fun loadFavoriteColors(): List<Int> = prefs.getString(KEY_FAVORITE_COLORS, null)
        ?.split(",")
        ?.mapNotNull { it.toLongOrNull(16)?.toInt() }
        ?.take(8)
        ?: emptyList()

    fun saveFavoriteColors(colors: List<Int>) {
        val encoded = colors.distinct().take(8).joinToString(",") { Integer.toUnsignedString(it, 16) }
        prefs.edit().putString(KEY_FAVORITE_COLORS, encoded).apply()
    }

    private fun defaultTools() = listOf(
        InkTool.PEN,
        InkTool.HIGHLIGHTER,
        InkTool.ERASER,
        InkTool.LASSO
    )

    companion object {
        private const val KEY_DOCK = "dock"
        private const val KEY_TOOL_ORDER = "tool_order"
        private const val KEY_VISIBLE_TOOLS_LEGACY = "visible_tools"
        private const val KEY_FAVORITE_COLORS = "favorite_colors"
    }
}
