package com.tomeofhealing.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.backup.*
import com.tomeofhealing.app.data.PersistentJournalRepository
import com.tomeofhealing.app.ui.components.FantasyBackdrop
import com.tomeofhealing.app.ui.components.FantasyPanel
import com.tomeofhealing.app.ui.theme.VisualPreferences
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    service: BackupService,
    repo: PersistentJournalRepository,
    visuals: VisualPreferences,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var folderName by remember { mutableStateOf(service.folderDisplayName()) }
    var lastName by remember { mutableStateOf(service.lastBackupName) }
    var lastAt by remember { mutableLongStateOf(service.lastBackupAt) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<Uri, BackupInspection>?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            service.rememberFolder(uri)
            folderName = service.folderDisplayName()
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching { service.inspectBackup(uri) }
                    .onSuccess { pendingImport = uri to it }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Unable to read backup.") }
                busy = false
            }
        }
    }

    FantasyBackdrop {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .84f)),
                    title = { Text("Backup & Restore", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FantasyPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Manual full-journal backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Backups include Fields, Topics, Notes, handwriting, history, emblems, themes, and imported image/PDF files.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider()
                        Text("Destination: ${folderName ?: "Not selected"}", style = MaterialTheme.typography.bodyMedium)
                        if (lastName != null) {
                            Text("Last backup: $lastName", style = MaterialTheme.typography.bodySmall)
                            if (lastAt > 0L) Text(formatDate(lastAt), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(enabled = !busy, onClick = { folderPicker.launch(service.configuredFolderUri) }) {
                                Text(if (folderName == null) "Choose folder" else "Change folder")
                            }
                            Button(
                                enabled = !busy && service.configuredFolderUri != null,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching { service.createBackup(repo, visuals) }
                                            .onSuccess { result ->
                                                lastName = result.fileName
                                                lastAt = service.lastBackupAt
                                                val rotation = if (result.deletedOldBackups > 0) " Oldest backup rotated out." else ""
                                                snackbar.showSnackbar("Backup created: ${result.fileName}.$rotation")
                                            }
                                            .onFailure { snackbar.showSnackbar(it.message ?: "Backup failed.") }
                                        busy = false
                                    }
                                }
                            ) { Text("Back Up Now") }
                        }
                        Text("The newest 5 Tome of Healing backups are kept in this folder.", style = MaterialTheme.typography.labelSmall)
                    }
                }

                FantasyPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Import / Restore", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Choose a .tohbackup file. You can replace this journal or merge it with the current one.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(enabled = !busy, onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                            Text("Import Backup")
                        }
                        Text(
                            "Merge conflicts can keep both copies, replace existing items, or keep the newest modified item.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Working…")
                    }
                }
            }
        }
    }

    pendingImport?.let { (uri, inspection) ->
        ImportOptionsDialog(
            inspection = inspection,
            onDismiss = { pendingImport = null },
            onImport = { mode, policy, restoreAppearance ->
                pendingImport = null
                busy = true
                scope.launch {
                    runCatching {
                        service.importBackup(uri, repo, visuals, mode, policy, restoreAppearance)
                    }.onSuccess { result ->
                        val conflictText = if (result.remappedConflicts > 0) " ${result.remappedConflicts} ID conflicts were kept as separate copies." else ""
                        snackbar.showSnackbar(
                            "Imported ${result.importedNodes} Fields/Topics and ${result.importedNotes} Notes.$conflictText"
                        )
                    }.onFailure {
                        snackbar.showSnackbar(it.message ?: "Import failed.")
                    }
                    busy = false
                }
            }
        )
    }
}

@Composable
private fun ImportOptionsDialog(
    inspection: BackupInspection,
    onDismiss: () -> Unit,
    onImport: (ImportMode, ConflictPolicy, Boolean) -> Unit
) {
    var mode by remember { mutableStateOf(ImportMode.REPLACE) }
    var policy by remember { mutableStateOf(ConflictPolicy.KEEP_BOTH) }
    var restoreAppearance by remember { mutableStateOf(true) }
    val m = inspection.manifest

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Tome of Healing backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Created: ${formatDate(m.createdAt)}")
                Text("${m.fieldCount} Fields • ${m.topicCount} Topics • ${m.noteCount} Notes • ${m.versionCount} history versions")
                if (m.attachmentFileCount > 0) Text("${m.attachmentFileCount} attachment files")
                HorizontalDivider()
                Text("Import mode", style = MaterialTheme.typography.titleSmall)
                RadioChoice("Replace current journal", mode == ImportMode.REPLACE) {
                    mode = ImportMode.REPLACE
                    restoreAppearance = true
                }
                RadioChoice("Merge with current journal", mode == ImportMode.MERGE) {
                    mode = ImportMode.MERGE
                    restoreAppearance = false
                }
                if (mode == ImportMode.MERGE) {
                    Text("When the same internal item ID exists in both journals:", style = MaterialTheme.typography.labelMedium)
                    RadioChoice("Keep both", policy == ConflictPolicy.KEEP_BOTH) { policy = ConflictPolicy.KEEP_BOTH }
                    RadioChoice("Replace existing", policy == ConflictPolicy.REPLACE_EXISTING) { policy = ConflictPolicy.REPLACE_EXISTING }
                    RadioChoice("Keep newest", policy == ConflictPolicy.KEEP_NEWEST) { policy = ConflictPolicy.KEEP_NEWEST }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = restoreAppearance, onCheckedChange = { restoreAppearance = it })
                    Text("Restore theme and library layout")
                }
                if (mode == ImportMode.REPLACE) {
                    Text("Replace removes the current journal only after the selected backup has been validated.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onImport(mode, policy, restoreAppearance) }) { Text(if (mode == ImportMode.REPLACE) "Restore" else "Merge") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RadioChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

private fun formatDate(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
