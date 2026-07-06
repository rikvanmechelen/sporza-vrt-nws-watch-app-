package me.vanmechelen.vrtsporza.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.vanmechelen.vrtsporza.data.MatchesRepository
import me.vanmechelen.vrtsporza.model.MatchDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MatchDetailUiState {
    data object Loading : MatchDetailUiState

    /** Detail extracted successfully. [detail] may be empty → UI shows "open op telefoon". */
    data class Ready(val detail: MatchDetail) : MatchDetailUiState

    /** Fetch/extraction failed; offer "open op telefoon". */
    data object Failed : MatchDetailUiState
}

class MatchDetailViewModel(
    private val repository: MatchesRepository,
    val matchUrl: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MatchDetailUiState>(MatchDetailUiState.Loading)
    val uiState: StateFlow<MatchDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = MatchDetailUiState.Loading
            _uiState.value = repository.detail(matchUrl).fold(
                onSuccess = { MatchDetailUiState.Ready(it) },
                onFailure = { MatchDetailUiState.Failed },
            )
        }
    }
}
