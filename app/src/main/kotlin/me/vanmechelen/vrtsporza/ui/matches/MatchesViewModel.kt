package me.vanmechelen.vrtsporza.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.vanmechelen.vrtsporza.data.MatchesRepository
import me.vanmechelen.vrtsporza.model.Match
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MatchesUiState(
    val matches: List<Match> = emptyList(),
    val isRefreshing: Boolean = false,
    /** True when the most recent refresh failed (e.g. offline). */
    val loadFailed: Boolean = false,
    /** When the calendar was last successfully synced (epoch ms); null until the first sync. */
    val lastSyncedEpochMs: Long? = null,
) {
    val isInitialLoading: Boolean get() = isRefreshing && matches.isEmpty()
    val showOfflineBanner: Boolean get() = loadFailed && matches.isNotEmpty()
    val showError: Boolean get() = loadFailed && matches.isEmpty() && !isRefreshing
}

/** Per-source ViewModel for the Matches section, mirroring [me.vanmechelen.vrtsporza.ui.headlines.HeadlinesViewModel]. */
class MatchesViewModel(
    private val repository: MatchesRepository,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val loadFailed = MutableStateFlow(false)

    val uiState: StateFlow<MatchesUiState> =
        combine(
            repository.matches(),
            isRefreshing,
            loadFailed,
            repository.lastSyncedAt(),
        ) { matches, refreshing, failed, syncedAt ->
            MatchesUiState(
                matches = matches,
                isRefreshing = refreshing,
                loadFailed = failed,
                lastSyncedEpochMs = syncedAt,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MatchesUiState(isRefreshing = true),
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val result = repository.refresh()
            loadFailed.value = result.isFailure
            isRefreshing.value = false
        }
    }
}
