package com.tomeofhealing.app.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tomeofhealing.app.model.CanvasDocument
import com.tomeofhealing.app.model.NoteSection

/** Imperative bridge used by the section navigator to jump inside the native ink viewport. */
class InkCanvasController internal constructor() {
    internal var view: InkSurfaceView? = null
    fun scrollToDocumentY(y: Float) { view?.scrollToDocumentY(y) }
    fun currentViewportCenterDocumentY(): Float = view?.currentViewportCenterDocumentY() ?: 0f
}

@Composable
fun rememberInkCanvasController(): InkCanvasController = remember { InkCanvasController() }

/** Compose host for the Android stylus surface. */
@Composable
fun InkCanvas(
    document: CanvasDocument,
    sections: List<NoteSection> = emptyList(),
    onDocumentChanged: (CanvasDocument) -> Unit,
    onDocumentMetaChanged: (CanvasDocument) -> Unit = {},
    toolConfig: InkToolConfig,
    paperPreset: String,
    modifier: Modifier = Modifier,
    controller: InkCanvasController = rememberInkCanvasController(),
    onTemporaryEraserChanged: (Boolean) -> Unit = {},
    onSelectionChanged: (Int) -> Unit = {},
    onSectionGrowthRequested: (String, Float) -> Unit = { _, _ -> },
    onLinkActivated: (String) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            InkSurfaceView(context).also { view ->
                controller.view = view
                view.document = document
                view.sections = sections
                view.toolConfig = toolConfig
                view.paperPreset = paperPreset
                view.onDocumentCommitted = onDocumentChanged
                view.onDocumentMetaChanged = onDocumentMetaChanged
                view.onTemporaryEraserChanged = onTemporaryEraserChanged
                view.onSelectionChanged = onSelectionChanged
                view.onSectionGrowthRequested = onSectionGrowthRequested
                view.onLinkActivated = onLinkActivated
            }
        },
        update = { view ->
            controller.view = view
            view.document = document
            view.sections = sections
            view.toolConfig = toolConfig
            view.paperPreset = paperPreset
            view.onDocumentCommitted = onDocumentChanged
            view.onDocumentMetaChanged = onDocumentMetaChanged
            view.onTemporaryEraserChanged = onTemporaryEraserChanged
            view.onSelectionChanged = onSelectionChanged
            view.onSectionGrowthRequested = onSectionGrowthRequested
            view.onLinkActivated = onLinkActivated
        }
    )
}
