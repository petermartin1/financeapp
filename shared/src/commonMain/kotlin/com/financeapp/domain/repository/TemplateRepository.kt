package com.financeapp.domain.repository

import com.financeapp.domain.model.TransactionTemplate
import com.financeapp.domain.model.TransactionTemplateWithDetails
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun getAllTemplates(): Flow<List<TransactionTemplateWithDetails>>
    suspend fun getTemplateById(id: Long): TransactionTemplate?
    suspend fun insertTemplate(template: TransactionTemplate): Long
    suspend fun updateTemplate(template: TransactionTemplate)
    suspend fun deleteTemplate(id: Long)

    // Notification method for reactive updates
    fun notifyTemplatesChanged()
}
