package be.vanmechelen.vrtnws.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.model.ArticleContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArticleUiState {
    data object Loading : ArticleUiState

    /** Body extracted successfully. [content] may still be empty, in which case the UI
     *  shows the "open on phone" fallback. */
    data class Ready(val content: ArticleContent) : ArticleUiState

    /** Fetch/extraction failed; offer "open on phone". */
    data object Failed : ArticleUiState
}

class ArticleViewModel(
    private val repository: NewsRepository,
    private val articleId: String,
    val articleUrl: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ArticleUiState.Loading
            _uiState.value = repository.body(articleId, articleUrl).fold(
                onSuccess = { ArticleUiState.Ready(it) },
                onFailure = { ArticleUiState.Failed },
            )
        }
    }
}
