package com.tomeofhealing.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.model.*

@Composable
fun CreateNodeDialog(
    type: NodeType,
    onDismiss: () -> Unit,
    onCreate: (title: String, template: NoteTemplate) -> Unit
) {
    var title by remember(type) { mutableStateOf("") }
    var template by remember(type) { mutableStateOf(NoteTemplate.DISEASE) }
    var templateOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create ${if (type == NodeType.FIELD) "Field" else "Topic"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == NodeType.TOPIC) {
                    Box {
                        OutlinedButton(onClick = { templateOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Master Summary template: ${template.label()}")
                        }
                        DropdownMenu(expanded = templateOpen, onDismissRequest = { templateOpen = false }) {
                            NoteTemplate.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label()) },
                                    onClick = {
                                        template = option
                                        templateOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onCreate(title.trim(), template) }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Note") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Note title") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = { onCreate(title.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RenameDialog(
    currentTitle: String,
    allowRename: Boolean = true,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (allowRename) "Rename" else "Protected title") },
        text = {
            if (allowRename) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("The Master Summary title is fixed so every Topic always has a predictable primary note.")
            }
        },
        confirmButton = {
            if (allowRename) {
                Button(enabled = title.isNotBlank(), onClick = { onRename(title.trim()) }) { Text("Rename") }
            } else {
                Button(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (allowRename) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $title?") },
        text = { Text(description) },
        confirmButton = { Button(onClick = onConfirm) { Text("Move to Trash") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SortDialog(
    title: String,
    selected: SortMode,
    onDismiss: () -> Unit,
    onSelect: (SortMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                        Spacer(Modifier.width(8.dp))
                        Text(mode.label())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun TopicSortDialog(
    childSort: SortMode,
    noteSort: SortMode,
    onDismiss: () -> Unit,
    onChildSort: (SortMode) -> Unit,
    onNoteSort: (SortMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort Topic") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                item { Text("Subtopics", style = MaterialTheme.typography.titleSmall) }
                items(SortMode.entries) { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChildSort(mode) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = childSort == mode, onClick = { onChildSort(mode) })
                        Text(mode.label())
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text("Notes", style = MaterialTheme.typography.titleSmall)
                }
                items(SortMode.entries) { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onNoteSort(mode) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = noteSort == mode, onClick = { onNoteSort(mode) })
                        Text(mode.label())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun NodeActionDialog(
    node: JournalNode,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onEditEmblem: () -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onPin: () -> Unit,
    onToggleDeletionLock: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(node.title) },
        text = {
            Column {
                ActionRow("Open", onOpen)
                ActionRow("Rename", onRename)
                ActionRow("Edit emblem", onEditEmblem)
                ActionRow("Move", onMove)
                ActionRow("Duplicate", onDuplicate)
                ActionRow(if (node.pinned) "Unpin" else "Pin", onPin)
                ActionRow(if (node.deletionLocked) "Unlock deletion" else "Lock against deletion", onToggleDeletionLock)
                ActionRow("Delete", onDelete, enabled = !node.deletionLocked)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Created: ${formatTimestamp(node.createdAt)}", style = MaterialTheme.typography.bodySmall)
                Text("Modified: ${formatTimestamp(node.modifiedAt)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun NoteActionDialog(
    note: JournalNote,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onDuplicate: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.title) },
        text = {
            Column {
                ActionRow("Open", onOpen)
                ActionRow("Rename", onRename, enabled = !note.isMasterSummary)
                ActionRow("Move earlier", onMoveEarlier, enabled = !note.isMasterSummary)
                ActionRow("Move later", onMoveLater, enabled = !note.isMasterSummary)
                ActionRow("Duplicate", onDuplicate)
                ActionRow(if (note.pinned) "Unpin" else "Pin", onPin, enabled = !note.isMasterSummary)
                ActionRow("Delete", onDelete, enabled = !note.isMasterSummary)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Created: ${formatTimestamp(note.createdAt)}", style = MaterialTheme.typography.bodySmall)
                Text("Modified: ${formatTimestamp(note.modifiedAt)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
    ) {
        Box(Modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
fun MoveNodeDialog(
    node: JournalNode,
    eligibleParents: List<JournalNode>,
    pathFor: (String) -> String,
    onDismiss: () -> Unit,
    onMoveTo: (String) -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${node.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Manual order", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onMoveEarlier) { Text("Earlier") }
                    OutlinedButton(onClick = onMoveLater) { Text("Later") }
                }
                if (node.type == NodeType.TOPIC) {
                    HorizontalDivider()
                    Text("Move under another Field or Topic", style = MaterialTheme.typography.titleSmall)
                    if (eligibleParents.isEmpty()) {
                        Text("No valid destinations.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(eligibleParents) { parent ->
                                TextButton(
                                    onClick = { onMoveTo(parent.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(Modifier.fillMaxWidth()) { Text(pathFor(parent.id)) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
