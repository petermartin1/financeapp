package com.financeapp.ui.categories

import com.financeapp.ui.supervisedViewModelScope

import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import com.financeapp.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository
) {
    private val scope = supervisedViewModelScope()

    val uiState: StateFlow<CategoriesUiState> = categoryRepository.getAllCategories()
        .map { categories ->
            CategoriesUiState(
                categories = categories,
                isLoading = false
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = CategoriesUiState()
        )

    fun addCategory(
        name: String,
        type: CategoryType,
        parentId: Long?,
        icon: String?,
        color: String?
    ) {
        scope.launch {
            val category = Category(
                name = name,
                type = type,
                parentId = parentId,
                icon = icon,
                color = color
            )
            categoryRepository.insertCategory(category)
        }
    }

    fun deleteCategory(id: Long) {
        scope.launch {
            categoryRepository.deleteCategory(id)
        }
    }

    fun getCategoriesByType(type: CategoryType): List<Category> {
        return uiState.value.categories.filter { it.type == type }
    }

    /**
     * Cleanup method to cancel all background coroutines.
     * Should be called when the ViewModel is no longer needed (e.g., in tests).
     */
    fun cleanup() {
        scope.cancel()
        // No need to wait - cancellation will be processed by test dispatcher
    }
}
