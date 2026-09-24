package com.tomeofhealing.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tomeofhealing.app.data.PersistentJournalRepository
import com.tomeofhealing.app.device.StylusCapabilities
import com.tomeofhealing.app.recovery.CrashRecoveryManager
import com.tomeofhealing.app.backup.BackupService
import com.tomeofhealing.app.model.NodeType
import com.tomeofhealing.app.ink.InkSurfaceMemoryRegistry
import com.tomeofhealing.app.ui.components.CreateNodeDialog
import com.tomeofhealing.app.ui.components.CreateNoteDialog
import com.tomeofhealing.app.ui.components.InternalLinkOption
import com.tomeofhealing.app.ui.screens.LibraryScreen
import com.tomeofhealing.app.ui.screens.DiagnosticsScreen
import com.tomeofhealing.app.ui.screens.BackupScreen
import com.tomeofhealing.app.ui.screens.NoteEditorScreen
import com.tomeofhealing.app.ui.screens.NotesScreen
import com.tomeofhealing.app.ui.screens.TomeSplashScreen
import com.tomeofhealing.app.ui.screens.TrashScreen
import com.tomeofhealing.app.ui.theme.TomeTheme
import com.tomeofhealing.app.ui.theme.VisualPreferences

class MainActivity : ComponentActivity() {
    private lateinit var repo: PersistentJournalRepository
    private lateinit var visualPreferences: VisualPreferences
    private lateinit var backupService: BackupService
    private lateinit var crashRecovery: CrashRecoveryManager
    private lateinit var stylusCapabilities: StylusCapabilities
    private var previousSessionUnclean: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PersistentJournalRepository(applicationContext)
        visualPreferences = VisualPreferences(applicationContext)
        backupService = BackupService(applicationContext)
        crashRecovery = CrashRecoveryManager(applicationContext)
        previousSessionUnclean = crashRecovery.previousSessionUnclean
        crashRecovery.install()
        stylusCapabilities = StylusCapabilities.detect(applicationContext)
        setContent {
            val theme = visualPreferences.theme
            TomeTheme(theme) {
                TomeApp(
                    repo = repo,
                    visuals = visualPreferences,
                    backupService = backupService,
                    crashRecovery = crashRecovery,
                    stylusCapabilities = stylusCapabilities,
                    previousSessionUnclean = previousSessionUnclean
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        crashRecovery.beginSession()
    }

    override fun onStop() {
        repo.checkpoint()
        crashRecovery.markCleanExit()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            InkSurfaceMemoryRegistry.trimAll()
        }
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        repo.close()
        super.onDestroy()
    }
}

private data class NodeCreateRequest(val parentId: String?, val type: NodeType)

@Composable
private fun TomeApp(
    repo: PersistentJournalRepository,
    visuals: VisualPreferences,
    backupService: BackupService,
    crashRecovery: CrashRecoveryManager,
    stylusCapabilities: StylusCapabilities,
    previousSessionUnclean: Boolean
) {
    val nav = rememberNavController()
    val nodes by repo.nodes.collectAsState()
    val notes by repo.notes.collectAsState()
    val versions by repo.versions.collectAsState()
    val rootSortMode by repo.rootSortMode.collectAsState()
    val layoutMode = visuals.libraryLayout

    var createNodeRequest by remember { mutableStateOf<NodeCreateRequest?>(null) }
    var createNoteTopicId by remember { mutableStateOf<String?>(null) }
    var showRecoveryNotice by remember { mutableStateOf(previousSessionUnclean) }

    NavHost(
        navController = nav,
        startDestination = "splash",
        enterTransition = {
            if (visuals.theme.animationScale <= 0.05f) EnterTransition.None
            else fadeIn(tween((180 * visuals.theme.animationScale.coerceAtLeast(.25f)).toInt())) +
                slideInHorizontally(tween((220 * visuals.theme.animationScale.coerceAtLeast(.25f)).toInt())) { it / 14 }
        },
        exitTransition = {
            if (visuals.theme.animationScale <= 0.05f) ExitTransition.None
            else fadeOut(tween((150 * visuals.theme.animationScale.coerceAtLeast(.25f)).toInt())) +
                slideOutHorizontally(tween((190 * visuals.theme.animationScale.coerceAtLeast(.25f)).toInt())) { -it / 18 }
        },
        popEnterTransition = {
            if (visuals.theme.animationScale <= 0.05f) EnterTransition.None
            else fadeIn(tween((160 * visuals.theme.animationScale.coerceAtLeast(.25f)).toInt())) +
                slideInHorizontally(tween((200 * visuals.theme.animationScale.coerceAtLeast(.25f)).toInt())) { -it / 18 }
        },
        popExitTransition = {
            if (visuals.theme.animationScale <= 0.05f) ExitTransition.None
            else fadeOut(tween((140 * visuals.theme.animationScale.coerceAtLeast(.25f)).toInt()))
        }
    ) {
        composable("splash") {
            TomeSplashScreen(onFinished = {
                nav.navigate("library") { popUpTo("splash") { inclusive = true } }
            })
        }

        composable("library") {
            nodes.size
            LibraryScreen(
                title = "Tome of Healing",
                nodes = repo.sortedChildren(null),
                sortMode = rootSortMode,
                layoutMode = layoutMode,
                onLayoutMode = visuals::saveLibraryLayout,
                allowThemeEditing = true,
                currentTheme = visuals.theme,
                onSaveTheme = visuals::saveTheme,
                onBack = null,
                onOpen = { openNode(nav, it.type, it.id) },
                onPin = { repo.togglePinNode(it.id) },
                onCreate = { createNodeRequest = NodeCreateRequest(null, NodeType.FIELD) },
                createLabel = "New Field",
                onRename = { node, title -> repo.renameNode(node.id, title) },
                onDuplicate = { repo.duplicateNode(it.id) },
                onDelete = { repo.deleteNode(it.id) },
                onRestore = { repo.restoreNodeTree(it.id) },
                onMoveEarlier = { repo.moveNodeEarlier(it.id) },
                onMoveLater = { repo.moveNodeLater(it.id) },
                onMoveTo = { node, parentId -> repo.moveNode(node.id, parentId) },
                moveTargets = { repo.eligibleMoveTargets(it.id) },
                pathFor = repo::nodePath,
                onSortMode = { repo.setChildSortMode(null, it) },
                onSaveEmblem = { node, emblem -> repo.updateNodeEmblem(node.id, emblem) },
                parentEmblemFor = { repo.parentEmblemFor(it.id) },
                onToggleDeletionLock = { repo.toggleDeletionLock(it.id) },
                onOpenBackup = { nav.navigate("backup") },
                onOpenTrash = { nav.navigate("trash") },
                onOpenDiagnostics = { nav.navigate("diagnostics") },
                trashCount = repo.trashCount()
            )
        }

        composable("backup") {
            nodes.size
            notes.size
            versions.size
            BackupScreen(
                service = backupService,
                repo = repo,
                visuals = visuals,
                onBack = { nav.popBackStack() }
            )
        }

        composable("diagnostics") {
            DiagnosticsScreen(
                repo = repo,
                recovery = crashRecovery,
                stylus = stylusCapabilities,
                onBack = { nav.popBackStack() }
            )
        }

        composable("trash") {
            // Reading these flows makes the Trash screen react immediately to restore/permanent delete.
            nodes.size
            notes.size
            TrashScreen(
                nodes = repo.trashedRootNodes(),
                notes = repo.trashedStandaloneNotes(),
                pathFor = repo::nodePath,
                onBack = { nav.popBackStack() },
                onRestoreNode = { repo.restoreNodeTree(it.id) },
                onRestoreNote = { repo.restoreNote(it.id) },
                onDeleteNodePermanently = { repo.permanentlyDeleteNode(it.id) },
                onDeleteNotePermanently = { repo.permanentlyDeleteNote(it.id) },
                onEmptyTrash = repo::emptyTrash
            )
        }

        composable("field/{fieldId}") { backStack ->
            val fieldId = backStack.arguments?.getString("fieldId") ?: return@composable
            nodes.size
            val field = repo.node(fieldId) ?: return@composable
            LibraryScreen(
                title = field.title,
                nodes = repo.sortedChildren(fieldId),
                sortMode = repo.sortModeForChildren(fieldId),
                layoutMode = layoutMode,
                onLayoutMode = visuals::saveLibraryLayout,
                allowThemeEditing = false,
                currentTheme = visuals.theme,
                onSaveTheme = visuals::saveTheme,
                onBack = { nav.popBackStack() },
                onOpen = { openNode(nav, it.type, it.id) },
                onPin = { repo.togglePinNode(it.id) },
                onCreate = { createNodeRequest = NodeCreateRequest(fieldId, NodeType.TOPIC) },
                createLabel = "New Topic",
                onRename = { node, title -> repo.renameNode(node.id, title) },
                onDuplicate = { repo.duplicateNode(it.id) },
                onDelete = { repo.deleteNode(it.id) },
                onRestore = { repo.restoreNodeTree(it.id) },
                onMoveEarlier = { repo.moveNodeEarlier(it.id) },
                onMoveLater = { repo.moveNodeLater(it.id) },
                onMoveTo = { node, parentId -> repo.moveNode(node.id, parentId) },
                moveTargets = { repo.eligibleMoveTargets(it.id) },
                pathFor = repo::nodePath,
                onSortMode = { repo.setChildSortMode(fieldId, it) },
                onSaveEmblem = { node, emblem -> repo.updateNodeEmblem(node.id, emblem) },
                parentEmblemFor = { repo.parentEmblemFor(it.id) },
                onToggleDeletionLock = { repo.toggleDeletionLock(it.id) }
            )
        }

        composable("topic/{topicId}") { backStack ->
            val topicId = backStack.arguments?.getString("topicId") ?: return@composable
            nodes.size
            notes.size
            val topic = repo.node(topicId) ?: return@composable
            NotesScreen(
                topic = topic,
                subtopics = repo.sortedChildren(topicId),
                notes = repo.sortedNotes(topicId),
                childSortMode = repo.sortModeForChildren(topicId),
                noteSortMode = repo.sortModeForNotes(topicId),
                onBack = { nav.popBackStack() },
                onOpenSubtopic = { nav.navigate("topic/${it.id}") },
                onOpenNote = { nav.navigate("note/${topicId}/${it.id}") },
                onCreateSubtopic = { createNodeRequest = NodeCreateRequest(topicId, NodeType.TOPIC) },
                onCreateNote = { createNoteTopicId = topicId },
                onRenameNode = { node, title -> repo.renameNode(node.id, title) },
                onDuplicateNode = { repo.duplicateNode(it.id) },
                onPinNode = { repo.togglePinNode(it.id) },
                onToggleDeletionLock = { repo.toggleDeletionLock(it.id) },
                onDeleteNode = { repo.deleteNode(it.id) },
                onRestoreNode = { repo.restoreNodeTree(it.id) },
                onMoveNodeEarlier = { repo.moveNodeEarlier(it.id) },
                onMoveNodeLater = { repo.moveNodeLater(it.id) },
                onMoveNodeTo = { node, parentId -> repo.moveNode(node.id, parentId) },
                moveTargets = { repo.eligibleMoveTargets(it.id) },
                pathFor = repo::nodePath,
                onRenameNote = { note, title -> repo.renameNote(note.id, title) },
                onDuplicateNote = { repo.duplicateNote(it.id) },
                onPinNote = { repo.togglePinNote(it.id) },
                onDeleteNote = { repo.deleteNote(it.id) },
                onRestoreNote = { repo.restoreNote(it.id) },
                onMoveNoteEarlier = { repo.moveNoteEarlier(it.id) },
                onMoveNoteLater = { repo.moveNoteLater(it.id) },
                onChildSortMode = { repo.setChildSortMode(topicId, it) },
                onNoteSortMode = { repo.setNoteSortMode(topicId, it) },
                onSaveEmblem = { node, emblem -> repo.updateNodeEmblem(node.id, emblem) },
                parentEmblem = repo.parentEmblemFor(topicId)
            )
        }

        composable("note/{topicId}/{noteId}") { backStack ->
            val topicId = backStack.arguments?.getString("topicId") ?: return@composable
            val noteId = backStack.arguments?.getString("noteId") ?: return@composable
            notes.size
            val note = repo.note(noteId) ?: return@composable
            val linkOptions = remember(nodes, notes) {
                buildList {
                    nodes.filter { it.deletedAt == null }.forEach { node ->
                        when (node.type) {
                            NodeType.FIELD -> add(InternalLinkOption(node.title, "field/${node.id}", "Field"))
                            NodeType.TOPIC -> add(InternalLinkOption(node.title, "topic/${node.id}", repo.nodePath(node.id)))
                        }
                    }
                    notes.filter { it.deletedAt == null }.forEach { targetNote ->
                        add(InternalLinkOption(targetNote.title, "note/${targetNote.topicId}/${targetNote.id}", "${repo.nodePath(targetNote.topicId)} › ${targetNote.title}"))
                    }
                }.sortedBy { it.label.lowercase() }
            }
            versions.size
            NoteEditorScreen(
                breadcrumb = repo.nodePath(topicId),
                note = note,
                internalLinkOptions = linkOptions,
                noteVersions = repo.versionsForNote(noteId),
                onOpenInternalLink = { route -> nav.navigate(route) },
                onCreateHistoryCheckpoint = { repo.createVersion(noteId) },
                onRestoreHistoryVersion = { versionId -> repo.restoreVersion(noteId, versionId) },
                onDeleteHistoryVersion = { versionId -> repo.deleteVersion(versionId) },
                onBack = { nav.popBackStack() },
                onSave = { canvas, sections -> repo.saveNoteContent(noteId, canvas, sections) }
            )
        }
    }

    if (showRecoveryNotice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRecoveryNotice = false },
            title = { androidx.compose.material3.Text("Recovered after an unexpected close") },
            text = {
                androidx.compose.material3.Text(
                    "The previous foreground session did not close normally. Tome of Healing continuously autosaves note content; a local crash report was retained under Health for troubleshooting."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showRecoveryNotice = false }) {
                    androidx.compose.material3.Text("Continue")
                }
            }
        )
    }

    createNodeRequest?.let { request ->
        CreateNodeDialog(
            type = request.type,
            onDismiss = { createNodeRequest = null },
            onCreate = { title, template ->
                repo.createNode(request.parentId, request.type, title, template)
                createNodeRequest = null
            }
        )
    }

    createNoteTopicId?.let { topicId ->
        CreateNoteDialog(
            onDismiss = { createNoteTopicId = null },
            onCreate = { title -> repo.createNote(topicId, title); createNoteTopicId = null }
        )
    }
}

private fun openNode(nav: NavHostController, type: NodeType, id: String) {
    when (type) {
        NodeType.FIELD -> nav.navigate("field/$id")
        NodeType.TOPIC -> nav.navigate("topic/$id")
    }
}
