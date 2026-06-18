package es.saniexam.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.saniexam.app.data.ingest.DatasetImportException
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.repository.CardStateRepository
import es.saniexam.app.domain.repository.DatasetRepository
import es.saniexam.app.domain.repository.QuestionRepository
import es.saniexam.app.domain.usecase.EnsureDatasetImportedUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Owns the Home screen state. The flow is:
 *  1. Emit [HomeUiState.Loading].
 *  2. Idempotently ensure the bundled pack is imported
 *     (`EnsureDatasetImportedUseCase`).
 *  3. Subscribe to `SubjectPack` + counts. If the resulting
 *     observation is empty, emit [HomeUiState.Empty].
 *
 * The ViewModel never mutates state from outside `viewModelScope`; the
 * one-shot import runs on the IO dispatcher. Card-state writes are
 * PR5 work; this VM only reads.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ensureImported: EnsureDatasetImportedUseCase,
    private val datasetRepository: DatasetRepository,
    private val questionRepository: QuestionRepository,
    private val cardStateRepository: CardStateRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { refresh() }

    /** Re-runs the idempotent import + re-samples counts. Safe to call from UI events. */
    fun refresh() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                ensureImported()
                publishReady()
            } catch (e: DatasetImportException) {
                _uiState.value = HomeUiState.Error(e.reason, e.questionId)
            } catch (t: Throwable) {
                // A non-DatasetImportException failure (e.g. I/O) is surfaced
                // as the closest spec reason so the UI can render a single
                // error affordance instead of crashing.
                _uiState.value = HomeUiState.Error(DatasetImportException.Reason.AssetUnreadable)
            }
        }
    }

    private suspend fun publishReady() {
        val packs = datasetRepository.observeActivePacks().first()
        val pack = packs.firstOrNull()
        if (pack == null) {
            _uiState.value = HomeUiState.Empty(pack = null)
            return
        }
        val total = questionRepository.count(pack.id)
        val now = withContext(io) { Instant.now(clock) }
        val due = cardStateRepository.countDue(now)
        _uiState.value = if (total == 0) {
            HomeUiState.Empty(pack = pack)
        } else {
            HomeUiState.Ready(pack = pack, totalQuestions = total, dueToday = due)
        }
    }
}
