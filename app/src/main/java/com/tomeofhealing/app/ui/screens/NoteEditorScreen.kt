package com.tomeofhealing.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tomeofhealing.app.ui.components.*
import com.tomeofhealing.app.ui.theme.LocalTomeVisualTheme
import com.tomeofhealing.app.attachments.AttachmentCanvasOps
import com.tomeofhealing.app.attachments.AttachmentStore
import com.tomeofhealing.app.content.ContentCanvasOps
import com.tomeofhealing.app.ink.*
import com.tomeofhealing.app.model.AttachmentItem
import com.tomeofhealing.app.model.CanvasDocument
import com.tomeofhealing.app.model.JournalNote
import com.tomeofhealing.app.model.TextItem
import com.tomeofhealing.app.model.TextObjectKind
import com.tomeofhealing.app.model.ChecklistEntry
import com.tomeofhealing.app.model.NoteSection
import com.tomeofhealing.app.model.NoteVersion
import com.tomeofhealing.app.model.SectionLayoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private data class EditorSnapshot(
    val document: CanvasDocument,
    val sections: List<NoteSection>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    breadcrumb: String,
    note: JournalNote,
    internalLinkOptions: List<InternalLinkOption> = emptyList(),
    noteVersions: List<NoteVersion> = emptyList(),
    onOpenInternalLink: (String) -> Unit = {},
    onCreateHistoryCheckpoint: () -> Unit = {},
    onRestoreHistoryVersion: (String) -> Unit = {},
    onDeleteHistoryVersion: (String) -> Unit = {},
    onBack: () -> Unit,
    onSave: (CanvasDocument, List<NoteSection>) -> Unit
) {
    val context = LocalContext.current
    val visualTheme = LocalTomeVisualTheme.current
    val toolbarPrefs = remember { InkToolbarPreferences(context) }
    val attachmentStore = remember(note.id) { AttachmentStore(context) }
    val canvasController = rememberInkCanvasController()
    val coroutineScope = rememberCoroutineScope()

    var document by remember(note.id) { mutableStateOf(note.canvas) }
    var sections by remember(note.id) {
        mutableStateOf(SectionLayoutEngine.normalizedSections(note.sections))
    }
    val undoStack = remember(note.id) { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember(note.id) { mutableStateListOf<EditorSnapshot>() }
    var saveState by remember(note.id) { mutableStateOf("Saved") }
    var editRevision by remember(note.id) { mutableIntStateOf(0) }
    var savedRevision by remember(note.id) { mutableIntStateOf(0) }

    var selectedTool by rememberSaveable(note.id) { mutableStateOf(InkTool.PEN) }
    var penColorArgb by rememberSaveable(note.id) { mutableIntStateOf(0xFF182033.toInt()) }
    val penColor = Color(penColorArgb)
    var penWidth by rememberSaveable(note.id) { mutableFloatStateOf(3.2f) }
    var highlighterWidth by rememberSaveable(note.id) { mutableFloatStateOf(16f) }
    var toolbarExpanded by rememberSaveable(note.id) { mutableStateOf(false) }
    var toolbarDock by rememberSaveable(note.id) { mutableStateOf(toolbarPrefs.loadDock()) }
    var visibleTools by remember(note.id) { mutableStateOf(toolbarPrefs.loadVisibleTools()) }
    var showToolbarSettings by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var temporaryEraser by remember { mutableStateOf(false) }
    var selectionCount by remember { mutableIntStateOf(0) }
    var favoriteColors by remember { mutableStateOf(toolbarPrefs.loadFavoriteColors()) }

    var showSectionNavigator by rememberSaveable(note.id) { mutableStateOf(false) }
    var showVersionHistory by rememberSaveable(note.id) { mutableStateOf(false) }
    var renameSectionId by remember { mutableStateOf<String?>(null) }
    var showAddSection by remember { mutableStateOf(false) }

    var showInsertAttachment by remember { mutableStateOf(false) }
    var showAttachmentList by remember { mutableStateOf(false) }
    var showTextObjectList by remember { mutableStateOf(false) }
    var showContentSearch by remember { mutableStateOf(false) }
    var objectEditorKind by remember { mutableStateOf<TextObjectKind?>(null) }
    var linkEditorMode by remember { mutableStateOf("web") }
    var editingTextObjectId by remember(note.id) { mutableStateOf<String?>(null) }
    var selectedTextObjectId by remember(note.id) { mutableStateOf<String?>(null) }
    var selectedAttachmentId by remember(note.id) { mutableStateOf<String?>(null) }
    var cropAttachmentId by remember(note.id) { mutableStateOf<String?>(null) }
    var pendingPdfPath by remember(note.id) { mutableStateOf<String?>(null) }
    var pendingPdfName by remember(note.id) { mutableStateOf<String?>(null) }
    var pendingCamera by remember(note.id) { mutableStateOf<AttachmentStore.StoredAttachment?>(null) }

    val latestDocument by rememberUpdatedState(document)
    val latestSections by rememberUpdatedState(sections)
    val latestEditRevision by rememberUpdatedState(editRevision)
    val latestSavedRevision by rememberUpdatedState(savedRevision)

    fun currentSnapshot() = EditorSnapshot(document, sections)

    fun applySnapshot(next: EditorSnapshot, pushUndo: Boolean = true) {
        if (next.document == document && next.sections == sections) return
        if (pushUndo) {
            undoStack += currentSnapshot()
            while (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
        }
        document = next.document
        sections = SectionLayoutEngine.normalizedSections(next.sections)
        editRevision += 1
    }

    fun recordDocumentEdit(next: CanvasDocument) {
        applySnapshot(EditorSnapshot(next, sections))
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack += currentSnapshot()
        val previous = undoStack.removeAt(undoStack.lastIndex)
        document = previous.document
        sections = previous.sections
        editRevision += 1
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack += currentSnapshot()
        val next = redoStack.removeAt(redoStack.lastIndex)
        document = next.document
        sections = next.sections
        editRevision += 1
    }

    fun selectTool(next: InkTool) {
        if (next != selectedTool) selectedTool = next
    }

    fun toggleSection(section: NoteSection) {
        val next = sections.map {
            if (it.id == section.id) it.copy(collapsed = !it.collapsed) else it
        }
        applySnapshot(EditorSnapshot(document, next))
    }

    fun moveSection(section: NoteSection, direction: Int) {
        val result = SectionLayoutEngine.moveSection(sections, document, section.id, direction)
        applySnapshot(EditorSnapshot(result.canvas, result.sections))
    }

    fun growSection(sectionId: String, delta: Float = SectionLayoutEngine.GROWTH_CHUNK_DP) {
        val result = SectionLayoutEngine.growSection(sections, document, sectionId, delta)
        applySnapshot(EditorSnapshot(result.canvas, result.sections))
    }

    fun addSection(title: String) {
        if (title.isBlank()) return
        val section = NoteSection(
            id = "section_${UUID.randomUUID()}",
            title = title.trim(),
            orderIndex = sections.size
        )
        val result = SectionLayoutEngine.addSection(sections, document, section)
        applySnapshot(EditorSnapshot(result.canvas, result.sections))
        canvasController.scrollToDocumentY(result.sections.last().verticalAnchor)
    }

    fun renameSection(sectionId: String, title: String) {
        if (title.isBlank()) return
        val next = sections.map {
            if (it.id == sectionId) it.copy(title = title.trim()) else it
        }
        applySnapshot(EditorSnapshot(document, next))
    }

    fun currentAttachments(): List<AttachmentItem> = AttachmentCanvasOps.attachments(document)
    fun currentTextObjects(): List<TextItem> = ContentCanvasOps.items(document)

    fun openLinkTarget(target: String) {
        when {
            target.startsWith("web:") -> {
                val url = target.removePrefix("web:")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            target.startsWith("internal:") -> onOpenInternalLink(target.removePrefix("internal:"))
        }
    }

    fun placementFor(width: Float, height: Float): Pair<Float, Float> {
        val x = ((document.widthDp - width) * 0.5f).coerceAtLeast(24f)
        var y = (canvasController.currentViewportCenterDocumentY() - height * 0.5f).coerceAtLeast(72f)
        repeat(12) {
            val mediaOverlap = currentAttachments().any { item ->
                x < item.x + item.width && x + width > item.x && y < item.y + item.height && y + height > item.y
            }
            val textOverlap = currentTextObjects().any { item ->
                x < item.x + item.width && x + width > item.x && y < item.y + item.height && y + height > item.y
            }
            if (!mediaOverlap && !textOverlap) return x to y
            y += 64f
        }
        return x to y
    }

    fun estimatedTextObjectHeight(
        kind: TextObjectKind,
        text: String = "",
        rows: List<List<String>> = emptyList(),
        checklist: List<ChecklistEntry> = emptyList()
    ): Float = when (kind) {
        TextObjectKind.LABEL -> 72f
        TextObjectKind.MEDICATION -> 190f
        TextObjectKind.TABLE -> maxOf(150f, 54f + rows.size * 52f)
        TextObjectKind.CHECKLIST -> maxOf(120f, 48f + checklist.size * 44f)
        TextObjectKind.LINK -> 86f
        TextObjectKind.TEXT -> maxOf(110f, 76f + text.length / 45f * 28f)
    }

    fun insertTextObject(
        kind: TextObjectKind,
        text: String = "",
        metadata: Map<String, String> = emptyMap(),
        rows: List<List<String>> = emptyList(),
        checklist: List<ChecklistEntry> = emptyList()
    ) {
        val width = when (kind) {
            TextObjectKind.LABEL, TextObjectKind.LINK -> 520f
            else -> 640f
        }.coerceAtMost(document.widthDp - 72f)
        val height = estimatedTextObjectHeight(kind, text, rows, checklist)
        val (x, y) = placementFor(width, height)
        val (baseDocument, baseSections) = prepareObjectInsertion(y, height)
        val sectionId = SectionLayoutEngine.sectionAtY(baseSections, y + 8f)?.id
        val next = ContentCanvasOps.addText(
            document = baseDocument,
            x = x,
            y = y,
            width = width,
            text = text,
            sectionId = sectionId,
            kind = kind,
            metadata = metadata,
            tableRows = rows,
            checklist = checklist
        )
        applySnapshot(EditorSnapshot(next, baseSections))
        canvasController.scrollToDocumentY(y)
    }

    fun updateTextObject(id: String, transform: (TextItem) -> TextItem) {
        applySnapshot(EditorSnapshot(ContentCanvasOps.update(document, id, transform), sections))
    }

    fun prepareObjectInsertion(y: Float, objectHeight: Float): Pair<CanvasDocument, List<NoteSection>> {
        val ordered = SectionLayoutEngine.normalizedSections(sections)
        val owner = SectionLayoutEngine.sectionAtY(ordered, y + 8f) ?: return document to sections
        val index = ordered.indexOfFirst { it.id == owner.id }
        if (index < 0 || index == ordered.lastIndex) return document to ordered
        val nextAnchor = ordered[index + 1].verticalAnchor
        val requiredBottom = y + objectHeight + 96f
        if (requiredBottom <= nextAnchor) return document to ordered
        val delta = maxOf(SectionLayoutEngine.GROWTH_CHUNK_DP, requiredBottom - nextAnchor)
        val grown = SectionLayoutEngine.growSection(ordered, document, owner.id, delta)
        return grown.canvas to grown.sections
    }

    suspend fun insertImage(stored: AttachmentStore.StoredAttachment) {
        val size = withContext(Dispatchers.IO) { attachmentStore.imageSize(stored.path) }
        val targetWidth = minOf(620f, document.widthDp - 80f)
        val targetHeight = (targetWidth * size.height / size.width.toFloat()).coerceAtLeast(100f)
        val (x, y) = placementFor(targetWidth, targetHeight)
        val (baseDocument, baseSections) = prepareObjectInsertion(y, targetHeight)
        val sectionId = SectionLayoutEngine.sectionAtY(baseSections, y + 8f)?.id
        val next = AttachmentCanvasOps.add(
            document = baseDocument,
            sourcePath = stored.path,
            kind = "image",
            x = x,
            y = y,
            width = targetWidth,
            height = targetHeight,
            sectionId = sectionId,
            originalName = stored.displayName
        )
        applySnapshot(EditorSnapshot(next, baseSections))
        canvasController.scrollToDocumentY(y)
    }

    suspend fun insertPdfPage(
        path: String,
        displayName: String,
        pageIndex: Int,
        crop: AttachmentCanvasOps.CropRect
    ) {
        val size = withContext(Dispatchers.IO) { attachmentStore.pdfPageSize(path, pageIndex) }
        val c = crop.normalized()
        val croppedWidth = size.width * (c.right - c.left)
        val croppedHeight = size.height * (c.bottom - c.top)
        val targetWidth = minOf(640f, document.widthDp - 80f)
        val targetHeight = (targetWidth * croppedHeight / croppedWidth.coerceAtLeast(1f)).coerceAtLeast(100f)
        val (x, y) = placementFor(targetWidth, targetHeight)
        val (baseDocument, baseSections) = prepareObjectInsertion(y, targetHeight)
        val sectionId = SectionLayoutEngine.sectionAtY(baseSections, y + 8f)?.id
        val next = AttachmentCanvasOps.add(
            document = baseDocument,
            sourcePath = path,
            kind = "pdf_page",
            x = x,
            y = y,
            width = targetWidth,
            height = targetHeight,
            sectionId = sectionId,
            originalName = displayName,
            sourcePage = pageIndex,
            crop = c
        )
        applySnapshot(EditorSnapshot(next, baseSections))
        canvasController.scrollToDocumentY(y)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val stored = withContext(Dispatchers.IO) { attachmentStore.importUri(note.id, uri) }
                insertImage(stored)
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val stored = withContext(Dispatchers.IO) { attachmentStore.importUri(note.id, uri) }
                pendingPdfPath = stored.path
                pendingPdfName = stored.displayName
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val stored = pendingCamera
        pendingCamera = null
        if (success && stored != null) {
            coroutineScope.launch { insertImage(stored) }
        } else if (stored != null) {
            runCatching { File(stored.path).delete() }
        }
    }

    LaunchedEffect(editRevision) {
        if (editRevision == 0 || editRevision == savedRevision) return@LaunchedEffect
        saveState = "Saving…"
        delay(900)
        val canvasSnapshot = document
        val sectionSnapshot = sections
        val revisionBeingSaved = editRevision
        withContext(Dispatchers.IO) { onSave(canvasSnapshot, sectionSnapshot) }
        savedRevision = revisionBeingSaved
        saveState = if (editRevision == savedRevision) "Saved" else "Saving…"
    }

    LaunchedEffect(toolbarExpanded) {
        if (toolbarExpanded) {
            delay(5000)
            toolbarExpanded = false
        }
    }

    DisposableEffect(note.id) {
        onDispose {
            if (latestEditRevision != latestSavedRevision) onSave(latestDocument, latestSections)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(visualTheme.surface).copy(alpha = .90f)),
                title = {
                    Column {
                        Text(breadcrumb, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Text(note.title, maxLines = 1)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { showInsertAttachment = true }) { Text("Insert") }
                    TextButton(
                        onClick = { showAttachmentList = true },
                        enabled = document.items.any { it is AttachmentItem }
                    ) { Text("Media") }
                    TextButton(
                        onClick = { showTextObjectList = true },
                        enabled = document.items.any { it is TextItem }
                    ) { Text("Content") }
                    TextButton(
                        onClick = { showContentSearch = true },
                        enabled = document.items.any { it is TextItem }
                    ) { Text("Find") }
                    TextButton(onClick = { showVersionHistory = true }) { Text("History") }
                    TextButton(onClick = { showSectionNavigator = true }) { Text("Sections") }
                    TextButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) { Text("Undo") }
                    TextButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) { Text("Redo") }
                    Text(
                        when {
                            temporaryEraser -> "Eraser"
                            selectionCount > 0 -> "$selectionCount selected"
                            else -> saveState
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(visualTheme.background))
        ) {
            InkCanvas(
                document = document,
                sections = sections,
                onDocumentChanged = ::recordDocumentEdit,
                onDocumentMetaChanged = { next ->
                    if (next != document) applySnapshot(EditorSnapshot(next, sections))
                },
                toolConfig = InkToolConfig(
                    tool = selectedTool,
                    color = penColor,
                    penWidthDp = penWidth,
                    highlighterWidthDp = highlighterWidth,
                    eraserRadiusDp = 18f,
                    pressureEnabled = true
                ),
                paperPreset = note.paperPreset,
                controller = canvasController,
                onTemporaryEraserChanged = { temporaryEraser = it },
                onSelectionChanged = { selectionCount = it },
                onSectionGrowthRequested = { sectionId, delta -> growSection(sectionId, delta) },
                onLinkActivated = ::openLinkTarget,
                modifier = Modifier.fillMaxSize()
            )

            EdgeInkToolbar(
                dock = toolbarDock,
                expanded = toolbarExpanded,
                onExpandedChange = { toolbarExpanded = it },
                visibleTools = visibleTools,
                selectedTool = selectedTool,
                onSelectTool = ::selectTool,
                penColor = penColor,
                onColorClick = { showColorDialog = true },
                widthLabel = when {
                    selectedTool == InkTool.HIGHLIGHTER -> "${highlighterWidth.toInt()}"
                    selectedTool == InkTool.MARKER -> "${(highlighterWidth * 0.55f).toInt()}"
                    penWidth < 2.8f -> "S"
                    penWidth < 5f -> "M"
                    else -> "L"
                },
                onWidthClick = {
                    if (selectedTool == InkTool.HIGHLIGHTER || selectedTool == InkTool.MARKER) {
                        highlighterWidth = when {
                            highlighterWidth < 14f -> 16f
                            highlighterWidth < 22f -> 26f
                            else -> 10f
                        }
                    } else {
                        penWidth = when {
                            penWidth < 2.8f -> 3.2f
                            penWidth < 5f -> 6f
                            else -> 2f
                        }
                    }
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onUndo = ::undo,
                onRedo = ::redo,
                onSettings = { showToolbarSettings = true }
            )

            // Only this narrow edge strip participates in the section gesture, so normal
            // finger scrolling and stylus input remain owned by the ink surface.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(22.dp)
                    .pointerInput(showSectionNavigator) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onHorizontalDrag = { _, amount -> drag += amount },
                            onDragEnd = {
                                if (drag > 48f) showSectionNavigator = true
                                drag = 0f
                            }
                        )
                    }
            )

            if (showSectionNavigator) {
                SectionNavigatorOverlay(
                    sections = sections,
                    onDismiss = { showSectionNavigator = false },
                    onJump = { section ->
                        canvasController.scrollToDocumentY(section.verticalAnchor)
                        showSectionNavigator = false
                    },
                    onToggleCollapse = ::toggleSection,
                    onMoveUp = { moveSection(it, -1) },
                    onMoveDown = { moveSection(it, +1) },
                    onAddSpace = { growSection(it.id) },
                    onRename = { renameSectionId = it.id },
                    onAddSection = { showAddSection = true }
                )
            }
        }
    }

    if (showToolbarSettings) {
        ToolbarSettingsDialog(
            currentDock = toolbarDock,
            visibleTools = visibleTools,
            onDismiss = { showToolbarSettings = false },
            onApply = { dock, tools ->
                toolbarDock = dock
                visibleTools = tools
                toolbarPrefs.saveDock(dock)
                toolbarPrefs.saveVisibleTools(tools)
                if (selectedTool !in tools) selectTool(tools.firstOrNull() ?: InkTool.PEN)
                showToolbarSettings = false
            }
        )
    }

    if (showColorDialog) {
        InkColorDialog(
            current = penColor,
            favorites = favoriteColors,
            onDismiss = { showColorDialog = false },
            onSelect = { color, saveFavorite ->
                penColorArgb = color.toArgb()
                if (saveFavorite) {
                    favoriteColors = (listOf(color.toArgb()) + favoriteColors).distinct().take(8)
                    toolbarPrefs.saveFavoriteColors(favoriteColors)
                }
                showColorDialog = false
            }
        )
    }

    if (showInsertAttachment) {
        InsertCanvasContentDialog(
            onDismiss = { showInsertAttachment = false },
            onImage = {
                showInsertAttachment = false
                imagePicker.launch("image/*")
            },
            onCamera = {
                showInsertAttachment = false
                val target = attachmentStore.createCameraTarget(note.id)
                pendingCamera = target
                cameraLauncher.launch(attachmentStore.shareableUri(target.path))
            },
            onPdf = {
                showInsertAttachment = false
                pdfPicker.launch(arrayOf("application/pdf"))
            },
            onText = { showInsertAttachment = false; editingTextObjectId = null; objectEditorKind = TextObjectKind.TEXT },
            onLabel = { showInsertAttachment = false; editingTextObjectId = null; objectEditorKind = TextObjectKind.LABEL },
            onMedication = { showInsertAttachment = false; editingTextObjectId = null; objectEditorKind = TextObjectKind.MEDICATION },
            onTable = { showInsertAttachment = false; editingTextObjectId = null; objectEditorKind = TextObjectKind.TABLE },
            onChecklist = { showInsertAttachment = false; editingTextObjectId = null; objectEditorKind = TextObjectKind.CHECKLIST },
            onWebLink = { showInsertAttachment = false; editingTextObjectId = null; linkEditorMode = "web"; objectEditorKind = TextObjectKind.LINK },
            onInternalLink = { showInsertAttachment = false; editingTextObjectId = null; linkEditorMode = "internal"; objectEditorKind = TextObjectKind.LINK }
        )
    }

    if (showAttachmentList) {
        AttachmentListDialog(
            items = currentAttachments(),
            onDismiss = { showAttachmentList = false },
            onJump = { item ->
                canvasController.scrollToDocumentY(item.y)
                showAttachmentList = false
            },
            onEdit = { item ->
                selectedAttachmentId = item.id
                showAttachmentList = false
            }
        )
    }

    selectedAttachmentId?.let { id ->
        val item = document.items.filterIsInstance<AttachmentItem>().firstOrNull { it.id == id }
        if (item == null) {
            selectedAttachmentId = null
        } else {
            AttachmentInspectorDialog(
                item = item,
                onDismiss = { selectedAttachmentId = null },
                onUpdate = { transform ->
                    val next = AttachmentCanvasOps.update(document, id, transform)
                    applySnapshot(EditorSnapshot(next, sections))
                },
                onScale = { factor ->
                    applySnapshot(EditorSnapshot(AttachmentCanvasOps.scale(document, id, factor), sections))
                },
                onBringForward = {
                    applySnapshot(EditorSnapshot(AttachmentCanvasOps.bringForward(document, id), sections))
                },
                onSendBackward = {
                    applySnapshot(EditorSnapshot(AttachmentCanvasOps.sendBackward(document, id), sections))
                },
                onCrop = {
                    cropAttachmentId = id
                    selectedAttachmentId = null
                },
                onJump = { canvasController.scrollToDocumentY(item.y) },
                onOpenPdf = if (item.kind == "pdf_page") {
                    {
                        pendingPdfPath = item.uri
                        pendingPdfName = item.originalName ?: "PDF"
                        selectedAttachmentId = null
                    }
                } else null,
                onDelete = {
                    applySnapshot(EditorSnapshot(AttachmentCanvasOps.remove(document, id), sections))
                    selectedAttachmentId = null
                }
            )
        }
    }

    if (showContentSearch) {
        ContentSearchDialog(
            objects = currentTextObjects(),
            onDismiss = { showContentSearch = false },
            onJump = { item ->
                canvasController.scrollToDocumentY(item.y)
                showContentSearch = false
            }
        )
    }

    if (showTextObjectList) {
        TextObjectListDialog(
            objects = currentTextObjects(),
            onDismiss = { showTextObjectList = false },
            onJump = { item ->
                canvasController.scrollToDocumentY(item.y)
                showTextObjectList = false
            },
            onEdit = { item ->
                selectedTextObjectId = item.id
                showTextObjectList = false
            }
        )
    }

    selectedTextObjectId?.let { id ->
        val item = document.items.filterIsInstance<TextItem>().firstOrNull { it.id == id }
        if (item == null) {
            selectedTextObjectId = null
        } else {
            TextObjectInspectorDialog(
                item = item,
                onDismiss = { selectedTextObjectId = null },
                onEdit = {
                    editingTextObjectId = id
                    if (item.kind == TextObjectKind.LINK) {
                        linkEditorMode = if (item.metadata["target"].orEmpty().startsWith("internal:")) "internal" else "web"
                    }
                    objectEditorKind = item.kind
                    selectedTextObjectId = null
                },
                onScale = { factor ->
                    applySnapshot(EditorSnapshot(ContentCanvasOps.scale(document, id, factor), sections))
                },
                onToggleLock = { updateTextObject(id) { it.copy(locked = !it.locked) } },
                onBringForward = { applySnapshot(EditorSnapshot(ContentCanvasOps.bringForward(document, id), sections)) },
                onSendBackward = { applySnapshot(EditorSnapshot(ContentCanvasOps.sendBackward(document, id), sections)) },
                onJump = { canvasController.scrollToDocumentY(item.y) },
                onOpenLink = item.metadata["target"]?.let { target -> { openLinkTarget(target) } },
                onDelete = {
                    applySnapshot(EditorSnapshot(ContentCanvasOps.remove(document, id), sections))
                    selectedTextObjectId = null
                }
            )
        }
    }

    objectEditorKind?.let { kind ->
        val editing = editingTextObjectId?.let { id -> document.items.filterIsInstance<TextItem>().firstOrNull { it.id == id } }
        fun finishEditor() {
            objectEditorKind = null
            editingTextObjectId = null
        }
        when (kind) {
            TextObjectKind.TEXT, TextObjectKind.LABEL -> TextBlockDialog(
                kind = kind,
                initial = editing?.text.orEmpty(),
                onDismiss = ::finishEditor,
                onConfirm = { value ->
                    if (editing != null) updateTextObject(editing.id) { it.copy(text = value) }
                    else insertTextObject(kind, text = value)
                    finishEditor()
                }
            )
            TextObjectKind.MEDICATION -> MedicationBlockDialog(
                initial = editing?.metadata.orEmpty(),
                onDismiss = ::finishEditor,
                onConfirm = { metadata ->
                    if (editing != null) updateTextObject(editing.id) { it.copy(metadata = metadata) }
                    else insertTextObject(kind, metadata = metadata)
                    finishEditor()
                }
            )
            TextObjectKind.TABLE -> TableBlockDialog(
                initialRows = editing?.tableRows.orEmpty(),
                onDismiss = ::finishEditor,
                onConfirm = { rows ->
                    if (editing != null) updateTextObject(editing.id) { it.copy(tableRows = rows) }
                    else insertTextObject(kind, rows = rows)
                    finishEditor()
                }
            )
            TextObjectKind.CHECKLIST -> ChecklistBlockDialog(
                initialEntries = editing?.checklist.orEmpty(),
                onDismiss = ::finishEditor,
                onConfirm = { entries ->
                    if (editing != null) updateTextObject(editing.id) { it.copy(checklist = entries) }
                    else insertTextObject(kind, checklist = entries)
                    finishEditor()
                }
            )
            TextObjectKind.LINK -> {
                if (linkEditorMode == "internal") {
                    InternalLinkPickerDialog(
                        options = internalLinkOptions,
                        currentTarget = editing?.metadata?.get("target")?.removePrefix("internal:"),
                        onDismiss = ::finishEditor,
                        onConfirm = { option ->
                            val metadata = mapOf("target" to "internal:${option.target}", "destinationLabel" to option.label)
                            if (editing != null) updateTextObject(editing.id) { it.copy(text = option.label, metadata = metadata) }
                            else insertTextObject(kind, text = option.label, metadata = metadata)
                            finishEditor()
                        }
                    )
                } else {
                    val existingTarget = editing?.metadata?.get("target").orEmpty().removePrefix("web:")
                    WebLinkDialog(
                        initialLabel = editing?.text.orEmpty(),
                        initialUrl = existingTarget,
                        onDismiss = ::finishEditor,
                        onConfirm = { label, url ->
                            val metadata = mapOf("target" to "web:$url", "destinationLabel" to label)
                            if (editing != null) updateTextObject(editing.id) { it.copy(text = label, metadata = metadata) }
                            else insertTextObject(kind, text = label, metadata = metadata)
                            finishEditor()
                        }
                    )
                }
            }
        }
    }

    cropAttachmentId?.let { id ->
        val item = document.items.filterIsInstance<AttachmentItem>().firstOrNull { it.id == id }
        if (item == null) {
            cropAttachmentId = null
        } else {
            CropAttachmentDialog(
                store = attachmentStore,
                item = item,
                onDismiss = { cropAttachmentId = null },
                onApply = { crop ->
                    applySnapshot(EditorSnapshot(AttachmentCanvasOps.applyCrop(document, id, crop), sections))
                }
            )
        }
    }

    val pdfPath = pendingPdfPath
    val pdfName = pendingPdfName
    if (pdfPath != null && pdfName != null) {
        PdfImportDialog(
            store = attachmentStore,
            path = pdfPath,
            displayName = pdfName,
            onDismiss = {
                pendingPdfPath = null
                pendingPdfName = null
            },
            onInsert = { page, crop ->
                coroutineScope.launch { insertPdfPage(pdfPath, pdfName, page, crop) }
            }
        )
    }

    if (showVersionHistory) {
        VersionHistoryDialog(
            noteTitle = note.title,
            versions = noteVersions,
            onDismiss = { showVersionHistory = false },
            onCreateCheckpoint = {
                coroutineScope.launch {
                    // Ensure the latest local ink is durable before capturing a named checkpoint.
                    val revision = editRevision
                    if (revision != savedRevision) {
                        val canvasSnapshot = document
                        val sectionSnapshot = sections
                        withContext(Dispatchers.IO) { onSave(canvasSnapshot, sectionSnapshot) }
                        savedRevision = revision
                        saveState = "Saved"
                    }
                    withContext(Dispatchers.IO) { onCreateHistoryCheckpoint() }
                }
            },
            onRestore = { version ->
                coroutineScope.launch {
                    // First persist any unsaved local edits so repository history can protect them.
                    if (editRevision != savedRevision) {
                        val canvasSnapshot = document
                        val sectionSnapshot = sections
                        withContext(Dispatchers.IO) { onSave(canvasSnapshot, sectionSnapshot) }
                    }
                    withContext(Dispatchers.IO) { onRestoreHistoryVersion(version.id) }
                    document = version.canvas
                    sections = SectionLayoutEngine.normalizedSections(version.sections)
                    undoStack.clear()
                    redoStack.clear()
                    editRevision = 0
                    savedRevision = 0
                    saveState = "Saved"
                    showVersionHistory = false
                }
            },
            onDelete = { version ->
                coroutineScope.launch { withContext(Dispatchers.IO) { onDeleteHistoryVersion(version.id) } }
            }
        )
    }

    renameSectionId?.let { sectionId ->
        val existing = sections.firstOrNull { it.id == sectionId }
        if (existing != null) {
            SectionNameDialog(
                title = "Rename section",
                initial = existing.title,
                actionLabel = "Rename",
                onDismiss = { renameSectionId = null },
                onConfirm = {
                    renameSection(sectionId, it)
                    renameSectionId = null
                }
            )
        } else renameSectionId = null
    }

    if (showAddSection) {
        SectionNameDialog(
            title = "Add handwritten section",
            initial = "",
            actionLabel = "Add",
            onDismiss = { showAddSection = false },
            onConfirm = {
                addSection(it)
                showAddSection = false
                showSectionNavigator = false
            }
        )
    }
}

@Composable
private fun BoxScope.SectionNavigatorOverlay(
    sections: List<NoteSection>,
    onDismiss: () -> Unit,
    onJump: (NoteSection) -> Unit,
    onToggleCollapse: (NoteSection) -> Unit,
    onMoveUp: (NoteSection) -> Unit,
    onMoveDown: (NoteSection) -> Unit,
    onAddSpace: (NoteSection) -> Unit,
    onRename: (NoteSection) -> Unit,
    onAddSection: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(onClick = onDismiss)
    )
    Surface(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .widthIn(min = 300.dp, max = 380.dp),
        tonalElevation = 12.dp,
        shadowElevation = 12.dp,
        color = Color(0xFFEEE7DC)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Tap a title to jump", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            HorizontalDivider()
            if (sections.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("This note has no structured sections yet.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddSection) { Text("Add section") }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(sections.sortedBy { it.orderIndex }, key = { _, it -> it.id }) { index, section ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onJump(section) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}. ${section.title}",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                AssistChip(
                                    onClick = { onToggleCollapse(section) },
                                    label = { Text(if (section.collapsed) "Open" else "Collapse") }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = { onMoveUp(section) }, enabled = index > 0) { Text("↑") }
                                TextButton(onClick = { onMoveDown(section) }, enabled = index < sections.lastIndex) { Text("↓") }
                                TextButton(onClick = { onAddSpace(section) }) { Text("+ Space") }
                                TextButton(onClick = { onRename(section) }) { Text("Rename") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Button(
                    onClick = onAddSection,
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                ) { Text("Add custom section") }
            }
        }
    }
}

@Composable
private fun SectionNameDialog(
    title: String,
    initial: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Section name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(actionLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BoxScope.EdgeInkToolbar(
    dock: ToolbarDock,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    visibleTools: List<InkTool>,
    selectedTool: InkTool,
    onSelectTool: (InkTool) -> Unit,
    penColor: Color,
    onColorClick: () -> Unit,
    widthLabel: String,
    onWidthClick: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSettings: () -> Unit
) {
    val alignment = when (dock) {
        ToolbarDock.LEFT -> Alignment.CenterStart
        ToolbarDock.RIGHT -> Alignment.CenterEnd
        ToolbarDock.BOTTOM -> Alignment.BottomCenter
    }
    val arrangement = if (dock == ToolbarDock.BOTTOM) Arrangement.Center else Arrangement.spacedBy(4.dp)

    Surface(
        modifier = Modifier
            .align(alignment)
            .padding(8.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEE241B32)
    ) {
        if (!expanded) {
            TextButton(onClick = { onExpandedChange(true) }) { Text("TOOLS", color = Color(0xFFC9D0DA)) }
        } else if (dock == ToolbarDock.BOTTOM) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = arrangement,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarContents(
                    visibleTools, selectedTool, onSelectTool, penColor, onColorClick,
                    widthLabel, onWidthClick, canUndo, canRedo, onUndo, onRedo, onSettings
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ToolbarContents(
                    visibleTools, selectedTool, onSelectTool, penColor, onColorClick,
                    widthLabel, onWidthClick, canUndo, canRedo, onUndo, onRedo, onSettings
                )
            }
        }
    }
}

@Composable
private fun ToolbarContents(
    visibleTools: List<InkTool>,
    selectedTool: InkTool,
    onSelectTool: (InkTool) -> Unit,
    penColor: Color,
    onColorClick: () -> Unit,
    widthLabel: String,
    onWidthClick: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSettings: () -> Unit
) {
    visibleTools.forEach { tool ->
        FilterChip(
            selected = tool == selectedTool,
            onClick = { onSelectTool(tool) },
            label = {
                Text(
                    when (tool) {
                        InkTool.PEN -> "Pen"
                        InkTool.FOUNTAIN -> "Ftn"
                        InkTool.BALLPOINT -> "Ball"
                        InkTool.PENCIL -> "Pencil"
                        InkTool.MARKER -> "Mark"
                        InkTool.HIGHLIGHTER -> "Hi"
                        InkTool.RULER -> "Line"
                        InkTool.SHAPE -> "Rect"
                        InkTool.ERASER -> "Erase"
                        InkTool.LASSO -> "Lasso"
                    }
                )
            }
        )
    }
    Box(
        Modifier
            .size(34.dp)
            .background(penColor, CircleShape)
            .border(1.dp, Color(0xFFC9D0DA), CircleShape)
            .clickable(onClick = onColorClick)
    )
    TextButton(onClick = onWidthClick) { Text(widthLabel) }
    TextButton(onClick = onUndo, enabled = canUndo) { Text("↶") }
    TextButton(onClick = onRedo, enabled = canRedo) { Text("↷") }
    TextButton(onClick = onSettings) { Text("⋮") }
}

@Composable
private fun ToolbarSettingsDialog(
    currentDock: ToolbarDock,
    visibleTools: List<InkTool>,
    onDismiss: () -> Unit,
    onApply: (ToolbarDock, List<InkTool>) -> Unit
) {
    var dock by remember { mutableStateOf(currentDock) }
    var tools by remember { mutableStateOf(visibleTools) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Handwriting toolbar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dock")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolbarDock.entries.forEach { option ->
                        FilterChip(
                            selected = dock == option,
                            onClick = { dock = option },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                HorizontalDivider()
                Text("Visible tools")
                InkTool.entries.forEach { tool ->
                    val index = tools.indexOf(tool)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = index >= 0,
                            onCheckedChange = { checked ->
                                val next = tools.toMutableList()
                                if (checked && tool !in next) next += tool
                                if (!checked && next.size > 1) next.remove(tool)
                                tools = next
                            }
                        )
                        Text(tool.label, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                if (index > 0) {
                                    val next = tools.toMutableList()
                                    val value = next.removeAt(index)
                                    next.add(index - 1, value)
                                    tools = next
                                }
                            },
                            enabled = index > 0
                        ) { Text("↑") }
                        TextButton(
                            onClick = {
                                if (index >= 0 && index < tools.lastIndex) {
                                    val next = tools.toMutableList()
                                    val value = next.removeAt(index)
                                    next.add(index + 1, value)
                                    tools = next
                                }
                            },
                            enabled = index >= 0 && index < tools.lastIndex
                        ) { Text("↓") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(dock, tools) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InkColorDialog(
    current: Color,
    favorites: List<Int>,
    onDismiss: () -> Unit,
    onSelect: (Color, Boolean) -> Unit
) {
    var hex by remember(current) {
        mutableStateOf(String.format("%08X", current.toArgb()))
    }
    var invalid by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 8.dp) {
            Column(
                Modifier.padding(20.dp).widthIn(min = 300.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Ink color", style = MaterialTheme.typography.titleLarge)
                Text("Medical presets")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MedicalInkPalette.forEach { color ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(color, CircleShape)
                                .border(
                                    if (color.toArgb() == current.toArgb()) 3.dp else 1.dp,
                                    Color(0xFFC9D0DA),
                                    CircleShape
                                )
                                .clickable { onSelect(color, false) }
                        )
                    }
                }
                if (favorites.isNotEmpty()) {
                    Text("Saved favorites")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        favorites.forEach { argb ->
                            val color = Color(argb)
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .background(color, CircleShape)
                                    .border(1.dp, Color(0xFFC9D0DA), CircleShape)
                                    .clickable { onSelect(color, false) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it.uppercase().filter { ch -> ch in "0123456789ABCDEF" }.take(8)
                        invalid = false
                    },
                    label = { Text("Custom ARGB hex") },
                    supportingText = { Text("Example: FF173E74") },
                    isError = invalid,
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        val parsed = runCatching {
                            val normalized = if (hex.length == 6) "FF$hex" else hex
                            require(normalized.length == 8)
                            normalized.toLong(16).toInt()
                        }.getOrNull()
                        if (parsed == null) invalid = true else onSelect(Color(parsed), true)
                    }) { Text("Use & save") }
                }
            }
        }
    }
}
