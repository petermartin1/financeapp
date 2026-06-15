package com.financeapp.ui.error

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide channel for surfacing user-facing error messages from background work.
 *
 * ViewModels route uncaught coroutine exceptions here (see [com.financeapp.ui.supervisedViewModelScope]),
 * and the UI collects [messages] to show a transient notice (e.g. a Snackbar). This is the
 * shared error channel called for by R16/R28 and underpins the N6 scope hardening.
 */
object AppErrorBus {
    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Surfaces [message] to the UI and records it (with [throwable], if any) to the crash log. */
    fun report(message: String, throwable: Throwable? = null) {
        _messages.tryEmit(message)
        CrashLog.record(message, throwable)
    }

    fun report(throwable: Throwable) {
        report(throwable.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again.", throwable)
    }
}
