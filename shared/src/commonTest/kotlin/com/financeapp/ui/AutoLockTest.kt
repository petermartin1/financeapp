package com.financeapp.ui

import kotlin.test.*

class AutoLockTest {
    @Test fun `locks after the timeout elapses`() {
        assertTrue(AutoLockPolicy.shouldLock(lastActivityMs = 0, nowMs = 10 * 60_000, timeoutMs = 10 * 60_000))
    }
    @Test fun `does not lock before the timeout`() {
        assertFalse(AutoLockPolicy.shouldLock(lastActivityMs = 0, nowMs = 5 * 60_000, timeoutMs = 10 * 60_000))
    }
}
