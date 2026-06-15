package com.financeapp.ui

import com.financeapp.ui.error.AppErrorBus
import com.financeapp.ui.error.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineScopesTest {

    @Test
    fun `a failing child does not cancel the scope and the error is reported`() = runTest {
        val reported = mutableListOf<Throwable>()
        val scope = supervisedViewModelScope(
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            onError = { reported.add(it) }
        )

        scope.launch { throw RuntimeException("boom") }

        // The scope (and therefore any stateIn collector living in it) must survive.
        assertTrue(scope.isActive)
        assertEquals(1, reported.size)
        assertEquals("boom", reported[0].message)
    }

    @Test
    fun `the scope remains usable after a child fails`() = runTest {
        val scope = supervisedViewModelScope(
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            onError = {}
        )

        scope.launch { throw RuntimeException("first op failed") }

        var ranAfterFailure = false
        scope.launch { ranAfterFailure = true }

        assertTrue(ranAfterFailure)
    }

    @AfterTest
    fun resetCrashLog() {
        CrashLog.sink = { } // avoid leaking captured sinks / writing files across tests
    }

    @Test
    fun `launchReporting surfaces a specific message and logs the failure`() = runTest {
        val logged = mutableListOf<String>()
        CrashLog.sink = { logged.add(it) }

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        scope.launchReporting("save the transaction") {
            throw RuntimeException("db offline")
        }

        assertEquals(1, logged.size)
        assertTrue(logged[0].contains("Couldn't save the transaction. db offline"), logged[0])
        // The throwable's stack trace is recorded for diagnosis.
        assertTrue(logged[0].contains("RuntimeException"), logged[0])
    }

    @Test
    fun `AppErrorBus report logs the throwable with its stack trace`() {
        val logged = mutableListOf<String>()
        CrashLog.sink = { logged.add(it) }

        AppErrorBus.report(IllegalStateException("kaboom"))

        assertEquals(1, logged.size)
        assertTrue(logged[0].contains("kaboom"), logged[0])
        assertTrue(logged[0].contains("IllegalStateException"), logged[0])
    }
}
