package com.tomeofhealing.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.model.EmblemDefinition

private val bases = listOf("open_tome", "shield", "circle", "hex", "rune_plate", "medallion")
private val centralSymbols = listOf("rod_of_asclepius", "heart", "brain", "eye", "lungs", "bone", "flask", "cross", "skull")
private val secondarySymbols = listOf("arcane_halo", "laurel", "wings", "none")
private val frames = listOf("silver_filigree", "rune_metal", "plain", "none")
private val emblemPalette = listOf(0xFF2B114AL, 0xFFC9D0DAL, 0xFF102B59L, 0xFF174A3FL, 0xFF7A2638L, 0xFF765D2CL, 0xFF362A66L, 0xFF151A26L)

@Composable
fun EmblemEditorDialog(
    title: String,
    initial: EmblemDefinition,
    inheritedFrom: EmblemDefinition? = null,
    onDismiss: () -> Unit,
    onSave: (EmblemDefinition) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emblem Maker — $title") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmblemView(value, size = 124.dp, animate = true)
                }
                Text("Base", style = MaterialTheme.typography.titleSmall)
                OptionStrip(bases, value.base) { value = value.copy(base = it) }
                Text("Central symbol", style = MaterialTheme.typography.titleSmall)
                OptionStrip(centralSymbols, value.centralSymbol) { value = value.copy(centralSymbol = it) }
                Text("Secondary motif", style = MaterialTheme.typography.titleSmall)
                OptionStrip(secondarySymbols, value.secondarySymbol) { value = value.copy(secondarySymbol = it) }
                Text("Frame", style = MaterialTheme.typography.titleSmall)
                OptionStrip(frames, value.frame) { value = value.copy(frame = it) }
                ColorRow("Primary", value.primaryColor) { value = value.copy(primaryColor = it) }
                ColorRow("Moon/metal", value.secondaryColor) { value = value.copy(secondaryColor = it) }
                ColorRow("Accent", value.accentColor) { value = value.copy(accentColor = it) }
                Text("Glow ${(value.glow * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = value.glow, onValueChange = { value = value.copy(glow = it) }, valueRange = 0f..0.65f)
                OutlinedTextField(value = value.banner.orEmpty(), onValueChange = { value = value.copy(banner = it.take(16).ifBlank { null }) }, label = { Text("Banner (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = value.initials.orEmpty(), onValueChange = { value = value.copy(initials = it.take(3).ifBlank { null }) }, label = { Text("Initials (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                inheritedFrom?.let { parent ->
                    OutlinedButton(onClick = { value = parent }, modifier = Modifier.fillMaxWidth()) { Text("Reset to parent emblem") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(value) }) { Text("Save emblem") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun OptionStrip(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option.replace('_', ' ').replaceFirstChar { it.uppercase() }) })
        }
    }
}

@Composable
private fun ColorRow(label: String, selected: Long, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            emblemPalette.forEach { argb ->
                Surface(
                    color = Color(argb),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(if (selected == argb) 34.dp else 30.dp).clickable { onSelect(argb) },
                    border = if (selected == argb) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {}
            }
        }
    }
}
