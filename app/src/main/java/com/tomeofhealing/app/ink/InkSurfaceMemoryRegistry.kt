package com.tomeofhealing.app.ink

import java.util.Collections
import java.util.WeakHashMap

/** Weak registry so the Activity can release decoded media under Android memory pressure. */
object InkSurfaceMemoryRegistry {
    private val views = Collections.newSetFromMap(WeakHashMap<InkSurfaceView, Boolean>())

    @Synchronized fun register(view: InkSurfaceView) { views.add(view) }
    @Synchronized fun unregister(view: InkSurfaceView) { views.remove(view) }
    @Synchronized fun trimAll() { views.toList().forEach { it.trimCaches() } }
}
