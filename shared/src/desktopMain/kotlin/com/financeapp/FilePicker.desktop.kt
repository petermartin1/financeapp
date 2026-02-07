package com.financeapp

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun pickFile(onFileContent: (String) -> Unit) {
    val dialog = FileDialog(null as Frame?, "Select File to Import", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.lowercase().endsWith(".ofx") ||
        name.lowercase().endsWith(".qfx") ||
        name.lowercase().endsWith(".qif") ||
        name.lowercase().endsWith(".csv")
    }
    dialog.isVisible = true

    val directory = dialog.directory
    val filename = dialog.file

    if (directory != null && filename != null) {
        val file = File(directory, filename)
        val maxSize = 100 * 1024 * 1024L // 100 MB limit
        if (file.length() > maxSize) {
            throw IllegalArgumentException("File too large (${file.length()} bytes). Maximum allowed: $maxSize bytes.")
        }
        val content = file.readText()
        onFileContent(content)
    }
}

actual fun saveFile(content: String, suggestedFilename: String, onResult: (Boolean) -> Unit) {
    val dialog = FileDialog(null as Frame?, "Save File", FileDialog.SAVE)
    dialog.file = suggestedFilename
    dialog.isVisible = true

    val directory = dialog.directory
    val filename = dialog.file

    if (directory != null && filename != null) {
        try {
            val file = File(directory, filename)
            file.writeText(content)
            onResult(true)
        } catch (e: Exception) {
            onResult(false)
        }
    } else {
        onResult(false)
    }
}
