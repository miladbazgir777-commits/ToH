package com.tomeofhealing.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tomeofhealing.app.attachments.AttachmentCanvasOps
import com.tomeofhealing.app.attachments.AttachmentStore
import com.tomeofhealing.app.model.AttachmentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun InsertAttachmentDialog(
    onDismiss: () -> Unit,
    onImage: () -> Unit,
    onCamera: () -> Unit,
    onPdf: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert reference material") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onImage, modifier = Modifier.fillMaxWidth()) { Text("Image from gallery/files") }
                Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("Take photo") }
                Button(onClick = onPdf, modifier = Modifier.fillMaxWidth()) { Text("PDF page / excerpt") }
                Text(
                    "Imported files are copied into Tome of Healing so they remain available offline.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun AttachmentListDialog(
    items: List<AttachmentItem>,
    onDismiss: () -> Unit,
    onJump: (AttachmentItem) -> Unit,
    onEdit: (AttachmentItem) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 10.dp) {
            Column(Modifier.widthIn(min = 340.dp, max = 520.dp).heightIn(max = 620.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Canvas objects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                HorizontalDivider()
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text("No images or PDF excerpts in this note.")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(items, key = { it.id }) { item ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onEdit(item) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.originalName ?: if (item.kind == "pdf_page") "PDF page" else "Image",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            when (item.kind) {
                                                "pdf_page" -> "PDF • page ${(item.sourcePage ?: 0) + 1}"
                                                else -> "Image"
                                            } + if (item.locked) " • locked" else "",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    TextButton(onClick = { onJump(item) }) { Text("Jump") }
                                    TextButton(onClick = { onEdit(item) }) { Text("Edit") }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentInspectorDialog(
    item: AttachmentItem,
    onDismiss: () -> Unit,
    onUpdate: ((AttachmentItem) -> AttachmentItem) -> Unit,
    onScale: (Float) -> Unit,
    onBringForward: () -> Unit,
    onSendBackward: () -> Unit,
    onCrop: () -> Unit,
    onJump: () -> Unit,
    onOpenPdf: (() -> Unit)?,
    onDelete: () -> Unit
) {
    var caption by remember(item.id, item.caption) { mutableStateOf(item.caption.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit canvas object") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.originalName ?: item.kind, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onScale(0.85f) }) { Text("Smaller") }
                    OutlinedButton(onClick = { onScale(1.15f) }) { Text("Larger") }
                    OutlinedButton(onClick = { onUpdate { it.copy(rotationDegrees = it.rotationDegrees + 90f) } }) { Text("Rotate 90°") }
                }
                Text("Opacity ${(item.opacity * 100).toInt()}%")
                Slider(
                    value = item.opacity,
                    onValueChange = { value -> onUpdate { it.copy(opacity = value) } },
                    valueRange = 0.10f..1f
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = item.locked,
                        onClick = { onUpdate { it.copy(locked = !it.locked) } },
                        label = { Text(if (item.locked) "Locked" else "Unlocked") }
                    )
                    OutlinedButton(onClick = onCrop) { Text("Crop") }
                    OutlinedButton(onClick = onJump) { Text("Jump") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBringForward) { Text("Bring forward") }
                    OutlinedButton(onClick = onSendBackward) { Text("Send behind") }
                }
                if (onOpenPdf != null) {
                    OutlinedButton(onClick = onOpenPdf, modifier = Modifier.fillMaxWidth()) { Text("Open source PDF") }
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onUpdate { it.copy(caption = caption.trim().ifBlank { null }) }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Apply caption") }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Remove from note") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun CropAttachmentDialog(
    store: AttachmentStore,
    item: AttachmentItem,
    onDismiss: () -> Unit,
    onApply: (AttachmentCanvasOps.CropRect) -> Unit
) {
    var left by remember(item.id) { mutableFloatStateOf(item.cropLeft) }
    var top by remember(item.id) { mutableFloatStateOf(item.cropTop) }
    var right by remember(item.id) { mutableFloatStateOf(item.cropRight) }
    var bottom by remember(item.id) { mutableFloatStateOf(item.cropBottom) }
    val bitmap by rememberAttachmentBitmap(store, item)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 10.dp) {
            Column(Modifier.widthIn(min = 360.dp, max = 620.dp).padding(16.dp)) {
                Text("Crop", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                AttachmentPreview(bitmap, left, top, right, bottom, modifier = Modifier.fillMaxWidth().height(260.dp))
                Spacer(Modifier.height(8.dp))
                CropSliders(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    onLeft = { left = it.coerceAtMost(right - 0.05f) },
                    onTop = { top = it.coerceAtMost(bottom - 0.05f) },
                    onRight = { right = it.coerceAtLeast(left + 0.05f) },
                    onBottom = { bottom = it.coerceAtLeast(top + 0.05f) }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        onApply(AttachmentCanvasOps.CropRect(left, top, right, bottom))
                        onDismiss()
                    }) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
fun PdfImportDialog(
    store: AttachmentStore,
    path: String,
    displayName: String,
    onDismiss: () -> Unit,
    onInsert: (pageIndex: Int, crop: AttachmentCanvasOps.CropRect) -> Unit
) {
    val pageCount = remember(path) { runCatching { store.pdfPageCount(path) }.getOrDefault(0) }
    var page by remember(path) { mutableIntStateOf(0) }
    var cropEnabled by remember(path) { mutableStateOf(false) }
    var left by remember(path, page) { mutableFloatStateOf(0f) }
    var top by remember(path, page) { mutableFloatStateOf(0f) }
    var right by remember(path, page) { mutableFloatStateOf(1f) }
    var bottom by remember(path, page) { mutableFloatStateOf(1f) }
    var bitmap by remember(path, page) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path, page) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { store.renderPdfPage(path, page, 1400) }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 12.dp) {
            Column(Modifier.widthIn(min = 420.dp, max = 720.dp).heightIn(max = 820.dp).padding(16.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text("Page ${if (pageCount == 0) 0 else page + 1} of $pageCount", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                AttachmentPreview(bitmap, left, top, right, bottom, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text("Previous") }
                    FilterChip(selected = cropEnabled, onClick = { cropEnabled = !cropEnabled }, label = { Text("Crop excerpt") })
                    OutlinedButton(onClick = { page = (page + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0)) }, enabled = page < pageCount - 1) { Text("Next") }
                }
                if (cropEnabled) {
                    CropSliders(
                        left, top, right, bottom,
                        onLeft = { left = it.coerceAtMost(right - 0.05f) },
                        onTop = { top = it.coerceAtMost(bottom - 0.05f) },
                        onRight = { right = it.coerceAtLeast(left + 0.05f) },
                        onBottom = { bottom = it.coerceAtLeast(top + 0.05f) }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Button(
                        onClick = {
                            onInsert(page, if (cropEnabled) AttachmentCanvasOps.CropRect(left, top, right, bottom) else AttachmentCanvasOps.CropRect())
                        },
                        enabled = pageCount > 0
                    ) { Text(if (cropEnabled) "Insert excerpt" else "Insert page") }
                }
                Text(
                    "The original PDF remains intact. Ink added on the note is stored separately as vector annotation.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun CropSliders(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onLeft: (Float) -> Unit,
    onTop: (Float) -> Unit,
    onRight: (Float) -> Unit,
    onBottom: (Float) -> Unit
) {
    Text("Left ${(left * 100).toInt()}%")
    Slider(left, onLeft, valueRange = 0f..0.95f)
    Text("Top ${(top * 100).toInt()}%")
    Slider(top, onTop, valueRange = 0f..0.95f)
    Text("Right ${(right * 100).toInt()}%")
    Slider(right, onRight, valueRange = 0.05f..1f)
    Text("Bottom ${(bottom * 100).toInt()}%")
    Slider(bottom, onBottom, valueRange = 0.05f..1f)
}

@Composable
private fun rememberAttachmentBitmap(store: AttachmentStore, item: AttachmentItem): State<Bitmap?> {
    val state = remember(item.uri, item.kind, item.sourcePage) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri, item.kind, item.sourcePage) {
        state.value = withContext(Dispatchers.IO) { store.renderItem(item, 1400) }
    }
    return state
}

@Composable
private fun AttachmentPreview(
    bitmap: Bitmap?,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier.background(Color(0xFF211B2B)), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            // Preview shows the whole source plus a translucent crop mask description rather than
            // allocating another cropped bitmap on every slider tick.
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                contentScale = ContentScale.Fit
            )
            if (left > 0.001f || top > 0.001f || right < 0.999f || bottom < 0.999f) {
                Surface(
                    color = Color.Black.copy(alpha = 0.48f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                ) {
                    Text(
                        "Crop: L${(left * 100).toInt()} T${(top * 100).toInt()} R${(right * 100).toInt()} B${(bottom * 100).toInt()}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
