package com.tomeofhealing.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.model.ChecklistEntry
import com.tomeofhealing.app.model.TextItem
import com.tomeofhealing.app.model.TextObjectKind
import java.util.UUID

data class InternalLinkOption(val label: String, val target: String, val subtitle: String = "")

@Composable
fun InsertCanvasContentDialog(
    onDismiss: () -> Unit,
    onImage: () -> Unit,
    onCamera: () -> Unit,
    onPdf: () -> Unit,
    onText: () -> Unit,
    onLabel: () -> Unit,
    onMedication: () -> Unit,
    onTable: () -> Unit,
    onChecklist: () -> Unit,
    onWebLink: () -> Unit,
    onInternalLink: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert into note") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Text("Reference media", style = MaterialTheme.typography.labelLarge) }
                item { InsertRow("Image from gallery/files", onImage) }
                item { InsertRow("Camera photo", onCamera) }
                item { InsertRow("PDF page or excerpt", onPdf) }
                item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                item { Text("Typed content", style = MaterialTheme.typography.labelLarge) }
                item { InsertRow("Text box", onText) }
                item { InsertRow("Label / callout", onLabel) }
                item { InsertRow("Medication / dose block", onMedication) }
                item { InsertRow("Small table", onTable) }
                item { InsertRow("Checklist", onChecklist) }
                item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                item { Text("Links", style = MaterialTheme.typography.labelLarge) }
                item { InsertRow("Web link", onWebLink) }
                item { InsertRow("Field / Topic / Note link", onInternalLink) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun InsertRow(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        tonalElevation = 1.dp
    ) { Text(label, modifier = Modifier.padding(12.dp)) }
}

@Composable
fun TextBlockDialog(
    kind: TextObjectKind,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    val title = if (kind == TextObjectKind.LABEL) "Label / callout" else "Text box"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(if (kind == TextObjectKind.LABEL) "Label" else "Text") },
                minLines = if (kind == TextObjectKind.LABEL) 1 else 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MedicationBlockDialog(
    initial: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial["name"].orEmpty()) }
    var dose by remember(initial) { mutableStateOf(initial["dose"].orEmpty()) }
    var route by remember(initial) { mutableStateOf(initial["route"].orEmpty()) }
    var frequency by remember(initial) { mutableStateOf(initial["frequency"].orEmpty()) }
    var duration by remember(initial) { mutableStateOf(initial["duration"].orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial["note"].orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Medication / dose block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Medication") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(dose, { dose = it }, label = { Text("Dose") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(route, { route = it }, label = { Text("Route") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(frequency, { frequency = it }, label = { Text("Frequency") }, singleLine = true)
                OutlinedTextField(duration, { duration = it }, label = { Text("Duration") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, minLines = 2, maxLines = 4)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() || dose.isNotBlank(),
                onClick = {
                    onConfirm(mapOf("name" to name.trim(), "dose" to dose.trim(), "route" to route.trim(), "frequency" to frequency.trim(), "duration" to duration.trim(), "note" to note.trim()))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TableBlockDialog(
    initialRows: List<List<String>> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (List<List<String>>) -> Unit
) {
    val initial = remember(initialRows) { initialRows.joinToString("\n") { it.joinToString(" | ") } }
    var raw by remember(initial) { mutableStateOf(initial.ifBlank { "Column 1 | Column 2\n | " }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Small table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("One row per line; separate cells with |", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(raw, { raw = it }, minLines = 5, maxLines = 12, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rows = raw.lines().filter { it.isNotBlank() }.map { line -> line.split('|').map { it.trim() } }
                if (rows.isNotEmpty()) onConfirm(rows)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ChecklistBlockDialog(
    initialEntries: List<ChecklistEntry> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (List<ChecklistEntry>) -> Unit
) {
    val initial = remember(initialEntries) { initialEntries.joinToString("\n") { it.text } }
    var raw by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Checklist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("One item per line", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(raw, { raw = it }, minLines = 5, maxLines = 12, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val oldByText = initialEntries.associateBy { it.text }
                val entries = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { text ->
                    oldByText[text] ?: ChecklistEntry(UUID.randomUUID().toString(), text)
                }
                if (entries.isNotEmpty()) onConfirm(entries)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun WebLinkDialog(
    initialLabel: String = "",
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Web link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("https://…") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = {
                val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url.trim() else "https://${url.trim()}"
                onConfirm(label.trim().ifBlank { normalized }, normalized)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun InternalLinkPickerDialog(
    options: List<InternalLinkOption>,
    currentTarget: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (InternalLinkOption) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        if (query.isBlank()) options else options.filter { it.label.contains(query, true) || it.subtitle.contains(query, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link to journal item") },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Find by title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(filtered, key = { it.target }) { option ->
                        ListItem(
                            headlineContent = { Text(option.label) },
                            supportingContent = { if (option.subtitle.isNotBlank()) Text(option.subtitle) },
                            trailingContent = { if (option.target == currentTarget) Text("Current") },
                            modifier = Modifier.clickable { onConfirm(option) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun TextObjectInspectorDialog(
    item: TextItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onScale: (Float) -> Unit,
    onToggleLock: () -> Unit,
    onBringForward: () -> Unit,
    onSendBackward: () -> Unit,
    onJump: () -> Unit,
    onOpenLink: (() -> Unit)?,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.kind.name.lowercase().replaceFirstChar { it.uppercase() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.text.takeIf { it.isNotBlank() }?.let { Text(it.take(180)) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = onEdit, label = { Text("Edit") })
                    AssistChip(onClick = { onScale(0.9f) }, label = { Text("Smaller") })
                    AssistChip(onClick = { onScale(1.1f) }, label = { Text("Larger") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = onToggleLock, label = { Text(if (item.locked) "Unlock" else "Lock") })
                    AssistChip(onClick = onBringForward, label = { Text("Forward") })
                    AssistChip(onClick = onSendBackward, label = { Text("Backward") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = onJump, label = { Text("Jump") })
                    onOpenLink?.let { open -> AssistChip(onClick = open, label = { Text("Open link") }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete") } }
    )
}

@Composable
fun TextObjectListDialog(
    objects: List<TextItem>,
    onDismiss: () -> Unit,
    onJump: (TextItem) -> Unit,
    onEdit: (TextItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Typed content") },
        text = {
            if (objects.isEmpty()) {
                Text("No typed content objects in this note.")
            } else {
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    items(objects, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.text.ifBlank { item.kind.name.lowercase().replaceFirstChar { it.uppercase() } }.take(80)) },
                            supportingContent = { Text(item.kind.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.clickable { onJump(item) },
                            trailingContent = { TextButton(onClick = { onEdit(item) }) { Text("Edit") } }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ContentSearchDialog(
    objects: List<TextItem>,
    onDismiss: () -> Unit,
    onJump: (TextItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    fun searchable(item: TextItem): String = buildString {
        append(item.text).append(' ')
        append(item.metadata.values.joinToString(" ")).append(' ')
        append(item.tableRows.flatten().joinToString(" ")).append(' ')
        append(item.checklist.joinToString(" ") { it.text })
    }
    val results = remember(query, objects) {
        if (query.isBlank()) emptyList() else objects.filter { searchable(it).contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find typed content") },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search this note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (query.isNotBlank() && results.isEmpty()) {
                    Text("No typed-content matches. Handwriting is intentionally not OCR-searched.")
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(results, key = { it.id }) { item ->
                            ListItem(
                                headlineContent = { Text(item.text.ifBlank { item.metadata["name"].orEmpty().ifBlank { item.kind.name } }.take(100)) },
                                supportingContent = { Text(item.kind.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.clickable { onJump(item) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
