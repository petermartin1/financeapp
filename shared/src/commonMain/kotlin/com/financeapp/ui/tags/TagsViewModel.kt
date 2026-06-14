package com.financeapp.ui.tags

import com.financeapp.ui.supervisedViewModelScope

import com.financeapp.domain.model.Tag
import com.financeapp.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TagsUiState(
    val tags: List<Tag> = emptyList(),
    val isLoading: Boolean = true
)

class TagsViewModel(
    private val tagRepository: TagRepository
) {
    private val scope = supervisedViewModelScope()

    val uiState: StateFlow<TagsUiState> = tagRepository.getAllTags()
        .map { tags ->
            TagsUiState(
                tags = tags,
                isLoading = false
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = TagsUiState()
        )

    fun addTag(name: String, color: String?) {
        scope.launch {
            val tag = Tag(name = name, color = color)
            tagRepository.insertTag(tag)
        }
    }

    fun updateTag(tag: Tag) {
        scope.launch {
            tagRepository.updateTag(tag)
        }
    }

    fun deleteTag(id: Long) {
        scope.launch {
            tagRepository.deleteTag(id)
        }
    }

    /**
     * Cleanup method to cancel all background coroutines.
     * Should be called when the ViewModel is no longer needed (e.g., in tests).
     */
    fun cleanup() {
        scope.cancel()
    }
}
