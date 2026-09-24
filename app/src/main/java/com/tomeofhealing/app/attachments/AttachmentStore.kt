package com.tomeofhealing.app.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.tomeofhealing.app.model.AttachmentItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Owns imported media bytes. SAF/camera sources are copied into the app-private journal store,
 * so notes continue to render offline even when the original provider disappears.
 */
class AttachmentStore(private val context: Context) {
    data class StoredAttachment(
        val path: String,
        val displayName: String,
        val mimeType: String?
    )

    data class MediaSize(val width: Int, val height: Int)

    fun importUri(noteId: String, source: Uri): StoredAttachment {
        val resolver = context.contentResolver
        val displayName = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "attachment_${System.currentTimeMillis()}"
        val mime = resolver.getType(source)
        val extension = extensionFor(displayName, mime)
        val destination = newAttachmentFile(noteId, extension)
        resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Unable to open selected attachment." }
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        return StoredAttachment(destination.absolutePath, displayName, mime)
    }

    /** Camera target is created directly in the note's private attachment directory. */
    fun createCameraTarget(noteId: String): StoredAttachment {
        val file = newAttachmentFile(noteId, ".jpg")
        if (!file.exists()) file.createNewFile()
        return StoredAttachment(file.absolutePath, "Camera_${System.currentTimeMillis()}.jpg", "image/jpeg")
    }

    fun shareableUri(path: String): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        File(path)
    )

    fun imageSize(path: String): MediaSize {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return MediaSize(max(1, options.outWidth), max(1, options.outHeight))
    }

    fun pdfPageCount(path: String): Int = openPdf(path) { renderer -> renderer.pageCount }

    fun pdfPageSize(path: String, pageIndex: Int): MediaSize = openPdf(path) { renderer ->
        val safe = pageIndex.coerceIn(0, max(0, renderer.pageCount - 1))
        renderer.openPage(safe).use { page -> MediaSize(page.width, page.height) }
    }

    fun renderPdfPage(path: String, pageIndex: Int, maxDimension: Int = 1800): Bitmap = openPdf(path) { renderer ->
        val safe = pageIndex.coerceIn(0, max(0, renderer.pageCount - 1))
        renderer.openPage(safe).use { page ->
            val scale = (maxDimension.toFloat() / max(page.width, page.height)).coerceAtMost(1f)
            val width = max(1, (page.width * scale).toInt())
            val height = max(1, (page.height * scale).toInt())
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    fun renderImage(path: String, maxDimension: Int = 1800): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun renderItem(item: AttachmentItem, maxDimension: Int = 1800): Bitmap? = when (item.kind) {
        "pdf_page" -> runCatching { renderPdfPage(item.uri, item.sourcePage ?: 0, maxDimension) }.getOrNull()
        else -> renderImage(item.uri, maxDimension)
    }

    fun totalStoredBytes(): Long {
        val root = File(context.filesDir, "journal_attachments")
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun deleteIfOrphan(path: String, referencedPaths: Set<String>) {
        if (path !in referencedPaths) runCatching { File(path).delete() }
    }

    /** Permanently remove every app-private attachment owned by a deleted note. */
    fun deleteNoteDirectory(noteId: String) {
        val dir = File(context.filesDir, "journal_attachments/$noteId")
        if (dir.exists()) runCatching { dir.deleteRecursively() }
    }

    private fun newAttachmentFile(noteId: String, extension: String): File {
        val dir = File(context.filesDir, "journal_attachments/$noteId").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}$extension")
    }

    private fun extensionFor(name: String, mime: String?): String {
        val fromName = name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".${it.lowercase()}" }
        if (fromName != null && fromName.length <= 8) return fromName
        return when (mime) {
            "application/pdf" -> ".pdf"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
    }

    private inline fun <T> openPdf(path: String, block: (PdfRenderer) -> T): T {
        return ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(block)
        }
    }
}
