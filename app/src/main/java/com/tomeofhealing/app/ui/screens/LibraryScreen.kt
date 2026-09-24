package com.tomeofhealing.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.model.EmblemDefinition
import com.tomeofhealing.app.model.JournalNode
import com.tomeofhealing.app.model.SortMode
import com.tomeofhealing.app.ui.components.*
import com.tomeofhealing.app.ui.theme.LibraryLayoutMode
import com.tomeofhealing.app.ui.theme.TomeVisualTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    title: String,
    nodes: List<JournalNode>,
    sortMode: SortMode,
    layoutMode: LibraryLayoutMode,
    onLayoutMode: (LibraryLayoutMode) -> Unit,
    allowThemeEditing: Boolean,
    currentTheme: TomeVisualTheme,
    onSaveTheme: (TomeVisualTheme) -> Unit,
    onBack: (() -> Unit)?,
    onOpen: (JournalNode) -> Unit,
    onPin: (JournalNode) -> Unit,
    onCreate: () -> Unit,
    createLabel: String,
    onRename: (JournalNode, String) -> Unit,
    onDuplicate: (JournalNode) -> Unit,
    onDelete: (JournalNode) -> Boolean,
    onRestore: (JournalNode) -> Unit,
    onMoveEarlier: (JournalNode) -> Unit,
    onMoveLater: (JournalNode) -> Unit,
    onMoveTo: (JournalNode, String) -> Boolean,
    moveTargets: (JournalNode) -> List<JournalNode>,
    pathFor: (String) -> String,
    onSortMode: (SortMode) -> Unit,
    onSaveEmblem: (JournalNode, EmblemDefinition) -> Unit,
    parentEmblemFor: (JournalNode) -> EmblemDefinition?,
    onToggleDeletionLock: (JournalNode) -> Unit,
    onOpenBackup: (() -> Unit)? = null,
    onOpenTrash: (() -> Unit)? = null,
    onOpenDiagnostics: (() -> Unit)? = null,
    trashCount: Int = 0
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedNode by remember { mutableStateOf<JournalNode?>(null) }
    var renameNode by remember { mutableStateOf<JournalNode?>(null) }
    var moveNode by remember { mutableStateOf<JournalNode?>(null) }
    var deleteNode by remember { mutableStateOf<JournalNode?>(null) }
    var emblemNode by remember { mutableStateOf<JournalNode?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showView by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }

    FantasyBackdrop {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .84f)),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onBack == null) {
                                EmblemView(TomeOfHealingMainEmblem, size = 42.dp, animate = true, showBanner = false)
                                Spacer(Modifier.width(10.dp))
                            }
                            Column {
                                Text(title, style = MaterialTheme.typography.titleLarge)
                                if (onBack == null) Text("Medical knowledge archive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = .72f))
                            }
                        }
                    },
                    navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("Back") } },
                    actions = {
                        Box {
                            TextButton(onClick = { showView = true }) { Text("View") }
                            DropdownMenu(expanded = showView, onDismissRequest = { showView = false }) {
                                LibraryLayoutMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = { onLayoutMode(mode); showView = false },
                                        trailingIcon = { if (layoutMode == mode) Text("◆") }
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { showSort = true }) { Text("Sort") }
                        if (allowThemeEditing) TextButton(onClick = { showTheme = true }) { Text("Theme") }
                        if (onOpenBackup != null) TextButton(onClick = onOpenBackup) { Text("Backup") }
                        if (onOpenTrash != null) {
                            TextButton(onClick = onOpenTrash) {
                                Text(if (trashCount > 0) "Trash ($trashCount)" else "Trash")
                            }
                        }
                        if (onOpenDiagnostics != null) TextButton(onClick = onOpenDiagnostics) { Text("Health") }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = onCreate, text = { Text(createLabel) }, icon = { Text("✦") })
            }
        ) { padding ->
            if (nodes.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    FantasyPanel(Modifier.padding(24.dp)) { Text("Nothing here yet. Use $createLabel to begin.", modifier = Modifier.padding(24.dp)) }
                }
            } else {
                when (layoutMode) {
                    LibraryLayoutMode.GRID -> NodeGrid(nodes, padding, 220.dp, 142.dp, onOpen) { selectedNode = it }
                    LibraryLayoutMode.COMMAND_PANEL -> NodeGrid(nodes, padding, 150.dp, 118.dp, onOpen) { selectedNode = it }
                    LibraryLayoutMode.LIST -> NodeList(nodes, padding, compact = true, onOpen) { selectedNode = it }
                    LibraryLayoutMode.TOME -> NodeList(nodes, padding, compact = false, onOpen) { selectedNode = it }
                }
            }
        }
    }

    if (showSort) SortDialog(title = "Sort $title", selected = sortMode, onDismiss = { showSort = false }, onSelect = { onSortMode(it); showSort = false })
    if (showTheme) ThemeEditorDialog(initial = currentTheme, onDismiss = { showTheme = false }, onSave = { onSaveTheme(it); showTheme = false })

    selectedNode?.let { node ->
        NodeActionDialog(
            node = node,
            onDismiss = { selectedNode = null },
            onOpen = { selectedNode = null; onOpen(node) },
            onRename = { selectedNode = null; renameNode = node },
            onEditEmblem = { selectedNode = null; emblemNode = node },
            onMove = { selectedNode = null; moveNode = node },
            onDuplicate = { selectedNode = null; onDuplicate(node) },
            onPin = { selectedNode = null; onPin(node) },
            onToggleDeletionLock = { selectedNode = null; onToggleDeletionLock(node) },
            onDelete = { selectedNode = null; deleteNode = node }
        )
    }

    emblemNode?.let { node ->
        EmblemEditorDialog(
            title = node.title,
            initial = node.emblem,
            inheritedFrom = parentEmblemFor(node),
            onDismiss = { emblemNode = null },
            onSave = { onSaveEmblem(node, it); emblemNode = null }
        )
    }

    renameNode?.let { node -> RenameDialog(currentTitle = node.title, onDismiss = { renameNode = null }, onRename = { onRename(node, it); renameNode = null }) }
    moveNode?.let { node ->
        MoveNodeDialog(node = node, eligibleParents = moveTargets(node), pathFor = pathFor, onDismiss = { moveNode = null }, onMoveTo = { onMoveTo(node, it); moveNode = null }, onMoveEarlier = { onMoveEarlier(node) }, onMoveLater = { onMoveLater(node) })
    }
    deleteNode?.let { node ->
        DeleteConfirmDialog(
            title = node.title,
            description = if (node.type.name == "FIELD") "This Field and every nested Topic/Note will be moved to Trash." else "This Topic, its subtopics, and all contained notes will be moved to Trash.",
            onDismiss = { deleteNode = null },
            onConfirm = {
                val deleted = onDelete(node); deleteNode = null
                if (deleted) scope.launch {
                    val result = snackbar.showSnackbar("${node.title} moved to Trash", "Undo", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) onRestore(node)
                } else scope.launch {
                    snackbar.showSnackbar("Deletion blocked by a protected Field/Topic. Unlock it first.")
                }
            }
        )
    }
}

@Composable
private fun NodeGrid(
    nodes: List<JournalNode>,
    padding: PaddingValues,
    minCell: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onOpen: (JournalNode) -> Unit,
    onLong: (JournalNode) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCell),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 92.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(padding).fillMaxSize()
    ) {
        gridItems(nodes, key = { it.id }) { node -> NodeCard(node, Modifier.fillMaxWidth().height(height), compact = height < 130.dp, onOpen, onLong) }
    }
}

@Composable
private fun NodeList(
    nodes: List<JournalNode>,
    padding: PaddingValues,
    compact: Boolean,
    onOpen: (JournalNode) -> Unit,
    onLong: (JournalNode) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
        modifier = Modifier.padding(padding).fillMaxSize()
    ) {
        items(nodes, key = { it.id }) { node -> NodeCard(node, Modifier.fillMaxWidth().height(if (compact) 88.dp else 126.dp), compact, onOpen, onLong) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeCard(
    node: JournalNode,
    modifier: Modifier,
    compact: Boolean,
    onOpen: (JournalNode) -> Unit,
    onLong: (JournalNode) -> Unit
) {
    FantasyPanel(
        modifier = modifier.combinedClickable(onClick = { onOpen(node) }, onLongClick = { onLong(node) }),
        emphasized = node.pinned
    ) {
        Row(Modifier.padding(if (compact) 10.dp else 15.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            EmblemView(node.emblem, size = if (compact) 54.dp else 78.dp, animate = node.pinned, showBanner = false)
            Spacer(Modifier.width(if (compact) 10.dp else 15.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(node.title, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, maxLines = 2)
                    if (node.pinned) { Spacer(Modifier.width(6.dp)); Text("◆", color = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(4.dp))
                Text(node.type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = .72f))
                if (node.deletionLocked) Text("Deletion locked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                if (!compact) Text("Last modified: ${formatTimestamp(node.modifiedAt)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = .65f))
        }
    }
}
