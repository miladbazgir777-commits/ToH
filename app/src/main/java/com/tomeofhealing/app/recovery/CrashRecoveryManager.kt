package com.tomeofhealing.app.recovery

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal, local-only crash/session recorder. No telemetry is transmitted.
 * A dirty marker lets the next launch know that Android did not close the prior process cleanly.
 */
class CrashRecoveryManager(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "diagnostics").apply { mkdirs() }
    private val sessionMarker = File(directory, "session.dirty")
    private val crashLog = File(directory, "last_crash.txt")
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    val previousSessionUnclean: Boolean
        get() = sessionMarker.exists()

    fun beginSession() {
        sessionMarker.writeText(System.currentTimeMillis().toString())
    }

    fun markCleanExit() {
        runCatching { sessionMarker.delete() }
    }

    fun install() {
        if (previousHandler != null) return
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
                crashLog.writeText(
                    buildString {
                        appendLine("Tome of Healing local crash report")
                        appendLine("Time: $stamp")
                        appendLine("Thread: ${thread.name}")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine()
                        appendLine(throwable.stackTraceToString())
                    }
                )
            }
            val handler = previousHandler
            if (handler != null) {
                handler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun lastCrashText(): String? = runCatching { crashLog.takeIf(File::exists)?.readText() }.getOrNull()
    fun clearCrashReport() { runCatching { crashLog.delete() } }
}
