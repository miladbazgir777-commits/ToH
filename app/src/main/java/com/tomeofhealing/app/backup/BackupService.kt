package com.tomeofhealing.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tomeofhealing.app.data.PersistentJournalRepository
import com.tomeofhealing.app.model.NodeType
import com.tomeofhealing.app.model.AttachmentItem
import com.tomeofhealing.app.ui.theme.LibraryLayoutMode
import com.tomeofhealing.app.ui.theme.TomeVisualTheme
import com.tomeofhealing.app.ui.theme.VisualPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Full logical journal backup + app-private media bundle. */
class BackupService(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        classDiscriminator = "itemType"
    }

    val configuredFolderUri: Uri?
        get() = prefs.getString(KEY_FOLDER_URI, null)?.let(Uri::parse)

    val lastBackupName: String?
        get() = prefs.getString(KEY_LAST_BACKUP_NAME, null)

    val lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)

    fun rememberFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun clearRememberedFolder() {
        prefs.edit().remove(KEY_FOLDER_URI).apply()
    }

    fun folderDisplayName(): String? = configuredFolderUri?.let { uri ->
        DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
    }

    suspend fun createBackup(
        repo: PersistentJournalRepository,
        visuals: VisualPreferences
    ): BackupResult {
        val visualSnapshot = visuals.theme
        val layoutSnapshot = visuals.libraryLayout
        return withContext(Dispatchers.IO) {
        val folderUri = configuredFolderUri ?: error("Choose a backup folder first.")
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: error("The remembered backup folder is no longer available.")
        require(folder.canWrite()) { "The backup folder is not writable." }

        repo.checkpoint()
        val state = repo.exportState()
        val now = System.currentTimeMillis()
        val visual = visualSnapshot
        val inkPrefs = context.getSharedPreferences("ink_toolbar_preferences", Context.MODE_PRIVATE)
        val payload = BackupPayload(
            createdAt = now,
            rootSortMode = state.rootSortMode,
            nodes = state.nodes,
            notes = state.notes,
            versions = state.versions,
            visual = BackupVisualSettings(
                themeName = visual.name,
                primary = visual.primary,
                secondary = visual.secondary,
                accent = visual.accent,
                background = visual.background,
                surface = visual.surface,
                parchment = visual.parchment,
                ink = visual.ink,
                material = visual.material,
                textureStrength = visual.textureStrength,
                glowStrength = visual.glowStrength,
                animationScale = visual.animationScale,
                libraryLayout = layoutSnapshot.name,
                toolbarDock = inkPrefs.getString("dock", null),
                toolbarToolOrder = inkPrefs.getString("tool_order", null),
                toolbarLegacyVisibleTools = inkPrefs.getStringSet("visible_tools", emptySet()).orEmpty().toList(),
                toolbarFavoriteColors = inkPrefs.getString("favorite_colors", null)
            )
        )

        val attachmentFilesByNote = collectAttachmentFiles(payload)
        val attachmentFiles = attachmentFilesByNote.values.sumOf { it.size }
        val manifest = BackupManifest(
            appVersion = APP_VERSION,
            createdAt = now,
            fieldCount = payload.nodes.count { it.type == NodeType.FIELD },
            topicCount = payload.nodes.count { it.type == NodeType.TOPIC },
            noteCount = payload.notes.size,
            versionCount = payload.versions.size,
            attachmentFileCount = attachmentFiles
        )

        val fileName = "TomeOfHealing_${timestamp(now)}$BACKUP_EXTENSION"
        val temp = File.createTempFile("tome_backup_", BACKUP_EXTENSION, context.cacheDir)
        try {
            ZipOutputStream(FileOutputStream(temp).buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(PAYLOAD_ENTRY))
                zip.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                attachmentFilesByNote.forEach { (noteId, files) ->
                    files.forEach { file ->
                        zip.putNextEntry(ZipEntry("attachments/$noteId/${safeFileName(file.name)}"))
                        FileInputStream(file).buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }

            folder.findFile(fileName)?.delete()
            val destination = folder.createFile(MIME_TYPE, fileName)
                ?: error("Unable to create backup file in the selected folder.")
            context.contentResolver.openOutputStream(destination.uri, "w")?.use { output ->
                FileInputStream(temp).buffered().use { it.copyTo(output) }
            } ?: error("Unable to open backup destination.")

            val deleted = rotateBackups(folder)
            prefs.edit()
                .putLong(KEY_LAST_BACKUP_AT, now)
                .putString(KEY_LAST_BACKUP_NAME, fileName)
                .apply()
            BackupResult(fileName, temp.length(), deleted)
        } finally {
            temp.delete()
        }
        }
    }

    suspend fun inspectBackup(uri: Uri): BackupInspection = withContext(Dispatchers.IO) {
        val temp = copyUriToTemp(uri)
        try {
            val manifest = ZipFile(temp).use { zip ->
                val entry = zip.getEntry(MANIFEST_ENTRY) ?: error("Not a Tome of Healing backup: manifest missing.")
                val raw = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                json.decodeFromString<BackupManifest>(raw)
            }
            validateFormat(manifest.formatVersion)
            BackupInspection(uri.toString(), manifest)
        } finally {
            temp.delete()
        }
    }

    suspend fun importBackup(
        uri: Uri,
        repo: PersistentJournalRepository,
        visuals: VisualPreferences,
        mode: ImportMode,
        conflictPolicy: ConflictPolicy,
        restoreAppearance: Boolean
    ): ImportResult = withContext(Dispatchers.IO) {
        val temp = copyUriToTemp(uri)
        val stagingRoot = File(context.filesDir, "journal_attachments_import_${UUID.randomUUID()}")
        try {
            val payload = ZipFile(temp).use { zip ->
                val manifestEntry = zip.getEntry(MANIFEST_ENTRY) ?: error("Backup manifest missing.")
                val manifestRaw = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val manifest = json.decodeFromString<BackupManifest>(manifestRaw)
                validateFormat(manifest.formatVersion)

                val payloadEntry = zip.getEntry(PAYLOAD_ENTRY) ?: error("Backup journal payload missing.")
                val raw = zip.getInputStream(payloadEntry).bufferedReader().use { it.readText() }
                json.decodeFromString<BackupPayload>(raw).also(::validatePayload)
            }

            val attachmentPath: (String, String) -> String = { noteId, name ->
                File(context.filesDir, "journal_attachments/$noteId/${safeFileName(name)}").absolutePath
            }
            val plan = if (mode == ImportMode.REPLACE) {
                BackupMergeEngine.replace(payload, attachmentPath)
            } else {
                BackupMergeEngine.merge(repo.exportState(), payload, conflictPolicy, attachmentPath)
            }

            // Stage every destination directory, even when empty. That lets merge semantics
            // replace an existing note's old media with an imported note that contains none.
            plan.attachmentCopies.values.forEach { destinationNoteId ->
                File(stagingRoot, destinationNoteId).mkdirs()
            }
            val copiedFiles = ZipFile(temp).use { zip ->
                extractSelectedAttachments(zip, stagingRoot, plan.attachmentCopies)
            }

            commitAttachmentAndStateSwap(stagingRoot, plan, repo, mode)

            if (restoreAppearance) {
                withContext(Dispatchers.Main) { restoreVisualSettings(payload.visual, visuals) }
            }

            ImportResult(
                importedNodes = plan.importedNodesApplied,
                importedNotes = plan.importedNotesApplied,
                importedVersions = plan.importedVersionsApplied,
                copiedAttachmentFiles = copiedFiles,
                remappedConflicts = plan.remappedConflicts,
                appearanceRestored = restoreAppearance
            )
        } finally {
            temp.delete()
            if (stagingRoot.exists()) stagingRoot.deleteRecursively()
        }
    }

    private fun commitAttachmentAndStateSwap(
        stagingRoot: File,
        plan: BackupMergeEngine.Plan,
        repo: PersistentJournalRepository,
        mode: ImportMode
    ) {
        val liveRoot = File(context.filesDir, "journal_attachments")
        if (mode == ImportMode.REPLACE) {
            val rollback = File(context.filesDir, "journal_attachments_rollback_${UUID.randomUUID()}")
            if (liveRoot.exists() && !liveRoot.renameTo(rollback)) {
                error("Unable to prepare current attachments for restore.")
            }
            var stagedMoved = false
            try {
                if (stagingRoot.exists()) {
                    stagedMoved = stagingRoot.renameTo(liveRoot)
                    if (!stagedMoved) error("Unable to install restored attachments.")
                } else {
                    liveRoot.mkdirs()
                }
                repo.replaceAllState(plan.state)
                rollback.deleteRecursively()
            } catch (t: Throwable) {
                if (stagedMoved && liveRoot.exists()) liveRoot.deleteRecursively()
                if (rollback.exists()) rollback.renameTo(liveRoot)
                throw t
            }
            return
        }

        liveRoot.mkdirs()
        val rollbackRoot = File(context.filesDir, "journal_attachments_merge_rollback_${UUID.randomUUID()}")
        rollbackRoot.mkdirs()
        val swapped = mutableListOf<String>()
        try {
            plan.attachmentCopies.values.distinct().forEach { destinationNoteId ->
                val liveDir = File(liveRoot, destinationNoteId)
                val oldDir = File(rollbackRoot, destinationNoteId)
                val stagedDir = File(stagingRoot, destinationNoteId).apply { mkdirs() }
                if (liveDir.exists()) {
                    oldDir.parentFile?.mkdirs()
                    if (!liveDir.renameTo(oldDir)) error("Unable to stage existing media for $destinationNoteId")
                }
                swapped += destinationNoteId
                if (!stagedDir.renameTo(liveDir)) error("Unable to install imported media for $destinationNoteId")
            }
            repo.replaceAllState(plan.state)
            rollbackRoot.deleteRecursively()
        } catch (t: Throwable) {
            swapped.asReversed().forEach { noteId ->
                val liveDir = File(liveRoot, noteId)
                val oldDir = File(rollbackRoot, noteId)
                if (liveDir.exists()) liveDir.deleteRecursively()
                if (oldDir.exists()) oldDir.renameTo(liveDir)
            }
            rollbackRoot.deleteRecursively()
            throw t
        }
    }

    private fun extractSelectedAttachments(
        zip: ZipFile,
        stagingRoot: File,
        sourceToDestination: Map<String, String>
    ): Int {
        var count = 0
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith("attachments/")) continue
            val parts = entry.name.split('/')
            if (parts.size < 3) continue
            val sourceNoteId = parts[1]
            val destinationNoteId = sourceToDestination[sourceNoteId] ?: continue
            val relative = parts.drop(2).joinToString("/")
            val destinationRoot = File(stagingRoot, destinationNoteId)
            val destination = secureChild(destinationRoot, relative)
            destination.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                FileOutputStream(destination).buffered().use { input.copyTo(it) }
            }
            count += 1
        }
        return count
    }

    private fun collectAttachmentFiles(payload: BackupPayload): Map<String, List<File>> {
        val versionsByNote = payload.versions.groupBy { it.noteId }
        return payload.notes.associate { note ->
            val candidates = linkedMapOf<String, File>()
            val ownDir = File(context.filesDir, "journal_attachments/${note.id}")
            if (ownDir.isDirectory) {
                ownDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    candidates.putIfAbsent(safeFileName(file.name), file)
                }
            }
            val canvases = buildList {
                add(note.canvas)
                versionsByNote[note.id].orEmpty().forEach { add(it.canvas) }
            }
            canvases.forEach { canvas ->
                canvas.items.filterIsInstance<AttachmentItem>().forEach { item ->
                    val file = File(item.uri)
                    if (file.isFile) candidates.putIfAbsent(safeFileName(file.name), file)
                }
            }
            note.id to candidates.values.toList()
        }
    }

    private fun restoreVisualSettings(settings: BackupVisualSettings, visuals: VisualPreferences) {
        visuals.saveTheme(
            TomeVisualTheme(
                name = settings.themeName,
                primary = settings.primary,
                secondary = settings.secondary,
                accent = settings.accent,
                background = settings.background,
                surface = settings.surface,
                parchment = settings.parchment,
                ink = settings.ink,
                material = settings.material,
                textureStrength = settings.textureStrength,
                glowStrength = settings.glowStrength,
                animationScale = settings.animationScale
            )
        )
        val layout = runCatching { LibraryLayoutMode.valueOf(settings.libraryLayout) }
            .getOrDefault(LibraryLayoutMode.GRID)
        visuals.saveLibraryLayout(layout)
        val editor = context.getSharedPreferences("ink_toolbar_preferences", Context.MODE_PRIVATE).edit()
        if (settings.toolbarDock == null) editor.remove("dock") else editor.putString("dock", settings.toolbarDock)
        if (settings.toolbarToolOrder == null) editor.remove("tool_order") else editor.putString("tool_order", settings.toolbarToolOrder)
        if (settings.toolbarFavoriteColors == null) editor.remove("favorite_colors") else editor.putString("favorite_colors", settings.toolbarFavoriteColors)
        if (settings.toolbarLegacyVisibleTools.isEmpty()) editor.remove("visible_tools")
        else editor.putStringSet("visible_tools", settings.toolbarLegacyVisibleTools.toSet())
        editor.apply()
    }

    private fun validatePayload(payload: BackupPayload) {
        validateFormat(payload.formatVersion)
        require(payload.nodes.map { it.id }.toSet().size == payload.nodes.size) { "Backup contains duplicate Field/Topic IDs." }
        require(payload.notes.map { it.id }.toSet().size == payload.notes.size) { "Backup contains duplicate Note IDs." }
        val nodeIds = payload.nodes.mapTo(mutableSetOf()) { it.id }
        val noteIds = payload.notes.mapTo(mutableSetOf()) { it.id }
        payload.nodes.forEach { node ->
            require(node.parentId == null || node.parentId in nodeIds) { "Backup hierarchy is incomplete." }
        }
        payload.notes.forEach { note -> require(note.topicId in nodeIds) { "A Note references a missing Topic." } }
        payload.versions.forEach { version -> require(version.noteId in noteIds) { "History references a missing Note." } }
    }

    private fun validateFormat(version: Int) {
        require(version in 1..CURRENT_FORMAT) { "Unsupported backup format version $version." }
    }

    private fun copyUriToTemp(uri: Uri): File {
        val temp = File.createTempFile("tome_import_", BACKUP_EXTENSION, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).buffered().use { input.copyTo(it) }
        } ?: error("Unable to open selected backup.")
        return temp
    }

    private fun rotateBackups(folder: DocumentFile): Int {
        val matching = folder.listFiles()
            .filter { it.isFile && it.name?.startsWith(BACKUP_PREFIX) == true && it.name?.endsWith(BACKUP_EXTENSION) == true }
            .sortedWith(compareByDescending<DocumentFile> { it.lastModified() }.thenByDescending { it.name ?: "" })
        var deleted = 0
        matching.drop(ROTATION_LIMIT).forEach { if (it.delete()) deleted += 1 }
        return deleted
    }

    private fun secureChild(root: File, relative: String): File {
        val target = File(root, relative).canonicalFile
        val canonicalRoot = root.canonicalFile
        require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
            "Unsafe path in backup archive."
        }
        return target
    }

    private fun safeFileName(name: String): String = File(name).name.ifBlank { UUID.randomUUID().toString() }

    private fun timestamp(time: Long): String = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(time))

    companion object {
        private const val PREFS_NAME = "tome_backup_preferences"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"
        private const val KEY_LAST_BACKUP_NAME = "last_backup_name"
        private const val BACKUP_PREFIX = "TomeOfHealing_"
        const val BACKUP_EXTENSION = ".tohbackup"
        const val MIME_TYPE = "application/octet-stream"
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val PAYLOAD_ENTRY = "payload.json"
        private const val ROTATION_LIMIT = 5
        private const val CURRENT_FORMAT = 1
        private const val APP_VERSION = "0.9.0-m9"
    }
}
