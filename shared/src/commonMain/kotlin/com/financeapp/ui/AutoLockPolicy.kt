package com.financeapp.ui

object AutoLockPolicy {
    fun shouldLock(lastActivityMs: Long, nowMs: Long, timeoutMs: Long): Boolean =
        nowMs - lastActivityMs >= timeoutMs
}
