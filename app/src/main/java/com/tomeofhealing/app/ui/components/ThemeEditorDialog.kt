package com.tomeofhealing.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.ui.theme.TomeThemePresets
import com.tomeofhealing.app.ui.theme.TomeVisualTheme

@Composable
fun ThemeEditorDialog(
    initial: TomeVisualTheme,
    onDismiss: () -> Unit,
    onSave: (TomeVisualTheme) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    var materialOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme Workshop") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Presets", style = MaterialTheme.typography.titleSmall) }
                TomeThemePresets.all.forEach { preset ->
                    item {
                        FantasyPanel(Modifier.fillMaxWidth().clickable { value = preset }) {
                            Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(preset.name)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ThemeSwatch(preset.primary); ThemeSwatch(preset.secondary); ThemeSwatch(preset.accent)
                                }
                            }
                        }
                    }
                }
                item { RuneDivider(Modifier.padding(vertical = 2.dp)) }
                item { Text("Advanced customization", style = MaterialTheme.typography.titleSmall) }
                item { ThemeHexField("Moon silver / primary", value.primary) { value = value.copy(name = "Custom Theme", primary = it) } }
                item { ThemeHexField("Deep / secondary", value.secondary) { value = value.copy(name = "Custom Theme", secondary = it) } }
                item { ThemeHexField("Accent", value.accent) { value = value.copy(name = "Custom Theme", accent = it) } }
                item { ThemeHexField("Background", value.background) { value = value.copy(name = "Custom Theme", background = it) } }
                item { ThemeHexField("Panel surface", value.surface) { value = value.copy(name = "Custom Theme", surface = it) } }
                item {
                    Box {
                        OutlinedButton(onClick = { materialOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Material: ${value.material.replace('_', ' ')}") }
                        DropdownMenu(expanded = materialOpen, onDismissRequest = { materialOpen = false }) {
                            listOf("moon_stone", "dark_iron", "aged_wood", "arcane_glass", "parchment").forEach { material ->
                                DropdownMenuItem(text = { Text(material.replace('_', ' ')) }, onClick = { value = value.copy(name = "Custom Theme", material = material); materialOpen = false })
                            }
                        }
                    }
                }
                item { Text("Texture ${(value.textureStrength * 100).toInt()}%", style = MaterialTheme.typography.labelMedium); Slider(value.textureStrength, { value = value.copy(name = "Custom Theme", textureStrength = it) }, valueRange = 0f..0.6f) }
                item { Text("Ambient glow ${(value.glowStrength * 100).toInt()}%", style = MaterialTheme.typography.labelMedium); Slider(value.glowStrength, { value = value.copy(name = "Custom Theme", glowStrength = it) }, valueRange = 0f..0.6f) }
                item { Text("Animation intensity ${(value.animationScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium); Slider(value.animationScale, { value = value.copy(name = "Custom Theme", animationScale = it) }, valueRange = 0f..1f) }
            }
        },
        confirmButton = { Button(onClick = { onSave(value) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ThemeSwatch(argb: Long) {
    Surface(color = Color(argb), modifier = Modifier.size(16.dp), shape = MaterialTheme.shapes.extraSmall) {}
}

@Composable
private fun ThemeHexField(label: String, argb: Long, onValid: (Long) -> Unit) {
    var text by remember(argb) { mutableStateOf("%08X".format(argb)) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
            if (text.length == 8) runCatching { text.toLong(16) }.getOrNull()?.let(onValid)
        },
        label = { Text("$label (AARRGGBB)") },
        singleLine = true,
        leadingIcon = { ThemeSwatch(argb) },
        modifier = Modifier.fillMaxWidth()
    )
}
