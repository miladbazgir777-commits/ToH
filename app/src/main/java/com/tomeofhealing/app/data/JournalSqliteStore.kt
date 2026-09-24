package com.tomeofhealing.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tomeofhealing.app.model.CanvasDocument
import com.tomeofhealing.app.model.EmblemDefinition
import com.tomeofhealing.app.model.JournalNode
import com.tomeofhealing.app.model.JournalNote
import com.tomeofhealing.app.model.NodeType
import com.tomeofhealing.app.model.NoteSection
import com.tomeofhealing.app.model.NoteVersion
import com.tomeofhealing.app.model.SortMode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal data class JournalSnapshot(
    val nodes: List<JournalNode>,
    val notes: List<JournalNote>,
    val rootSortMode: SortMode
)

/**
 * Small SQLite persistence layer used by Milestone 2.
 *
 * The hierarchy and note metadata are stored in normal SQLite columns so they can be
 * indexed and migrated later. Complex document payloads (emblem, sections, vector
 * canvas) are serialized as JSON per row. This keeps the schema stable while the
 * handwriting model is still evolving.
 */
internal class JournalSqliteStore(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        // WAL reduces writer stalls during handwriting autosave and is resilient to process death.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL("PRAGMA synchronous=NORMAL")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "itemType"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE journal_nodes (
                id TEXT PRIMARY KEY NOT NULL,
                parent_id TEXT,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                modified_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL,
                deletion_locked INTEGER NOT NULL,
                deleted_at INTEGER,
                sort_mode TEXT NOT NULL,
                note_sort_mode TEXT NOT NULL,
                manual_order INTEGER NOT NULL,
                emblem_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_nodes_parent ON journal_nodes(parent_id)")
        db.execSQL("CREATE INDEX idx_nodes_deleted ON journal_nodes(deleted_at)")

        db.execSQL(
            """
            CREATE TABLE journal_notes (
                id TEXT PRIMARY KEY NOT NULL,
                topic_id TEXT NOT NULL,
                title TEXT NOT NULL,
                is_master_summary INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                modified_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL,
                deleted_at INTEGER,
                paper_preset TEXT NOT NULL,
                manual_order INTEGER NOT NULL,
                sections_json TEXT NOT NULL,
                canvas_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_notes_topic ON journal_notes(topic_id)")
        db.execSQL("CREATE INDEX idx_notes_deleted ON journal_notes(deleted_at)")

        createNoteVersionsTable(db)

        db.execSQL(
            """
            CREATE TABLE app_settings (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var version = oldVersion
        if (version < 2) {
            createNoteVersionsTable(db)
            version = 2
        }
        if (version != newVersion) {
            throw IllegalStateException("No migration defined from $version to $newVersion")
        }
    }

    private fun createNoteVersionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS note_versions (
                id TEXT PRIMARY KEY NOT NULL,
                note_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                source_modified_at INTEGER NOT NULL,
                label TEXT NOT NULL,
                sections_json TEXT NOT NULL,
                canvas_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_versions_note_created ON note_versions(note_id, created_at DESC)")
    }

    fun quickCheck(): String = runCatching {
        readableDatabase.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "no result"
        }
    }.getOrElse { "error: ${it.message ?: it::class.java.simpleName}" }

    fun databaseSizeBytes(): Long = runCatching { java.io.File(readableDatabase.path).length() }.getOrDefault(0L)

    fun loadSnapshot(): JournalSnapshot? {
        val db = readableDatabase
        val nodes = mutableListOf<JournalNode>()
        db.query("journal_nodes", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val emblemJson = cursor.getString(cursor.getColumnIndexOrThrow("emblem_json"))
                nodes += JournalNode(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    parentId = cursor.stringOrNull("parent_id"),
                    type = enumOrDefault(cursor.getString(cursor.getColumnIndexOrThrow("type")), NodeType.TOPIC),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    modifiedAt = cursor.getLong(cursor.getColumnIndexOrThrow("modified_at")),
                    pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) != 0,
                    deletionLocked = cursor.getInt(cursor.getColumnIndexOrThrow("deletion_locked")) != 0,
                    deletedAt = cursor.longOrNull("deleted_at"),
                    sortMode = enumOrDefault(cursor.getString(cursor.getColumnIndexOrThrow("sort_mode")), SortMode.MANUAL),
                    noteSortMode = enumOrDefault(cursor.getString(cursor.getColumnIndexOrThrow("note_sort_mode")), SortMode.MANUAL),
                    manualOrder = cursor.getInt(cursor.getColumnIndexOrThrow("manual_order")),
                    emblem = decodeOrDefault(emblemJson, EmblemDefinition()) {
                        json.decodeFromString(EmblemDefinition.serializer(), emblemJson)
                    }
                )
            }
        }

        val notes = mutableListOf<JournalNote>()
        db.query("journal_notes", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val sectionsJson = cursor.getString(cursor.getColumnIndexOrThrow("sections_json"))
                val canvasJson = cursor.getString(cursor.getColumnIndexOrThrow("canvas_json"))
                notes += JournalNote(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    topicId = cursor.getString(cursor.getColumnIndexOrThrow("topic_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    isMasterSummary = cursor.getInt(cursor.getColumnIndexOrThrow("is_master_summary")) != 0,
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    modifiedAt = cursor.getLong(cursor.getColumnIndexOrThrow("modified_at")),
                    pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) != 0,
                    deletedAt = cursor.longOrNull("deleted_at"),
                    paperPreset = cursor.getString(cursor.getColumnIndexOrThrow("paper_preset")),
                    manualOrder = cursor.getInt(cursor.getColumnIndexOrThrow("manual_order")),
                    sections = decodeOrDefault(sectionsJson, emptyList()) {
                        json.decodeFromString(ListSerializer(NoteSection.serializer()), sectionsJson)
                    },
                    canvas = decodeOrDefault(canvasJson, CanvasDocument()) {
                        json.decodeFromString(CanvasDocument.serializer(), canvasJson)
                    }
                )
            }
        }

        val rootSortMode = readSetting(db, SETTING_ROOT_SORT)
            ?.let { enumOrDefault(it, SortMode.MANUAL) }
            ?: SortMode.MANUAL

        // A completely empty database means the app has not been initialized yet.
        if (nodes.isEmpty() && notes.isEmpty() && readSetting(db, SETTING_INITIALIZED) == null) {
            return null
        }
        return JournalSnapshot(nodes, notes, rootSortMode)
    }


    /**
     * Hot-path handwriting save. Unlike structural mutations, ink autosave updates only
     * the current note payload and its owning Topic timestamp, avoiding a full journal
     * snapshot rewrite after every writing pause.
     */
    fun saveCanvas(noteId: String, topicId: String, canvas: CanvasDocument, modifiedAt: Long) {
        val currentSections = readableDatabase.query(
            "journal_notes", arrayOf("sections_json"), "id = ?", arrayOf(noteId), null, null, null, "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) decodeOrDefault(cursor.getString(0), emptyList()) {
                json.decodeFromString(ListSerializer(NoteSection.serializer()), cursor.getString(0))
            } else emptyList()
        }
        saveNoteContent(noteId, topicId, canvas, currentSections, modifiedAt)
    }

    fun saveNoteContent(
        noteId: String,
        topicId: String,
        canvas: CanvasDocument,
        sections: List<NoteSection>,
        modifiedAt: Long
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val noteValues = ContentValues().apply {
                put("canvas_json", json.encodeToString(CanvasDocument.serializer(), canvas))
                put("sections_json", json.encodeToString(ListSerializer(NoteSection.serializer()), sections))
                put("modified_at", modifiedAt)
            }
            db.update("journal_notes", noteValues, "id = ?", arrayOf(noteId))

            val nodeValues = ContentValues().apply { put("modified_at", modifiedAt) }
            db.update("journal_nodes", nodeValues, "id = ?", arrayOf(topicId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadNoteVersions(noteId: String? = null): List<NoteVersion> {
        val selection = if (noteId == null) null else "note_id = ?"
        val args = if (noteId == null) null else arrayOf(noteId)
        val versions = mutableListOf<NoteVersion>()
        readableDatabase.query(
            "note_versions",
            null,
            selection,
            args,
            null,
            null,
            "created_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val sectionsJson = cursor.getString(cursor.getColumnIndexOrThrow("sections_json"))
                val canvasJson = cursor.getString(cursor.getColumnIndexOrThrow("canvas_json"))
                versions += NoteVersion(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    noteId = cursor.getString(cursor.getColumnIndexOrThrow("note_id")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    sourceModifiedAt = cursor.getLong(cursor.getColumnIndexOrThrow("source_modified_at")),
                    label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
                    sections = decodeOrDefault(sectionsJson, emptyList()) {
                        json.decodeFromString(ListSerializer(NoteSection.serializer()), sectionsJson)
                    },
                    canvas = decodeOrDefault(canvasJson, CanvasDocument()) {
                        json.decodeFromString(CanvasDocument.serializer(), canvasJson)
                    }
                )
            }
        }
        return versions
    }

    fun insertNoteVersion(version: NoteVersion, keepLatest: Int = 10) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", version.id)
                put("note_id", version.noteId)
                put("created_at", version.createdAt)
                put("source_modified_at", version.sourceModifiedAt)
                put("label", version.label)
                put("sections_json", json.encodeToString(ListSerializer(NoteSection.serializer()), version.sections))
                put("canvas_json", json.encodeToString(CanvasDocument.serializer(), version.canvas))
            }
            db.insertOrThrow("note_versions", null, values)
            db.execSQL(
                """
                DELETE FROM note_versions
                WHERE note_id = ? AND id NOT IN (
                    SELECT id FROM note_versions
                    WHERE note_id = ?
                    ORDER BY created_at DESC
                    LIMIT ?
                )
                """.trimIndent(),
                arrayOf(version.noteId, version.noteId, keepLatest)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteNoteVersion(versionId: String) {
        writableDatabase.delete("note_versions", "id = ?", arrayOf(versionId))
    }

    fun deleteVersionsForNote(noteId: String) {
        writableDatabase.delete("note_versions", "note_id = ?", arrayOf(noteId))
    }

    fun deleteVersionsForNotes(noteIds: Collection<String>) {
        if (noteIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            noteIds.forEach { db.delete("note_versions", "note_id = ?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun replaceAll(snapshot: JournalSnapshot, versions: List<NoteVersion>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("note_versions", null, null)
            db.delete("journal_notes", null, null)
            db.delete("journal_nodes", null, null)

            snapshot.nodes.forEach { node -> db.insertOrThrow("journal_nodes", null, node.toValues()) }
            snapshot.notes.forEach { note -> db.insertOrThrow("journal_notes", null, note.toValues()) }
            versions.forEach { version ->
                val values = ContentValues().apply {
                    put("id", version.id)
                    put("note_id", version.noteId)
                    put("created_at", version.createdAt)
                    put("source_modified_at", version.sourceModifiedAt)
                    put("label", version.label)
                    put("sections_json", json.encodeToString(ListSerializer(NoteSection.serializer()), version.sections))
                    put("canvas_json", json.encodeToString(CanvasDocument.serializer(), version.canvas))
                }
                db.insertOrThrow("note_versions", null, values)
            }
            putSetting(db, SETTING_ROOT_SORT, snapshot.rootSortMode.name)
            putSetting(db, SETTING_INITIALIZED, "1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveSnapshot(snapshot: JournalSnapshot) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Snapshot replacement is deliberate for the current MVP: it guarantees that
            // multi-object operations (deep duplicate, move, delete tree) are atomically
            // represented on disk. Incremental DAO-style writes can replace this later.
            db.delete("journal_notes", null, null)
            db.delete("journal_nodes", null, null)

            snapshot.nodes.forEach { node -> db.insertOrThrow("journal_nodes", null, node.toValues()) }
            snapshot.notes.forEach { note -> db.insertOrThrow("journal_notes", null, note.toValues()) }

            putSetting(db, SETTING_ROOT_SORT, snapshot.rootSortMode.name)
            putSetting(db, SETTING_INITIALIZED, "1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun JournalNode.toValues() = ContentValues().apply {
        put("id", id)
        putNullable("parent_id", parentId)
        put("type", type.name)
        put("title", title)
        put("created_at", createdAt)
        put("modified_at", modifiedAt)
        put("pinned", if (pinned) 1 else 0)
        put("deletion_locked", if (deletionLocked) 1 else 0)
        putNullable("deleted_at", deletedAt)
        put("sort_mode", sortMode.name)
        put("note_sort_mode", noteSortMode.name)
        put("manual_order", manualOrder)
        put("emblem_json", json.encodeToString(EmblemDefinition.serializer(), emblem))
    }

    private fun JournalNote.toValues() = ContentValues().apply {
        put("id", id)
        put("topic_id", topicId)
        put("title", title)
        put("is_master_summary", if (isMasterSummary) 1 else 0)
        put("created_at", createdAt)
        put("modified_at", modifiedAt)
        put("pinned", if (pinned) 1 else 0)
        putNullable("deleted_at", deletedAt)
        put("paper_preset", paperPreset)
        put("manual_order", manualOrder)
        put("sections_json", json.encodeToString(ListSerializer(NoteSection.serializer()), sections))
        put("canvas_json", json.encodeToString(CanvasDocument.serializer(), canvas))
    }

    private fun readSetting(db: SQLiteDatabase, key: String): String? =
        db.query(
            "app_settings",
            arrayOf("value"),
            "key = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun putSetting(db: SQLiteDatabase, key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("app_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun android.database.Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, default: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(default)

    private inline fun <T> decodeOrDefault(raw: String, default: T, decoder: () -> T): T =
        runCatching(decoder).getOrDefault(default)

    companion object {
        private const val DATABASE_NAME = "tome_of_healing.db"
        private const val DATABASE_VERSION = 2
        private const val SETTING_ROOT_SORT = "root_sort_mode"
        private const val SETTING_INITIALIZED = "initialized"
    }
}
