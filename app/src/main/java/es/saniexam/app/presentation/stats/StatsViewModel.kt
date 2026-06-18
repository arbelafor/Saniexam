package es.saniexam.app.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.saniexam.app.domain.usecase.GetStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Stats screen state. The screen is read-only: the VM
 * resolves the [StatsUiState] from [GetStatsUseCase] once and
 * exposes it as a [StateFlow]. The screen does not poll; future
 * PR5 (Review commit) can re-invoke [refresh] from a navigation
 * callback so the screen updates when a session ends.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStats: GetStatsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val stats = getStats()
            _uiState.value = when {
                stats.totalReviews == 0 -> StatsUiState.Empty
                stats.totalReviews < GetStatsUseCase.MIN_RETENTION_SAMPLE -> StatsUiState.Insufficient(stats.totalReviews)
                else -> StatsUiState.Ready(
                    streakDays = stats.streakDays,
                    totalReviews = stats.totalReviews,
                    retention30d = stats.retention30d ?: 0f,
                )
            }
        }
    }
}
