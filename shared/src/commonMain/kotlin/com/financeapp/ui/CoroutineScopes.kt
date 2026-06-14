package com.financeapp.ui

import com.financeapp.ui.error.AppErrorBus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Creates the scope a ViewModel uses for its `launch {}` work and `stateIn` collectors.
 *
 * Uses a [SupervisorJob] so a failure in one `launch {}` does not cancel sibling coroutines
 * or the scope itself (which previously tore down the `stateIn` collector and silently froze
 * the screen — N6), and a [CoroutineExceptionHandler] so an otherwise-uncaught exception is
 * surfaced via [AppErrorBus] instead of crashing or being swallowed.
 */
fun supervisedViewModelScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    onError: (Throwable) -> Unit = AppErrorBus::report
): CoroutineScope = CoroutineScope(
    dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, throwable -> onError(throwable) }
)
