package com.tomeofhealing.app.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class TomeVisualTheme(
    val name: String,
    val primary: Long,
    val secondary: Long,
    val accent: Long,
    val background: Long,
    val surface: Long,
    val parchment: Long,
    val ink: Long,
    val material: String = "moon_stone",
    val textureStrength: Float = 0.22f,
    val glowStrength: Float = 0.22f,
    val animationScale: Float = 0.55f
)

enum class LibraryLayoutMode { GRID, LIST, TOME, COMMAND_PANEL }

object TomeThemePresets {
    val MoonlitArchive = TomeVisualTheme(
        name = "Moonlit Archive",
        primary = 0xFFC9D0DAL,
        secondary = 0xFF2B114AL,
        accent = 0xFF102B59L,
        background = 0xFF090D18L,
        surface = 0xFF14192AL,
        parchment = 0xFFE8E0D2L,
        ink = 0xFF201A28L,
        material = "moon_stone"
    )
    val EmeraldHealer = TomeVisualTheme(
        name = "Emerald Healer",
        primary = 0xFFD6D9DCL,
        secondary = 0xFF123F35L,
        accent = 0xFF2B735EL,
        background = 0xFF08120FL,
        surface = 0xFF10221DL,
        parchment = 0xFFE8E3D5L,
        ink = 0xFF1F2A25L,
        material = "aged_wood"
    )
    val CrimsonCitadel = TomeVisualTheme(
        name = "Crimson Citadel",
        primary = 0xFFD4C6B6L,
        secondary = 0xFF5B1721L,
        accent = 0xFF8C552EL,
        background = 0xFF120A0CL,
        surface = 0xFF251318L,
        parchment = 0xFFE5D7C7L,
        ink = 0xFF2A1717L,
        material = "dark_iron"
    )
    val ArcaneViolet = TomeVisualTheme(
        name = "Arcane Violet",
        primary = 0xFFD9D0EAL,
        secondary = 0xFF4A1E68L,
        accent = 0xFF263F78L,
        background = 0xFF0D0915L,
        surface = 0xFF1B1228L,
        parchment = 0xFFE8E0ECL,
        ink = 0xFF231A2BL,
        material = "arcane_glass",
        glowStrength = 0.32f
    )
    val AncientParchment = TomeVisualTheme(
        name = "Ancient Parchment",
        primary = 0xFFD9C79EL,
        secondary = 0xFF5A4026L,
        accent = 0xFF705E3EL,
        background = 0xFF17130EL,
        surface = 0xFF2A2319L,
        parchment = 0xFFE6D4AEL,
        ink = 0xFF2B2116L,
        material = "parchment",
        glowStrength = 0.10f,
        animationScale = 0.35f
    )

    val all = listOf(MoonlitArchive, EmeraldHealer, CrimsonCitadel, ArcaneViolet, AncientParchment)
}

class VisualPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tome_visual_preferences", Context.MODE_PRIVATE)

    var theme by mutableStateOf(loadTheme())
        private set

    var libraryLayout by mutableStateOf(
        runCatching { LibraryLayoutMode.valueOf(prefs.getString("library_layout", LibraryLayoutMode.GRID.name)!!) }
            .getOrDefault(LibraryLayoutMode.GRID)
    )
        private set

    fun saveTheme(value: TomeVisualTheme) {
        theme = value
        prefs.edit()
            .putString("theme_name", value.name)
            .putLong("theme_primary", value.primary)
            .putLong("theme_secondary", value.secondary)
            .putLong("theme_accent", value.accent)
            .putLong("theme_background", value.background)
            .putLong("theme_surface", value.surface)
            .putLong("theme_parchment", value.parchment)
            .putLong("theme_ink", value.ink)
            .putString("theme_material", value.material)
            .putFloat("theme_texture", value.textureStrength)
            .putFloat("theme_glow", value.glowStrength)
            .putFloat("theme_animation", value.animationScale)
            .apply()
    }

    fun saveLibraryLayout(value: LibraryLayoutMode) {
        libraryLayout = value
        prefs.edit().putString("library_layout", value.name).apply()
    }

    private fun loadTheme(): TomeVisualTheme {
        val d = TomeThemePresets.MoonlitArchive
        return TomeVisualTheme(
            name = prefs.getString("theme_name", d.name) ?: d.name,
            primary = prefs.getLong("theme_primary", d.primary),
            secondary = prefs.getLong("theme_secondary", d.secondary),
            accent = prefs.getLong("theme_accent", d.accent),
            background = prefs.getLong("theme_background", d.background),
            surface = prefs.getLong("theme_surface", d.surface),
            parchment = prefs.getLong("theme_parchment", d.parchment),
            ink = prefs.getLong("theme_ink", d.ink),
            material = prefs.getString("theme_material", d.material) ?: d.material,
            textureStrength = prefs.getFloat("theme_texture", d.textureStrength),
            glowStrength = prefs.getFloat("theme_glow", d.glowStrength),
            animationScale = prefs.getFloat("theme_animation", d.animationScale)
        )
    }
}

val LocalTomeVisualTheme = staticCompositionLocalOf { TomeThemePresets.MoonlitArchive }

private fun typography() = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 42.sp, letterSpacing = 1.2.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 0.6.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.3.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp)
)

@Composable
fun TomeTheme(theme: TomeVisualTheme = TomeThemePresets.MoonlitArchive, content: @Composable () -> Unit) {
    val primary = Color(theme.primary)
    val secondary = Color(theme.secondary)
    val accent = Color(theme.accent)
    val background = Color(theme.background)
    val surface = Color(theme.surface)

    val scheme = darkColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = accent,
        background = background,
        surface = surface,
        surfaceVariant = surface.copy(alpha = 0.88f),
        onPrimary = Color(0xFF111522),
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color(0xFFE8EAF0),
        onSurface = Color(0xFFE8EAF0),
        outline = primary.copy(alpha = 0.55f)
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalTomeVisualTheme provides theme) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
