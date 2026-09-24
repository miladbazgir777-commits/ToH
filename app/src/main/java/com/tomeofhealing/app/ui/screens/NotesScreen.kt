package com.tomeofhealing.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.model.EmblemDefinition
import com.tomeofhealing.app.model.JournalNode
import com.tomeofhealing.app.model.JournalNote
import com.tomeofhealing.app.model.SortMode
import com.tomeofhealing.app.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    topic: JournalNode,
    subtopics: List<JournalNode>,
    notes: List<JournalNote>,
    childSortMode: SortMode,
    noteSortMode: SortMode,
    onBack: () -> Unit,
    onOpenSubtopic: (JournalNode) -> Unit,
    onOpenNote: (JournalNote) -> Unit,
    onCreateSubtopic: () -> Unit,
    onCreateNote: () -> Unit,
    onRenameNode: (JournalNode, String) -> Unit,
    onDuplicateNode: (JournalNode) -> Unit,
    onPinNode: (JournalNode) -> Unit,
    onToggleDeletionLock: (JournalNode) -> Unit,
    onDeleteNode: (JournalNode) -> Boolean,
    onRestoreNode: (JournalNode) -> Unit,
    onMoveNodeEarlier: (JournalNode) -> Unit,
    onMoveNodeLater: (JournalNode) -> Unit,
    onMoveNodeTo: (JournalNode, String) -> Boolean,
    moveTargets: (JournalNode) -> List<JournalNode>,
    pathFor: (String) -> String,
    onRenameNote: (JournalNote, String) -> Unit,
    onDuplicateNote: (JournalNote) -> Unit,
    onPinNote: (JournalNote) -> Unit,
    onDeleteNote: (JournalNote) -> Boolean,
    onRestoreNote: (JournalNote) -> Unit,
    onMoveNoteEarlier: (JournalNote) -> Unit,
    onMoveNoteLater: (JournalNote) -> Unit,
    onChildSortMode: (SortMode) -> Unit,
    onNoteSortMode: (SortMode) -> Unit,
    onSaveEmblem: (JournalNode, EmblemDefinition) -> Unit,
    parentEmblem: EmblemDefinition?
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSort by remember { mutableStateOf(false) }
    var editTopicEmblem by remember { mutableStateOf(false) }
    var selectedSubtopic by remember { mutableStateOf<JournalNode?>(null) }
    var renameSubtopic by remember { mutableStateOf<JournalNode?>(null) }
    var emblemSubtopic by remember { mutableStateOf<JournalNode?>(null) }
    var moveSubtopic by remember { mutableStateOf<JournalNode?>(null) }
    var deleteSubtopic by remember { mutableStateOf<JournalNode?>(null) }
    var selectedNote by remember { mutableStateOf<JournalNote?>(null) }
    var renameNote by remember { mutableStateOf<JournalNote?>(null) }
    var deleteNote by remember { mutableStateOf<JournalNote?>(null) }

    FantasyBackdrop {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .84f)),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmblemView(topic.emblem, size = 44.dp, animate = topic.pinned, showBanner = false)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(topic.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                Text("Notes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = .72f))
                            }
                        }
                    },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                    actions = {
                        TextButton(onClick = { editTopicEmblem = true }) { Text("Emblem") }
                        TextButton(onClick = onCreateSubtopic) { Text("Subtopic") }
                        TextButton(onClick = { showSort = true }) { Text("Sort") }
                    }
                )
            },
            floatingActionButton = { ExtendedFloatingActionButton(onClick = onCreateNote, text = { Text("New Note") }, icon = { Text("✦") }) }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(padding).fillMaxSize()
            ) {
                if (subtopics.isNotEmpty()) {
                    item { Text("Subtopics", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 2.dp)) }
                    items(subtopics, key = { "topic_${it.id}" }) { child ->
                        FantasyPanel(
                            Modifier.fillMaxWidth().heightIn(min = 82.dp).combinedClickable(onClick = { onOpenSubtopic(child) }, onLongClick = { selectedSubtopic = child }),
                            emphasized = child.pinned
                        ) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                EmblemView(child.emblem, size = 56.dp, animate = child.pinned, showBanner = false)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(child.title, style = MaterialTheme.typography.titleMedium)
                                        if (child.pinned) { Spacer(Modifier.width(6.dp)); Text("◆", color = MaterialTheme.colorScheme.primary) }
                                    }
                                    if (child.deletionLocked) Text("Deletion locked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text("Modified: ${formatTimestamp(child.modifiedAt)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = .65f))
                            }
                        }
                    }
                    item { RuneDivider(Modifier.padding(vertical = 4.dp)) }
                }

                item { Text("Notes", style = MaterialTheme.typography.titleSmall) }
                if (notes.isEmpty()) item { Text("No notes yet.", style = MaterialTheme.typography.bodyMedium) }
                else items(notes, key = { "note_${it.id}" }) { note ->
                    FantasyPanel(
                        Modifier.fillMaxWidth().heightIn(min = 78.dp).combinedClickable(onClick = { onOpenNote(note) }, onLongClick = { selectedNote = note }),
                        emphasized = note.isMasterSummary || note.pinned
                    ) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            EmblemView(topic.emblem, size = 50.dp, animate = note.isMasterSummary, showBanner = false)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                                    if (note.isMasterSummary) { Spacer(Modifier.width(8.dp)); Text("MASTER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                }
                                Text("Modified: ${formatTimestamp(note.modifiedAt)}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (note.pinned && !note.isMasterSummary) Text("◆", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (showSort) TopicSortDialog(childSortMode, noteSortMode, { showSort = false }, onChildSortMode, onNoteSortMode)
    if (editTopicEmblem) EmblemEditorDialog(topic.title, topic.emblem, parentEmblem, { editTopicEmblem = false }) { onSaveEmblem(topic, it); editTopicEmblem = false }

    selectedSubtopic?.let { node ->
        NodeActionDialog(
            node = node,
            onDismiss = { selectedSubtopic = null },
            onOpen = { selectedSubtopic = null; onOpenSubtopic(node) },
            onRename = { selectedSubtopic = null; renameSubtopic = node },
            onEditEmblem = { selectedSubtopic = null; emblemSubtopic = node },
            onMove = { selectedSubtopic = null; moveSubtopic = node },
            onDuplicate = { selectedSubtopic = null; onDuplicateNode(node) },
            onPin = { selectedSubtopic = null; onPinNode(node) },
            onToggleDeletionLock = { selectedSubtopic = null; onToggleDeletionLock(node) },
            onDelete = { selectedSubtopic = null; deleteSubtopic = node }
        )
    }
    emblemSubtopic?.let { node ->
        EmblemEditorDialog(node.title, node.emblem, topic.emblem, { emblemSubtopic = null }) { onSaveEmblem(node, it); emblemSubtopic = null }
    }
    renameSubtopic?.let { node -> RenameDialog(node.title, onDismiss = { renameSubtopic = null }, onRename = { onRenameNode(node, it); renameSubtopic = null }) }
    moveSubtopic?.let { node -> MoveNodeDialog(node, moveTargets(node), pathFor, { moveSubtopic = null }, { onMoveNodeTo(node, it); moveSubtopic = null }, { onMoveNodeEarlier(node) }, { onMoveNodeLater(node) }) }
    deleteSubtopic?.let { node ->
        DeleteConfirmDialog(node.title, "This Topic, all subtopics, and all contained notes will be moved to Trash.", { deleteSubtopic = null }) {
            val deleted = onDeleteNode(node); deleteSubtopic = null
            if (deleted) scope.launch {
                val result = snackbar.showSnackbar("${node.title} moved to Trash", "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) onRestoreNode(node)
            } else scope.launch {
                snackbar.showSnackbar("Deletion blocked by a protected Field/Topic. Unlock it first.")
            }
        }
    }

    selectedNote?.let { note ->
        NoteActionDialog(note, { selectedNote = null }, { selectedNote = null; onOpenNote(note) }, { selectedNote = null; renameNote = note }, { onMoveNoteEarlier(note) }, { onMoveNoteLater(note) }, { selectedNote = null; onDuplicateNote(note) }, { selectedNote = null; onPinNote(note) }, { selectedNote = null; deleteNote = note })
    }
    renameNote?.let { note -> RenameDialog(note.title, allowRename = !note.isMasterSummary, onDismiss = { renameNote = null }, onRename = { onRenameNote(note, it); renameNote = null }) }
    deleteNote?.let { note ->
        DeleteConfirmDialog(note.title, "This note will be moved to Trash.", { deleteNote = null }) {
            val deleted = onDeleteNote(note); deleteNote = null
            if (deleted) scope.launch { val result = snackbar.showSnackbar("${note.title} moved to Trash", "Undo", duration = SnackbarDuration.Short); if (result == SnackbarResult.ActionPerformed) onRestoreNote(note) }
        }
    }
}
