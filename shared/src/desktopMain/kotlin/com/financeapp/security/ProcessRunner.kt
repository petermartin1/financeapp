package com.financeapp.security

import java.util.concurrent.TimeUnit

/**
 * Shared utility for executing external processes with timeout and proper stream handling.
 * Prevents buffer deadlock by draining stdout and stderr on separate threads.
 */
internal object ProcessRunner {
    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    fun run(
        command: List<String>,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        mergeStderr: Boolean = false
    ): Result {
        val process = ProcessBuilder(command)
            .redirectErrorStream(mergeStderr)
            .start()

        var stdoutContent = ""
        var stderrContent = ""

        val stdoutThread = Thread {
            stdoutContent = process.inputStream.bufferedReader().readText().trim()
        }.also { it.isDaemon = true; it.start() }

        val stderrThread = Thread {
            stderrContent = if (!mergeStderr) {
                process.errorStream.bufferedReader().readText().trim()
            } else ""
        }.also { it.isDaemon = true; it.start() }

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            stdoutThread.join(1000)
            stderrThread.join(1000)
            return Result(-1, "", "Process timed out after ${timeoutSeconds}s")
        }

        stdoutThread.join(5000)
        stderrThread.join(5000)
        return Result(process.exitValue(), stdoutContent, stderrContent)
    }

    /**
     * Run a process and write to its stdin before waiting for completion.
     */
    fun runWithStdin(
        command: List<String>,
        stdinContent: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): Result {
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        var stdoutContent = ""
        var stderrContent = ""

        val stdoutThread = Thread {
            stdoutContent = process.inputStream.bufferedReader().readText().trim()
        }.also { it.isDaemon = true; it.start() }

        val stderrThread = Thread {
            stderrContent = process.errorStream.bufferedReader().readText().trim()
        }.also { it.isDaemon = true; it.start() }

        process.outputStream.bufferedWriter().use { it.write(stdinContent) }

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            stdoutThread.join(1000)
            stderrThread.join(1000)
            return Result(-1, "", "Process timed out after ${timeoutSeconds}s")
        }

        stdoutThread.join(5000)
        stderrThread.join(5000)
        return Result(process.exitValue(), stdoutContent, stderrContent)
    }
}

/**
 * Validates that a string contains only valid Base64 characters.
 * Used to prevent injection when interpolating file contents into scripts.
 */
internal fun isValidBase64(input: String): Boolean {
    if (input.isEmpty()) return false
    return input.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' || it == '\n' || it == '\r' }
}
