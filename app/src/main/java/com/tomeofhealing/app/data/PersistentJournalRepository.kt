package com.tomeofhealing.app.data

import android.content.Context
import com.tomeofhealing.app.attachments.AttachmentStore
import com.tomeofhealing.app.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID


data class RepositoryState(
    val nodes: List<JournalNode>,
    val notes: List<JournalNote>,
    val versions: List<NoteVersion>,
    val rootSortMode: SortMode
)

/**
 * Persistent journal repository for Milestone 2.
 *
 * It preserves the synchronous UI contract from Milestone 1 while committing every
 * structural or note mutation to a transactional SQLite snapshot. This means Fields,
 * Topics, Notes, timestamps, sort modes, Trash state, structured sections and vector
 * canvas documents survive process/app restarts.
 */
class PersistentJournalRepository(context: Context) {
    private val store = JournalSqliteStore(context.applicationContext)
    private val attachmentStore = AttachmentStore(context.applicationContext)
    private val loadedSnapshot = store.loadSnapshot()
    private val initialSnapshot = loadedSnapshot ?: defaultSnapshot()

    private val _rootSortMode = MutableStateFlow(initialSnapshot.rootSortMode)
    val rootSortMode: StateFlow<SortMode> = _rootSortMode

    private val _nodes = MutableStateFlow(initialSnapshot.nodes)
    val nodes: StateFlow<List<JournalNode>> = _nodes

    private val _notes = MutableStateFlow(initialSnapshot.notes)
    val notes: StateFlow<List<JournalNote>> = _notes

    private val _versions = MutableStateFlow(store.loadNoteVersions())
    val versions: StateFlow<List<NoteVersion>> = _versions

    init {
        if (loadedSnapshot == null) persistSnapshot()
    }

    private fun persistSnapshot() {
        store.saveSnapshot(JournalSnapshot(_nodes.value, _notes.value, _rootSortMode.value))
    }

    /** Explicit lifecycle checkpoint; normal mutations are already persisted immediately. */
    fun checkpoint() = persistSnapshot()

    fun close() = store.close()

    fun storageIntegrity(): String = store.quickCheck()
    fun databaseSizeBytes(): Long = store.databaseSizeBytes()
    fun attachmentSizeBytes(): Long = attachmentStore.totalStoredBytes()

    /** Stable logical state used by full backup/import. */
    fun exportState(): RepositoryState = RepositoryState(
        nodes = _nodes.value,
        notes = _notes.value,
        versions = _versions.value,
        rootSortMode = _rootSortMode.value
    )

    /**
     * Atomically replaces the logical journal state after a validated import/merge.
     * The SQLite store commits nodes, notes, settings and history in one transaction
     * before the observable flows are changed.
     */
    @Synchronized
    fun replaceAllState(state: RepositoryState) {
        store.replaceAll(
            JournalSnapshot(state.nodes, state.notes, state.rootSortMode),
            state.versions
        )
        _nodes.value = state.nodes
        _notes.value = state.notes
        _versions.value = state.versions.sortedByDescending { it.createdAt }
        _rootSortMode.value = state.rootSortMode
    }

    private fun updateNodes(transform: (List<JournalNode>) -> List<JournalNode>) {
        _nodes.value = transform(_nodes.value)
        persistSnapshot()
    }

    private fun updateNotes(transform: (List<JournalNote>) -> List<JournalNote>) {
        _notes.value = transform(_notes.value)
        persistSnapshot()
    }

    fun node(id: String): JournalNode? = _nodes.value.firstOrNull { it.id == id }
    fun note(id: String): JournalNote? = _notes.value.firstOrNull { it.id == id }

    fun children(parentId: String?): List<JournalNode> =
        _nodes.value.filter { it.parentId == parentId && it.deletedAt == null }

    fun notesForTopic(topicId: String): List<JournalNote> =
        _notes.value.filter { it.topicId == topicId && it.deletedAt == null }

    fun sortModeForChildren(parentId: String?): SortMode =
        parentId?.let(::node)?.sortMode ?: _rootSortMode.value

    fun sortModeForNotes(topicId: String): SortMode =
        node(topicId)?.noteSortMode ?: SortMode.MANUAL

    fun sortedChildren(parentId: String?): List<JournalNode> =
        sortNodes(children(parentId), sortModeForChildren(parentId))

    fun sortedNotes(topicId: String): List<JournalNote> =
        sortNotes(notesForTopic(topicId), sortModeForNotes(topicId))

    fun setChildSortMode(parentId: String?, mode: SortMode) {
        if (parentId == null) {
            _rootSortMode.value = mode
            persistSnapshot()
        } else {
            updateNodes { list ->
                list.map { if (it.id == parentId) it.copy(sortMode = mode) else it }
            }
        }
    }

    fun setNoteSortMode(topicId: String, mode: SortMode) {
        updateNodes { list ->
            list.map { if (it.id == topicId) it.copy(noteSortMode = mode) else it }
        }
    }

    fun createNode(
        parentId: String?,
        type: NodeType,
        title: String,
        template: NoteTemplate = NoteTemplate.DISEASE
    ): String {
        require(title.isNotBlank())
        if (type == NodeType.FIELD) require(parentId == null) { "Fields are top-level." }
        if (type == NodeType.TOPIC && parentId != null) {
            require(node(parentId)?.deletedAt == null) { "Parent does not exist." }
        }

        val t = System.currentTimeMillis()
        val parentEmblem = parentId?.let(::node)?.emblem ?: EmblemDefinition()
        val id = UUID.randomUUID().toString()
        val nextOrder = children(parentId).maxOfOrNull { it.manualOrder }?.plus(1) ?: 0
        updateNodes {
            it + JournalNode(
                id = id,
                parentId = parentId,
                type = type,
                title = title.trim(),
                createdAt = t,
                modifiedAt = t,
                manualOrder = nextOrder,
                emblem = parentEmblem
            )
        }
        parentId?.let(::touchNode)
        if (type == NodeType.TOPIC) createMasterSummary(id, template)
        return id
    }

    fun createNote(topicId: String, title: String): String {
        require(node(topicId)?.type == NodeType.TOPIC)
        val t = System.currentTimeMillis()
        val nextOrder = notesForTopic(topicId).maxOfOrNull { it.manualOrder }?.plus(1) ?: 0
        val id = UUID.randomUUID().toString()
        updateNotes {
            it + JournalNote(
                id = id,
                topicId = topicId,
                title = title.trim(),
                isMasterSummary = false,
                createdAt = t,
                modifiedAt = t,
                manualOrder = nextOrder
            )
        }
        touchNode(topicId)
        return id
    }

    private fun createMasterSummary(topicId: String, template: NoteTemplate) {
        val t = System.currentTimeMillis()
        val sections = sectionsForTemplate(template)
        val minHeight = if (sections.isEmpty()) 2400f else sections.last().verticalAnchor + SectionLayoutEngine.SECTION_SPACING_DP
        updateNotes {
            it + JournalNote(
                id = UUID.randomUUID().toString(),
                topicId = topicId,
                title = "Master Summary",
                isMasterSummary = true,
                createdAt = t,
                modifiedAt = t,
                pinned = true,
                manualOrder = 0,
                sections = sections,
                canvas = CanvasDocument(heightDp = maxOf(2400f, minHeight))
            )
        }
    }

    fun renameNode(id: String, title: String) {
        if (title.isBlank()) return
        val t = System.currentTimeMillis()
        updateNodes { list ->
            list.map { if (it.id == id) it.copy(title = title.trim(), modifiedAt = t) else it }
        }
        node(id)?.parentId?.let(::touchNode)
    }

    fun renameNote(id: String, title: String) {
        if (title.isBlank()) return
        val existing = note(id) ?: return
        if (existing.isMasterSummary) return
        val t = System.currentTimeMillis()
        updateNotes { list ->
            list.map { if (it.id == id) it.copy(title = title.trim(), modifiedAt = t) else it }
        }
        touchNode(existing.topicId)
    }

    @Synchronized
    fun saveCanvas(noteId: String, canvas: CanvasDocument) {
        val existing = note(noteId) ?: return
        saveNoteContent(noteId, canvas, existing.sections)
    }

    /**
     * Hot-path persistence for handwriting plus structured section metadata.
     * Milestone 8 keeps a rolling ten-version history without snapshotting every pen stroke:
     * the previous durable state is checkpointed at most once per five-minute edit window.
     */
    @Synchronized
    fun saveNoteContent(noteId: String, canvas: CanvasDocument, sections: List<NoteSection>) {
        val existing = note(noteId) ?: return
        val normalized = SectionLayoutEngine.normalizedSections(sections)
        if (existing.canvas == canvas && existing.sections == normalized) return

        maybeCheckpoint(existing, label = "Automatic checkpoint")

        val t = System.currentTimeMillis()
        _notes.value = _notes.value.map {
            if (it.id == noteId) it.copy(canvas = canvas, sections = normalized, modifiedAt = t) else it
        }
        _nodes.value = _nodes.value.map {
            if (it.id == existing.topicId) it.copy(modifiedAt = t) else it
        }
        store.saveNoteContent(noteId, existing.topicId, canvas, normalized, t)
    }

    fun versionsForNote(noteId: String): List<NoteVersion> =
        _versions.value.filter { it.noteId == noteId }.sortedByDescending { it.createdAt }

    @Synchronized
    fun createVersion(noteId: String, label: String = "Manual checkpoint"): Boolean {
        val existing = note(noteId) ?: return false
        return writeVersion(existing, label, force = true)
    }

    @Synchronized
    fun restoreVersion(noteId: String, versionId: String): Boolean {
        val existing = note(noteId) ?: return false
        val version = _versions.value.firstOrNull { it.id == versionId && it.noteId == noteId } ?: return false
        writeVersion(existing, "Before history restore", force = true)
        val t = System.currentTimeMillis()
        val restoredSections = SectionLayoutEngine.normalizedSections(version.sections)
        _notes.value = _notes.value.map {
            if (it.id == noteId) it.copy(canvas = version.canvas, sections = restoredSections, modifiedAt = t) else it
        }
        _nodes.value = _nodes.value.map {
            if (it.id == existing.topicId) it.copy(modifiedAt = t) else it
        }
        store.saveNoteContent(noteId, existing.topicId, version.canvas, restoredSections, t)
        return true
    }

    fun deleteVersion(versionId: String) {
        store.deleteNoteVersion(versionId)
        _versions.value = _versions.value.filterNot { it.id == versionId }
    }

    private fun maybeCheckpoint(note: JournalNote, label: String) {
        val latest = versionsForNote(note.id).firstOrNull()
        val due = latest == null || System.currentTimeMillis() - latest.createdAt >= VERSION_INTERVAL_MS
        if (due) writeVersion(note, label, force = false)
    }

    private fun writeVersion(note: JournalNote, label: String, force: Boolean): Boolean {
        val latest = versionsForNote(note.id).firstOrNull()
        if (!force && latest != null && System.currentTimeMillis() - latest.createdAt < VERSION_INTERVAL_MS) return false
        if (latest != null && latest.canvas == note.canvas && latest.sections == note.sections) return false
        val version = NoteVersion(
            id = UUID.randomUUID().toString(),
            noteId = note.id,
            createdAt = System.currentTimeMillis(),
            sourceModifiedAt = note.modifiedAt,
            label = label,
            sections = note.sections,
            canvas = note.canvas
        )
        store.insertNoteVersion(version, VERSION_LIMIT)
        _versions.value = store.loadNoteVersions()
        return true
    }

    fun updateNodeEmblem(id: String, emblem: EmblemDefinition) {
        val t = System.currentTimeMillis()
        updateNodes { list ->
            list.map { if (it.id == id) it.copy(emblem = emblem, modifiedAt = t) else it }
        }
        node(id)?.parentId?.let(::touchNode)
    }

    fun parentEmblemFor(id: String): EmblemDefinition? = node(id)?.parentId?.let(::node)?.emblem

    fun togglePinNode(id: String) {
        updateNodes { list ->
            list.map {
                if (it.id == id) it.copy(pinned = !it.pinned, modifiedAt = System.currentTimeMillis()) else it
            }
        }
    }

    fun toggleDeletionLock(id: String) {
        val existing = node(id) ?: return
        if (existing.deletedAt != null) return
        updateNodes { list ->
            list.map {
                if (it.id == id) it.copy(
                    deletionLocked = !it.deletionLocked,
                    modifiedAt = System.currentTimeMillis()
                ) else it
            }
        }
    }

    fun togglePinNote(id: String) {
        val existing = note(id) ?: return
        if (existing.isMasterSummary) return // Master Summary is permanently pinned.
        updateNotes { list ->
            list.map {
                if (it.id == id) it.copy(pinned = !it.pinned, modifiedAt = System.currentTimeMillis()) else it
            }
        }
        touchNode(existing.topicId)
    }

    fun deleteNode(id: String): Boolean {
        val root = node(id) ?: return false
        val ids = subtreeIds(id)
        // A protected descendant protects the whole branch from an ancestor delete.
        if (ids.any { node(it)?.deletionLocked == true }) return false
        val noteIds = _notes.value.filter { it.topicId in ids && it.deletedAt == null }.mapTo(mutableSetOf()) { it.id }
        val t = System.currentTimeMillis()
        updateNodes { list -> list.map { if (it.id in ids) it.copy(deletedAt = t) else it } }
        updateNotes { list -> list.map { if (it.id in noteIds) it.copy(deletedAt = t) else it } }
        root.parentId?.let(::touchNode)
        return true
    }

    fun restoreNodeTree(id: String) {
        val root = node(id) ?: return
        val deletionTime = root.deletedAt ?: return
        // Matching the deletion timestamp preserves independently-deleted descendants.
        // It also means quick Undo still works after an activity/process restart.
        val nodeIds = subtreeIdsIncludingDeleted(id).filterTo(mutableSetOf()) { childId ->
            node(childId)?.deletedAt == deletionTime
        }
        val noteIds = _notes.value.filter {
            it.topicId in nodeIds && it.deletedAt == deletionTime
        }.mapTo(mutableSetOf()) { it.id }
        updateNodes { list -> list.map { if (it.id in nodeIds) it.copy(deletedAt = null) else it } }
        updateNotes { list -> list.map { if (it.id in noteIds) it.copy(deletedAt = null) else it } }
    }

    fun deleteNote(id: String): Boolean {
        val existing = note(id) ?: return false
        if (existing.isMasterSummary) return false
        updateNotes { list ->
            list.map { if (it.id == id) it.copy(deletedAt = System.currentTimeMillis()) else it }
        }
        touchNode(existing.topicId)
        return true
    }

    fun restoreNote(id: String) {
        val existing = note(id) ?: return
        if (node(existing.topicId)?.deletedAt != null) return
        updateNotes { list -> list.map { if (it.id == id) it.copy(deletedAt = null) else it } }
    }

    fun trashedRootNodes(): List<JournalNode> = _nodes.value
        .filter { candidate ->
            val deletedAt = candidate.deletedAt ?: return@filter false
            val parent = candidate.parentId?.let(::node)
            parent == null || parent.deletedAt != deletedAt
        }
        .sortedByDescending { it.deletedAt }

    fun trashedStandaloneNotes(): List<JournalNote> = _notes.value
        .filter { candidate ->
            val deletedAt = candidate.deletedAt ?: return@filter false
            val parent = node(candidate.topicId)
            parent != null && parent.deletedAt != deletedAt
        }
        .sortedByDescending { it.deletedAt }

    fun trashCount(): Int = trashedRootNodes().size + trashedStandaloneNotes().size

    @Synchronized
    fun permanentlyDeleteNode(id: String): Boolean {
        val root = node(id) ?: return false
        if (root.deletedAt == null) return false
        val nodeIds = subtreeIdsIncludingDeleted(id)
        val noteIds = _notes.value.filter { it.topicId in nodeIds }.mapTo(mutableSetOf()) { it.id }
        _nodes.value = _nodes.value.filterNot { it.id in nodeIds }
        _notes.value = _notes.value.filterNot { it.id in noteIds }
        store.deleteVersionsForNotes(noteIds)
        noteIds.forEach(attachmentStore::deleteNoteDirectory)
        _versions.value = _versions.value.filterNot { it.noteId in noteIds }
        persistSnapshot()
        return true
    }

    @Synchronized
    fun permanentlyDeleteNote(id: String): Boolean {
        val existing = note(id) ?: return false
        if (existing.deletedAt == null || existing.isMasterSummary) return false
        _notes.value = _notes.value.filterNot { it.id == id }
        store.deleteVersionsForNote(id)
        attachmentStore.deleteNoteDirectory(id)
        _versions.value = _versions.value.filterNot { it.noteId == id }
        persistSnapshot()
        return true
    }

    @Synchronized
    fun emptyTrash(): Int {
        val deletedNodeIds = _nodes.value.filter { it.deletedAt != null }.mapTo(mutableSetOf()) { it.id }
        val deletedNoteIds = _notes.value.filter { it.deletedAt != null || it.topicId in deletedNodeIds }
            .mapTo(mutableSetOf()) { it.id }
        val count = deletedNodeIds.size + deletedNoteIds.size
        if (count == 0) return 0
        _nodes.value = _nodes.value.filterNot { it.id in deletedNodeIds }
        _notes.value = _notes.value.filterNot { it.id in deletedNoteIds }
        store.deleteVersionsForNotes(deletedNoteIds)
        deletedNoteIds.forEach(attachmentStore::deleteNoteDirectory)
        _versions.value = _versions.value.filterNot { it.noteId in deletedNoteIds }
        persistSnapshot()
        return count
    }

    fun duplicateNode(id: String): String? {
        val source = node(id) ?: return null
        val copyRootId = duplicateNodeRecursive(source, source.parentId, isRootCopy = true)
        source.parentId?.let(::touchNode)
        return copyRootId
    }

    private fun duplicateNodeRecursive(source: JournalNode, newParentId: String?, isRootCopy: Boolean): String {
        val t = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        val siblings = children(newParentId)
        val order = siblings.maxOfOrNull { it.manualOrder }?.plus(1) ?: 0
        val newNode = source.copy(
            id = newId,
            parentId = newParentId,
            title = if (isRootCopy) "${source.title} Copy" else source.title,
            createdAt = t,
            modifiedAt = t,
            pinned = false,
            deletedAt = null,
            manualOrder = order
        )
        updateNodes { it + newNode }

        if (source.type == NodeType.TOPIC) {
            notesForTopic(source.id).forEach { oldNote ->
                val noteId = UUID.randomUUID().toString()
                updateNotes {
                    it + oldNote.copy(
                        id = noteId,
                        topicId = newId,
                        title = oldNote.title,
                        createdAt = t,
                        modifiedAt = t,
                        pinned = oldNote.isMasterSummary,
                        deletedAt = null
                    )
                }
            }
        }

        children(source.id).sortedBy { it.manualOrder }.forEach { child ->
            duplicateNodeRecursive(child, newId, isRootCopy = false)
        }
        return newId
    }

    fun duplicateNote(id: String): String? {
        val source = note(id) ?: return null
        val t = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        val nextOrder = notesForTopic(source.topicId).maxOfOrNull { it.manualOrder }?.plus(1) ?: 0
        updateNotes {
            it + source.copy(
                id = newId,
                title = "${source.title} Copy",
                isMasterSummary = false,
                createdAt = t,
                modifiedAt = t,
                pinned = false,
                deletedAt = null,
                manualOrder = nextOrder
            )
        }
        touchNode(source.topicId)
        return newId
    }

    fun moveNode(id: String, newParentId: String?): Boolean {
        val source = node(id) ?: return false
        if (source.type == NodeType.FIELD) return false // Fields remain top-level; use reorder for movement.
        if (newParentId == id) return false
        val newParent = newParentId?.let(::node) ?: return false
        if (newParent.deletedAt != null) return false
        if (newParentId in subtreeIdsIncludingDeleted(id)) return false

        val nextOrder = children(newParentId).maxOfOrNull { it.manualOrder }?.plus(1) ?: 0
        val oldParent = source.parentId
        updateNodes { list ->
            list.map {
                if (it.id == id) it.copy(
                    parentId = newParentId,
                    manualOrder = nextOrder,
                    modifiedAt = System.currentTimeMillis()
                ) else it
            }
        }
        oldParent?.let(::touchNode)
        touchNode(newParentId)
        normalizeSiblingOrder(oldParent)
        return true
    }

    fun eligibleMoveTargets(nodeId: String): List<JournalNode> {
        val source = node(nodeId) ?: return emptyList()
        if (source.type == NodeType.FIELD) return emptyList()
        val excluded = subtreeIdsIncludingDeleted(nodeId)
        return _nodes.value.filter {
            it.deletedAt == null && it.id !in excluded && (it.type == NodeType.FIELD || it.type == NodeType.TOPIC)
        }.sortedBy { nodePath(it.id) }
    }

    fun moveNodeEarlier(id: String) = reorderNode(id, -1)
    fun moveNodeLater(id: String) = reorderNode(id, +1)

    private fun reorderNode(id: String, delta: Int) {
        val source = node(id) ?: return
        val siblings = children(source.parentId).sortedBy { it.manualOrder }
        val index = siblings.indexOfFirst { it.id == id }
        val otherIndex = index + delta
        if (index < 0 || otherIndex !in siblings.indices) return
        val other = siblings[otherIndex]
        updateNodes { list ->
            list.map {
                when (it.id) {
                    source.id -> it.copy(manualOrder = other.manualOrder, modifiedAt = System.currentTimeMillis())
                    other.id -> it.copy(manualOrder = source.manualOrder, modifiedAt = System.currentTimeMillis())
                    else -> it
                }
            }
        }
    }

    fun moveNoteEarlier(id: String) = reorderNote(id, -1)
    fun moveNoteLater(id: String) = reorderNote(id, +1)

    private fun reorderNote(id: String, delta: Int) {
        val source = note(id) ?: return
        if (source.isMasterSummary) return
        val siblings = notesForTopic(source.topicId)
            .filterNot { it.isMasterSummary }
            .sortedBy { it.manualOrder }
        val index = siblings.indexOfFirst { it.id == id }
        val otherIndex = index + delta
        if (index < 0 || otherIndex !in siblings.indices) return
        val other = siblings[otherIndex]
        updateNotes { list ->
            list.map {
                when (it.id) {
                    source.id -> it.copy(manualOrder = other.manualOrder, modifiedAt = System.currentTimeMillis())
                    other.id -> it.copy(manualOrder = source.manualOrder, modifiedAt = System.currentTimeMillis())
                    else -> it
                }
            }
        }
        touchNode(source.topicId)
    }

    fun nodePath(id: String): String {
        val parts = mutableListOf<String>()
        var current = node(id)
        val guard = mutableSetOf<String>()
        while (current != null && guard.add(current.id)) {
            parts += current.title
            current = current.parentId?.let(::node)
        }
        return parts.asReversed().joinToString(" › ")
    }

    private fun touchNode(id: String) {
        updateNodes { list ->
            list.map { if (it.id == id) it.copy(modifiedAt = System.currentTimeMillis()) else it }
        }
    }

    private fun normalizeSiblingOrder(parentId: String?) {
        val orderedIds = children(parentId).sortedBy { it.manualOrder }.map { it.id }
        updateNodes { list ->
            list.map { node ->
                val index = orderedIds.indexOf(node.id)
                if (index >= 0) node.copy(manualOrder = index) else node
            }
        }
    }

    private fun subtreeIds(rootId: String): Set<String> =
        subtreeIdsIncludingDeleted(rootId).filterTo(mutableSetOf()) { id -> node(id)?.deletedAt == null }

    private fun subtreeIdsIncludingDeleted(rootId: String): Set<String> {
        val result = mutableSetOf<String>()
        fun walk(id: String) {
            if (!result.add(id)) return
            _nodes.value.filter { it.parentId == id }.forEach { walk(it.id) }
        }
        walk(rootId)
        return result
    }

    private fun sortNodes(source: List<JournalNode>, mode: SortMode): List<JournalNode> {
        val comparator = when (mode) {
            SortMode.MANUAL -> compareBy<JournalNode> { it.manualOrder }
            SortMode.ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortMode.NEWEST_MODIFIED -> compareByDescending<JournalNode> { it.modifiedAt }
            SortMode.OLDEST_MODIFIED -> compareBy<JournalNode> { it.modifiedAt }
            SortMode.NEWEST_CREATED -> compareByDescending<JournalNode> { it.createdAt }
            SortMode.OLDEST_CREATED -> compareBy<JournalNode> { it.createdAt }
        }
        return source.sortedWith(compareByDescending<JournalNode> { it.pinned }.then(comparator))
    }

    private fun sortNotes(source: List<JournalNote>, mode: SortMode): List<JournalNote> {
        val comparator = when (mode) {
            SortMode.MANUAL -> compareBy<JournalNote> { it.manualOrder }
            SortMode.ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortMode.NEWEST_MODIFIED -> compareByDescending<JournalNote> { it.modifiedAt }
            SortMode.OLDEST_MODIFIED -> compareBy<JournalNote> { it.modifiedAt }
            SortMode.NEWEST_CREATED -> compareByDescending<JournalNote> { it.createdAt }
            SortMode.OLDEST_CREATED -> compareBy<JournalNote> { it.createdAt }
        }
        return source.sortedWith(
            compareByDescending<JournalNote> { it.isMasterSummary }
                .thenByDescending { it.pinned }
                .then(comparator)
        )
    }

    companion object {
        private const val VERSION_LIMIT = 10
        private const val VERSION_INTERVAL_MS = 5L * 60L * 1000L

        private fun defaultSnapshot(): JournalSnapshot {
            val now = System.currentTimeMillis()
            val nodes = listOf(
                JournalNode("field_em", null, NodeType.FIELD, "Emergency Medicine", now, now, pinned = true, manualOrder = 0),
                JournalNode("field_derm", null, NodeType.FIELD, "Dermatology", now, now, manualOrder = 1),
                JournalNode("topic_headache", "field_em", NodeType.TOPIC, "Headache", now, now, manualOrder = 0)
            )
            val notes = listOf(
                JournalNote(
                    id = "note_master_headache",
                    topicId = "topic_headache",
                    title = "Master Summary",
                    isMasterSummary = true,
                    createdAt = now,
                    modifiedAt = now,
                    pinned = true,
                    manualOrder = 0,
                    sections = sectionsForTemplate(NoteTemplate.DISEASE),
                    canvas = CanvasDocument(heightDp = 4400f)
                )
            )
            return JournalSnapshot(nodes, notes, SortMode.MANUAL)
        }

        fun sectionsForTemplate(template: NoteTemplate): List<NoteSection> {
            val titles = when (template) {
                NoteTemplate.DISEASE -> listOf(
                    "Overview", "Clinical Features", "Differential Diagnosis", "Investigations",
                    "Treatment", "Drug Doses", "Red Flags", "Pearls"
                )
                NoteTemplate.DRUG -> listOf(
                    "Mechanism", "Indications", "Dose", "Contraindications",
                    "Adverse Effects", "Interactions", "Special Populations", "Pearls"
                )
                NoteTemplate.PROCEDURE -> listOf(
                    "Indications", "Contraindications", "Equipment", "Preparation",
                    "Technique", "Complications", "Aftercare", "Pearls"
                )
                NoteTemplate.EMERGENCY -> listOf(
                    "Presentation", "Immediate Assessment", "Red Flags", "Investigations",
                    "Immediate Treatment", "Definitive Treatment", "Disposition", "Pearls"
                )
                NoteTemplate.ECG -> listOf(
                    "Pattern", "Rate & Rhythm", "Intervals", "Axis", "Waveform Findings",
                    "Differential", "Clinical Correlation", "Management Pearls"
                )
                NoteTemplate.RADIOLOGY -> listOf(
                    "Modality", "Systematic Review", "Key Findings", "Differential",
                    "Pitfalls", "Clinical Correlation", "Next Step"
                )
                NoteTemplate.ANATOMY -> listOf(
                    "Overview", "Landmarks", "Relations", "Blood Supply", "Innervation",
                    "Function", "Clinical Relevance"
                )
                NoteTemplate.DIFFERENTIAL_DIAGNOSIS -> listOf(
                    "Problem Representation", "Common", "Dangerous", "Can't Miss",
                    "Discriminating Features", "Tests", "Disposition"
                )
                NoteTemplate.BLANK -> emptyList()
            }
            return titles.mapIndexed { index, title ->
                NoteSection(
                    id = "section_${UUID.randomUUID()}",
                    title = title,
                    orderIndex = index,
                    verticalAnchor = 48f + index * SectionLayoutEngine.SECTION_SPACING_DP
                )
            }
        }
    }
}
