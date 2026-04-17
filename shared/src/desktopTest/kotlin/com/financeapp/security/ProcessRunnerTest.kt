package com.financeapp.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessRunnerTest {

    @Test
    fun `test successful command execution`() {
        val result = ProcessRunner.run(listOf("echo", "hello"))
        assertEquals(0, result.exitCode)
        assertEquals("hello", result.stdout)
    }

    @Test
    fun `test command timeout returns negative exit code`() {
        val result = ProcessRunner.run(
            listOf("sleep", "10"),
            timeoutSeconds = 1
        )
        assertEquals(-1, result.exitCode)
        assertTrue(result.stderr.contains("timed out"), "Should report timeout: ${result.stderr}")
    }

    @Test
    fun `test stderr is captured separately`() {
        // Use sh -c to write to stderr
        val result = ProcessRunner.run(
            listOf("sh", "-c", "echo error_output >&2")
        )
        assertEquals(0, result.exitCode)
        assertTrue(result.stderr.contains("error_output"), "stderr should be captured: ${result.stderr}")
    }

    @Test
    fun `test merged stderr goes to stdout`() {
        val result = ProcessRunner.run(
            listOf("sh", "-c", "echo error_output >&2"),
            mergeStderr = true
        )
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("error_output"), "merged stderr should appear in stdout")
        assertEquals("", result.stderr, "stderr should be empty when merged")
    }

    @Test
    fun `test failed command returns non-zero exit code`() {
        val result = ProcessRunner.run(listOf("sh", "-c", "exit 42"))
        assertEquals(42, result.exitCode)
    }

    @Test
    fun `test runWithStdin sends input to process`() {
        val result = ProcessRunner.runWithStdin(
            command = listOf("cat"),
            stdinContent = "hello from stdin"
        )
        assertEquals(0, result.exitCode)
        assertEquals("hello from stdin", result.stdout)
    }

    @Test
    fun `test runWithStdin timeout`() {
        val result = ProcessRunner.runWithStdin(
            command = listOf("sleep", "10"),
            stdinContent = "data",
            timeoutSeconds = 1
        )
        assertEquals(-1, result.exitCode)
    }
}
