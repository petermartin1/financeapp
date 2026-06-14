package com.financeapp.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
}
