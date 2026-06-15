package com.financeapp.ui.error

import java.io.File
import kotlin.time.Clock

/**
 * Persistent sink for errors surfaced through [AppErrorBus]. Appends a timestamped line (plus
 * the stack trace for throwables) to a log file so failures can be diagnosed after the fact
 * (R28). The desktop module is JVM-only, so java.io is used directly.
 */
object CrashLog {

    /** Override in tests to capture entries instead of writing to disk. */
    var sink: (String) -> Unit = ::appendToFile

    fun record(message: String, throwable: Throwable? = null) {
        val entry = buildString {
            append(Clock.System.now().toString())
            append("  ")
            append(message)
            if (throwable != null) {
                append('\n')
                append(throwable.stackTraceToString())
            }
        }
        // Logging must never itself crash the caller.
        runCatching { sink(entry) }
    }

    private fun appendToFile(entry: String) {
        val dir = File(System.getProperty("user.home"), ".financeapp/logs")
        dir.mkdirs()
        File(dir, "error.log").appendText(entry + "\n")
    }
}
