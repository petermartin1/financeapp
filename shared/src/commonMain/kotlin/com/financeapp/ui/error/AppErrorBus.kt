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

    fun report(message: String) {
        _messages.tryEmit(message)
    }

    fun report(throwable: Throwable) {
        report(throwable.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again.")
    }
}
