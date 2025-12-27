package com.financeapp.domain.model

enum class ExportFormat(val displayName: String, val extension: String) {
    CSV("CSV", "csv"),
    OFX("OFX", "ofx")
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.CSV,
    val accountId: Long? = null,  // null = all accounts
    val includeCleared: Boolean = true,
    val includeReconciled: Boolean = true
)

data class BackupResult(
    val success: Boolean,
    val filePath: String? = null,
    val message: String,
    val recordCount: Int = 0
)
