package com.tomeofhealing.app.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tomeofhealing.app.model.AttachmentItem
import java.io.File
import kotlin.math.max

/** Small synchronous render cache. Imported assets are app-private, so cache misses are local-only. */
internal class AttachmentBitmapCache(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun clear() { cache.evictAll() }

    fun bitmap(item: AttachmentItem): Bitmap? {
        val key = "${item.kind}|${item.uri}|${item.sourcePage ?: -1}"
        cache.get(key)?.let { if (!it.isRecycled) return it }
        val loaded = when (item.kind) {
            "pdf_page" -> loadPdf(item.uri, item.sourcePage ?: 0)
            else -> loadImage(item.uri)
        } ?: return null
        cache.put(key, loaded)
        return loaded
    }

    private fun loadImage(source: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeSource(source, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 2200) sample *= 2
        return decodeSource(source, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun decodeSource(source: String, options: BitmapFactory.Options): Bitmap? {
        return when {
            source.startsWith("content://") -> context.contentResolver.openInputStream(Uri.parse(source))?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            source.startsWith("file://") -> BitmapFactory.decodeFile(Uri.parse(source).path, options)
            else -> BitmapFactory.decodeFile(source, options)
        }
    }

    private fun loadPdf(source: String, pageIndex: Int): Bitmap? = runCatching {
        openDescriptor(source)?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) {
                    null
                } else {
                    val safe = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(safe).use { page ->
                        val scale = (1800f / max(page.width, page.height)).coerceAtMost(1f)
                        val width = max(1, (page.width * scale).toInt())
                        val height = max(1, (page.height * scale).toInt())
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }.getOrNull()

    private fun openDescriptor(source: String): ParcelFileDescriptor? = when {
        source.startsWith("content://") -> context.contentResolver.openFileDescriptor(Uri.parse(source), "r")
        source.startsWith("file://") -> ParcelFileDescriptor.open(File(Uri.parse(source).path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        else -> ParcelFileDescriptor.open(File(source), ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
