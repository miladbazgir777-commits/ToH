package com.tomeofhealing.app.backup

import com.tomeofhealing.app.data.RepositoryState
import com.tomeofhealing.app.model.*
import java.io.File
import java.util.UUID

/**
 * Pure merge/remap engine. Android file I/O is intentionally kept out so the conflict rules
 * can be unit/smoke tested independently from Storage Access Framework plumbing.
 */
object BackupMergeEngine {
    data class Plan(
        val state: RepositoryState,
        /** source note id -> destination note id for imported notes that must copy media bytes */
        val attachmentCopies: Map<String, String>,
        val remappedConflicts: Int,
        val importedNodesApplied: Int,
        val importedNotesApplied: Int,
        val importedVersionsApplied: Int
    )

    fun replace(
        imported: BackupPayload,
        attachmentPath: (destinationNoteId: String, fileName: String) -> String
    ): Plan {
        val nodeMap = imported.nodes.associate { it.id to it.id }
        val noteMap = imported.notes.associate { it.id to it.id }
        val transformed = transformImported(imported, nodeMap, noteMap, attachmentPath)
        return Plan(
            state = RepositoryState(transformed.nodes, transformed.notes, transformed.versions, imported.rootSortMode),
            attachmentCopies = imported.notes.associate { it.id to it.id },
            remappedConflicts = 0,
            importedNodesApplied = imported.nodes.size,
            importedNotesApplied = imported.notes.size,
            importedVersionsApplied = imported.versions.size
        )
    }

    fun merge(
        current: RepositoryState,
        imported: BackupPayload,
        policy: ConflictPolicy,
        attachmentPath: (destinationNoteId: String, fileName: String) -> String,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): Plan {
        val existingNodeIds = current.nodes.mapTo(mutableSetOf()) { it.id }
        val existingNoteIds = current.notes.mapTo(mutableSetOf()) { it.id }
        val usedNodeIds = (existingNodeIds + imported.nodes.map { it.id }).toMutableSet()
        val usedNoteIds = (existingNoteIds + imported.notes.map { it.id }).toMutableSet()
        var remapped = 0

        val nodeMap = imported.nodes.associate { node ->
            val newId = if (policy == ConflictPolicy.KEEP_BOTH && node.id in existingNodeIds) {
                remapped += 1
                uniqueId(usedNodeIds, idFactory).also(usedNodeIds::add)
            } else node.id
            node.id to newId
        }
        val noteMap = imported.notes.associate { note ->
            val newId = if (policy == ConflictPolicy.KEEP_BOTH && note.id in existingNoteIds) {
                remapped += 1
                uniqueId(usedNoteIds, idFactory).also(usedNoteIds::add)
            } else note.id
            note.id to newId
        }

        val transformed = transformImported(imported, nodeMap, noteMap, attachmentPath)
        val attachmentCopies = linkedMapOf<String, String>()

        val mergedNodes = when (policy) {
            ConflictPolicy.KEEP_BOTH -> current.nodes + transformed.nodes
            ConflictPolicy.REPLACE_EXISTING -> mergeNodes(current.nodes, transformed.nodes) { _, incoming -> incoming }
            ConflictPolicy.KEEP_NEWEST -> mergeNodes(current.nodes, transformed.nodes) { existing, incoming ->
                if (incoming.modifiedAt > existing.modifiedAt) incoming else existing
            }
        }

        val existingNoteById = current.notes.associateBy { it.id }
        val mergedNotes = when (policy) {
            ConflictPolicy.KEEP_BOTH -> {
                imported.notes.forEach { source -> attachmentCopies[source.id] = noteMap.getValue(source.id) }
                current.notes + transformed.notes
            }
            ConflictPolicy.REPLACE_EXISTING -> {
                imported.notes.forEach { source -> attachmentCopies[source.id] = noteMap.getValue(source.id) }
                mergeNotes(current.notes, transformed.notes) { _, incoming -> incoming }
            }
            ConflictPolicy.KEEP_NEWEST -> {
                transformed.notes.forEachIndexed { index, incoming ->
                    val source = imported.notes[index]
                    val existing = existingNoteById[incoming.id]
                    if (existing == null || incoming.modifiedAt > existing.modifiedAt) {
                        attachmentCopies[source.id] = incoming.id
                    }
                }
                mergeNotes(current.notes, transformed.notes) { existing, incoming ->
                    if (incoming.modifiedAt > existing.modifiedAt) incoming else existing
                }
            }
        }

        val allowedIncomingVersionNoteIds = when (policy) {
            ConflictPolicy.KEEP_NEWEST -> attachmentCopies.values.toSet()
            else -> transformed.notes.mapTo(mutableSetOf()) { it.id }
        }
        val filteredIncomingVersions = transformed.versions.filter { it.noteId in allowedIncomingVersionNoteIds }
        val mergedVersions = mergeVersions(
            current.versions,
            filteredIncomingVersions,
            policy,
            idFactory
        )
        val currentVersionById = current.versions.associateBy { it.id }
        val appliedVersions = when (policy) {
            ConflictPolicy.KEEP_BOTH, ConflictPolicy.REPLACE_EXISTING -> filteredIncomingVersions.size
            ConflictPolicy.KEEP_NEWEST -> filteredIncomingVersions.count { incoming ->
                val existing = currentVersionById[incoming.id]
                existing == null || incoming.createdAt > existing.createdAt
            }
        }

        return Plan(
            state = RepositoryState(mergedNodes, mergedNotes, mergedVersions, current.rootSortMode),
            attachmentCopies = attachmentCopies,
            remappedConflicts = remapped,
            importedNodesApplied = transformed.nodes.count { incoming ->
                mergedNodes.any { it.id == incoming.id && it == incoming }
            },
            importedNotesApplied = transformed.notes.count { incoming ->
                mergedNotes.any { it.id == incoming.id && it == incoming }
            },
            importedVersionsApplied = appliedVersions
        )
    }

    private data class Transformed(
        val nodes: List<JournalNode>,
        val notes: List<JournalNote>,
        val versions: List<NoteVersion>
    )

    private fun transformImported(
        imported: BackupPayload,
        nodeMap: Map<String, String>,
        noteMap: Map<String, String>,
        attachmentPath: (destinationNoteId: String, fileName: String) -> String
    ): Transformed {
        val nodes = imported.nodes.map { node ->
            node.copy(
                id = nodeMap.getValue(node.id),
                parentId = node.parentId?.let { nodeMap[it] ?: it }
            )
        }
        val notes = imported.notes.map { note ->
            val mappedId = noteMap.getValue(note.id)
            note.copy(
                id = mappedId,
                topicId = nodeMap[note.topicId] ?: note.topicId,
                canvas = rewriteCanvas(note.canvas, nodeMap, noteMap, mappedId, attachmentPath)
            )
        }
        val versions = imported.versions.map { version ->
            val mappedNoteId = noteMap[version.noteId] ?: version.noteId
            version.copy(
                noteId = mappedNoteId,
                canvas = rewriteCanvas(version.canvas, nodeMap, noteMap, mappedNoteId, attachmentPath)
            )
        }
        return Transformed(nodes, notes, versions)
    }

    private fun rewriteCanvas(
        canvas: CanvasDocument,
        nodeMap: Map<String, String>,
        noteMap: Map<String, String>,
        destinationNoteId: String,
        attachmentPath: (destinationNoteId: String, fileName: String) -> String
    ): CanvasDocument = canvas.copy(items = canvas.items.map { item ->
        when (item) {
            is AttachmentItem -> item.copy(uri = attachmentPath(destinationNoteId, File(item.uri).name))
            is TextItem -> if (item.kind == TextObjectKind.LINK) {
                val target = item.metadata["target"]
                if (target?.startsWith("internal:") == true) {
                    item.copy(metadata = item.metadata + ("target" to remapInternalTarget(target, nodeMap, noteMap)))
                } else item
            } else item
            is StrokeItem -> item
        }
    })

    internal fun remapInternalTarget(
        raw: String,
        nodeMap: Map<String, String>,
        noteMap: Map<String, String>
    ): String {
        val route = raw.removePrefix("internal:")
        val parts = route.split('/')
        val mappedRoute = when {
            parts.size == 2 && parts[0] == "field" -> "field/${nodeMap[parts[1]] ?: parts[1]}"
            parts.size == 2 && parts[0] == "topic" -> "topic/${nodeMap[parts[1]] ?: parts[1]}"
            parts.size == 3 && parts[0] == "note" -> {
                val topic = nodeMap[parts[1]] ?: parts[1]
                val note = noteMap[parts[2]] ?: parts[2]
                "note/$topic/$note"
            }
            else -> route
        }
        return "internal:$mappedRoute"
    }

    private fun mergeVersions(
        current: List<NoteVersion>,
        incoming: List<NoteVersion>,
        policy: ConflictPolicy,
        idFactory: () -> String
    ): List<NoteVersion> {
        if (policy == ConflictPolicy.KEEP_BOTH) {
            val currentIds = current.mapTo(mutableSetOf()) { it.id }
            val used = (currentIds + incoming.map { it.id }).toMutableSet()
            return current + incoming.map { version ->
                if (version.id in currentIds) version.copy(id = uniqueId(used, idFactory).also(used::add))
                else version
            }
        }
        return mergeVersionsById(current, incoming) { existing, added ->
            when (policy) {
                ConflictPolicy.REPLACE_EXISTING -> added
                ConflictPolicy.KEEP_NEWEST -> if (added.createdAt > existing.createdAt) added else existing
                ConflictPolicy.KEEP_BOTH -> added
            }
        }
    }

    private fun <T> mergeById(
        current: List<T>,
        incoming: List<T>,
        idOf: (T) -> String,
        choose: (T, T) -> T
    ): List<T> {
        val map = LinkedHashMap<String, T>()
        current.forEach { map[idOf(it)] = it }
        incoming.forEach { item ->
            val id = idOf(item)
            val old = map[id]
            map[id] = if (old == null) item else choose(old, item)
        }
        return map.values.toList()
    }

    private fun mergeNodes(
        current: List<JournalNode>,
        incoming: List<JournalNode>,
        choose: (JournalNode, JournalNode) -> JournalNode
    ) = mergeById(current, incoming, JournalNode::id, choose)

    private fun mergeNotes(
        current: List<JournalNote>,
        incoming: List<JournalNote>,
        choose: (JournalNote, JournalNote) -> JournalNote
    ) = mergeById(current, incoming, JournalNote::id, choose)

    private fun mergeVersionsById(
        current: List<NoteVersion>,
        incoming: List<NoteVersion>,
        choose: (NoteVersion, NoteVersion) -> NoteVersion
    ) = mergeById(current, incoming, NoteVersion::id, choose)

    private fun uniqueId(used: Set<String>, idFactory: () -> String): String {
        var candidate = idFactory()
        while (candidate in used) candidate = idFactory()
        return candidate
    }
}
