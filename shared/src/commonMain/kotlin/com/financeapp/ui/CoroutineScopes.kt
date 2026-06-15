package com.financeapp.ui

import com.financeapp.ui.error.AppErrorBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

/**
 * Launches [block], reporting a specific, user-facing message via [AppErrorBus] if it fails
 * (R16) — e.g. `launchReporting("save the transaction") { ... }` surfaces "Couldn't save the
 * transaction. <detail>". Cancellation is re-thrown so structured concurrency still works.
 */
fun CoroutineScope.launchReporting(
    action: String,
    onError: (Throwable) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit
): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppErrorBus.report("Couldn't $action. ${e.message.orEmpty()}".trim(), e)
        onError(e)
    }
}
