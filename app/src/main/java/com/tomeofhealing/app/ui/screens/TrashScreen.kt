package com.tomeofhealing.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.model.JournalNode
import com.tomeofhealing.app.model.JournalNote
import com.tomeofhealing.app.ui.components.EmblemView
import com.tomeofhealing.app.ui.components.FantasyBackdrop
import com.tomeofhealing.app.ui.components.FantasyPanel
import com.tomeofhealing.app.ui.components.RuneDivider
import com.tomeofhealing.app.ui.components.formatTimestamp

private sealed interface TrashTarget {
    data class Node(val value: JournalNode) : TrashTarget
    data class Note(val value: JournalNote) : TrashTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    nodes: List<JournalNode>,
    notes: List<JournalNote>,
    pathFor: (String) -> String,
    onBack: () -> Unit,
    onRestoreNode: (JournalNode) -> Unit,
    onRestoreNote: (JournalNote) -> Unit,
    onDeleteNodePermanently: (JournalNode) -> Boolean,
    onDeleteNotePermanently: (JournalNote) -> Boolean,
    onEmptyTrash: () -> Int
) {
    var permanentTarget by remember { mutableStateOf<TrashTarget?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val total = nodes.size + notes.size

    FantasyBackdrop {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .88f)
                    ),
                    title = {
                        Column {
                            Text("Trash", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Items remain here until you permanently delete them.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .72f)
                            )
                        }
                    },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                    actions = {
                        TextButton(onClick = { confirmEmpty = true }, enabled = total > 0) {
                            Text("Empty Trash")
                        }
                    }
                )
            }
        ) { padding ->
            if (total == 0) {
                Box(
                    Modifier.padding(padding).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    FantasyPanel(Modifier.padding(24.dp)) {
                        Text("Trash is empty.", modifier = Modifier.padding(28.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (nodes.isNotEmpty()) {
                        item { Text("Fields & Topics", style = MaterialTheme.typography.titleSmall) }
                        items(nodes, key = { "trash_node_${it.id}" }) { node ->
                            FantasyPanel(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EmblemView(node.emblem, size = 52.dp, animate = false, showBanner = false)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(node.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            pathFor(node.id).ifBlank { node.title },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1
                                        )
                                        node.deletedAt?.let {
                                            Text("Deleted: ${formatTimestamp(it)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        TextButton(onClick = { onRestoreNode(node) }) { Text("Restore") }
                                        TextButton(onClick = { permanentTarget = TrashTarget.Node(node) }) {
                                            Text("Delete forever", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (nodes.isNotEmpty() && notes.isNotEmpty()) item { RuneDivider(Modifier.padding(vertical = 6.dp)) }

                    if (notes.isNotEmpty()) {
                        item { Text("Notes", style = MaterialTheme.typography.titleSmall) }
                        items(notes, key = { "trash_note_${it.id}" }) { note ->
                            FantasyPanel(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(note.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${pathFor(note.topicId)} › ${note.title}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1
                                        )
                                        note.deletedAt?.let {
                                            Text("Deleted: ${formatTimestamp(it)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        TextButton(onClick = { onRestoreNote(note) }) { Text("Restore") }
                                        TextButton(onClick = { permanentTarget = TrashTarget.Note(note) }) {
                                            Text("Delete forever", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    permanentTarget?.let { target ->
        val title = when (target) {
            is TrashTarget.Node -> target.value.title
            is TrashTarget.Note -> target.value.title
        }
        AlertDialog(
            onDismissRequest = { permanentTarget = null },
            title = { Text("Permanently delete $title?") },
            text = {
                Text(
                    when (target) {
                        is TrashTarget.Node -> "This permanently removes the item, every nested Topic/Note, and their saved note versions. This cannot be undone."
                        is TrashTarget.Note -> "This permanently removes the note and its saved versions. This cannot be undone."
                    }
                )
            },
            dismissButton = { TextButton(onClick = { permanentTarget = null }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        when (target) {
                            is TrashTarget.Node -> onDeleteNodePermanently(target.value)
                            is TrashTarget.Note -> onDeleteNotePermanently(target.value)
                        }
                        permanentTarget = null
                    }
                ) { Text("Delete forever") }
            }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Trash?") },
            text = { Text("All trashed Fields, Topics, Notes, and their version history will be permanently erased. This cannot be undone.") },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { onEmptyTrash(); confirmEmpty = false }
                ) { Text("Empty Trash") }
            }
        )
    }
}
