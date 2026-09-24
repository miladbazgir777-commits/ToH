package com.tomeofhealing.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.model.NoteVersion

@Composable
fun VersionHistoryDialog(
    noteTitle: String,
    versions: List<NoteVersion>,
    onDismiss: () -> Unit,
    onCreateCheckpoint: () -> Unit,
    onRestore: (NoteVersion) -> Unit,
    onDelete: (NoteVersion) -> Unit
) {
    var restoreCandidate by remember { mutableStateOf<NoteVersion?>(null) }
    var deleteCandidate by remember { mutableStateOf<NoteVersion?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Version history — $noteTitle") },
        text = {
            Column(Modifier.widthIn(min = 420.dp, max = 680.dp)) {
                Text(
                    "Up to the latest 10 versions are kept. Automatic checkpoints are rate-limited while you write.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onCreateCheckpoint, modifier = Modifier.fillMaxWidth()) {
                    Text("Create checkpoint now")
                }
                Spacer(Modifier.height(8.dp))
                if (versions.isEmpty()) {
                    Text("No saved versions yet.", modifier = Modifier.padding(vertical = 20.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(versions, key = { it.id }) { version ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(version.label, style = MaterialTheme.typography.titleSmall)
                                    Text("Checkpoint: ${formatTimestamp(version.createdAt)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Source modified: ${formatTimestamp(version.sourceModifiedAt)}", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "${version.sections.size} sections • ${version.canvas.items.size} canvas objects",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { restoreCandidate = version }) { Text("Restore") }
                                        TextButton(onClick = { deleteCandidate = version }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    restoreCandidate?.let { version ->
        AlertDialog(
            onDismissRequest = { restoreCandidate = null },
            title = { Text("Restore this version?") },
            text = { Text("Your current note will be checkpointed first, then this version will become the active note content.") },
            dismissButton = { TextButton(onClick = { restoreCandidate = null }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = { onRestore(version); restoreCandidate = null }) { Text("Restore") }
            }
        )
    }

    deleteCandidate?.let { version ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete checkpoint?") },
            text = { Text("This removes only this historical checkpoint. The current note is not changed.") },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = { onDelete(version); deleteCandidate = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
