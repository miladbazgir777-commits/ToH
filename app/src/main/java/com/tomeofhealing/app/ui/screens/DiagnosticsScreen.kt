package com.tomeofhealing.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.BuildConfig
import com.tomeofhealing.app.data.PersistentJournalRepository
import com.tomeofhealing.app.device.StylusCapabilities
import com.tomeofhealing.app.recovery.CrashRecoveryManager
import com.tomeofhealing.app.ui.components.FantasyBackdrop
import com.tomeofhealing.app.ui.components.FantasyPanel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    repo: PersistentJournalRepository,
    recovery: CrashRecoveryManager,
    stylus: StylusCapabilities,
    onBack: () -> Unit
) {
    var integrity by remember { mutableStateOf("Not checked") }
    var crashText by remember { mutableStateOf(recovery.lastCrashText()) }
    val nodes by repo.nodes.collectAsState()
    val notes by repo.notes.collectAsState()
    val versions by repo.versions.collectAsState()

    FantasyBackdrop {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .84f)),
                    title = { Text("Diagnostics", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
                )
            }
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FantasyPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("App", style = MaterialTheme.typography.titleMedium)
                        Text("Tome of Healing ${BuildConfig.VERSION_NAME}")
                        Text("Android API ${android.os.Build.VERSION.SDK_INT}")
                        Text("${stylus.manufacturer} ${stylus.model}")
                        Text(if (stylus.enhancedStylusMode) "Enhanced stylus mode available" else "Generic Android stylus mode")
                    }
                }

                FantasyPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Journal storage", style = MaterialTheme.typography.titleMedium)
                        Text("${nodes.size} Fields/Topics • ${notes.size} Notes • ${versions.size} history versions")
                        Text("Database: ${formatBytes(repo.databaseSizeBytes())}")
                        Text("Attachments: ${formatBytes(repo.attachmentSizeBytes())}")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { integrity = repo.storageIntegrity() }) { Text("Run database check") }
                            Text(integrity, modifier = Modifier.padding(top = 12.dp))
                        }
                        Text("Database checks are local. No diagnostic information is uploaded.", style = MaterialTheme.typography.labelSmall)
                    }
                }

                FantasyPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Crash recovery", style = MaterialTheme.typography.titleMedium)
                        if (crashText.isNullOrBlank()) {
                            Text("No local crash report is stored.")
                        } else {
                            Text(crashText!!.lineSequence().take(9).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                recovery.clearCrashReport()
                                crashText = null
                            }) { Text("Clear crash report") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit.coerceAtLeast(0)])
}
