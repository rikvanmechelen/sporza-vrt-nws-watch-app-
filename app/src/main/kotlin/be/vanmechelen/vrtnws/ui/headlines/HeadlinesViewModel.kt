package be.vanmechelen.vrtnws.ui.headlines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.model.Article
import be.vanmechelen.vrtnws.model.NewsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HeadlinesUiState(
    val articles: List<Article> = emptyList(),
    val isRefreshing: Boolean = false,
    /** True when the most recent refresh failed (e.g. offline). */
    val loadFailed: Boolean = false,
) {
    val isInitialLoading: Boolean get() = isRefreshing && articles.isEmpty()
    val showOfflineBanner: Boolean get() = loadFailed && articles.isNotEmpty()
    val showError: Boolean get() = loadFailed && articles.isEmpty() && !isRefreshing
}

class HeadlinesViewModel(
    private val repository: NewsRepository,
    private val source: NewsSource,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val loadFailed = MutableStateFlow(false)

    val uiState: StateFlow<HeadlinesUiState> =
        combine(repository.headlines(source), isRefreshing, loadFailed) { articles, refreshing, failed ->
            HeadlinesUiState(articles = articles, isRefreshing = refreshing, loadFailed = failed)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HeadlinesUiState(isRefreshing = true),
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val result = repository.refresh(source)
            loadFailed.value = result.isFailure
            isRefreshing.value = false
        }
    }
}
