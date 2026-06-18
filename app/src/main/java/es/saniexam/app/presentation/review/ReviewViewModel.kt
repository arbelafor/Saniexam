package es.saniexam.app.presentation.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.saniexam.app.R
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.repository.UserSettingsRepository
import es.saniexam.app.domain.usecase.CommitRatingUseCase
import es.saniexam.app.domain.usecase.GetDueQueueUseCase
import es.saniexam.app.scheduler.FsrsEngine
import es.saniexam.app.scheduler.FsrsState
import es.saniexam.app.scheduler.Rating
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Owns the Review session state machine.
 *
 * Lifecycle:
 *  1. On `init` the ViewModel:
 *     a. Asks `UserSettingsRepository` for the persisted
 *        `lastRevealedCardId` (spec "Interrupt and Resume" — the queue
 *        resumes on the same card with the same reveal state, no
 *        `ReviewLog` row is appended for the un-committed interaction).
 *     b. Calls `GetDueQueueUseCase` to build the queue snapshot.
 *     c. Restores the reveal state on the matching card if the
 *        persisted id is in the current queue.
 *  2. `onReveal` flips the local `revealed` flag, computes the
 *     previews (pure read from the engine), and persists
 *     `lastRevealedCardId` so a process kill leaves the user on the
 *     same card with the same reveal state.
 *  3. `onRate` calls `CommitRatingUseCase` (the **only** writer for
 *     `CardState` + `ReviewLog` + the in-flight `UserSettings`) and
 *     advances the cursor. When the cursor falls off the end of the
 *     queue, [ReviewUiEvent.SessionEnd] is emitted.
 *
 * Stats refresh is the caller's concern: the `Route` wrapper subscribes
 * to [uiEvents] and pops back, where the `HomeViewModel` + Stats
 * `ViewModel` re-sample on next visit.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    application: Application,
    private val getDueQueue: GetDueQueueUseCase,
    private val commitRating: CommitRatingUseCase,
    private val userSettingsRepository: UserSettingsRepository,
    private val engine: FsrsEngine,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ReviewUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<ReviewUiEvent> = _uiEvents.asSharedFlow()

    /** Snapshot of the queue + the cursor. Mutated only on the IO
     *  dispatcher through [start], [onReveal], and [onRate]. */
    private var queue: List<es.saniexam.app.domain.model.CardStateWithQuestion> = emptyList()
    private var cursor: Int = 0
    private var resumedCardId: String? = null

    init { start() }

    fun start() {
        _uiState.value = ReviewUiState.Loading
        viewModelScope.launch {
            try {
                val now = withContext(io) { Instant.now(clock) }
                val items = getDueQueue(now, limit = DEFAULT_QUEUE_LIMIT)
                val persistedResumeId = withContext(io) { userSettingsRepository.get().lastRevealedCardId }
                queue = items
                resumedCardId = persistedResumeId
                if (items.isEmpty()) {
                    _uiState.value = ReviewUiState.Empty
                    return@launch
                }
                val resumeIndex = persistedResumeId
                    ?.let { id -> items.indexOfFirst { it.cardState.questionId == id } }
                    ?.takeIf { it >= 0 }
                    ?: 0
                cursor = resumeIndex
                val current = items[cursor]
                // Restore revealed=true if we resumed on the persisted card.
                if (persistedResumeId != null && current.cardState.questionId == persistedResumeId) {
                    val preview = withContext(io) { engine.preview(current.cardState.toFsrsState(), now) }
                    _uiState.value = ReviewUiState.Active(
                        queuePosition = cursor + 1,
                        queueSize = items.size,
                        current = current,
                        revealed = true,
                        preview = preview,
                    )
                } else {
                    _uiState.value = ReviewUiState.Active(
                        queuePosition = cursor + 1,
                        queueSize = items.size,
                        current = current,
                        revealed = false,
                        preview = null,
                    )
                }
            } catch (t: Throwable) {
                val msg = t.message
                    ?: getApplication<Application>().getString(R.string.review_error_unknown)
                _uiState.value = ReviewUiState.Error(msg)
            }
        }
    }

    fun onReveal() {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (state.revealed) return
        viewModelScope.launch {
            try {
                val now = withContext(io) { Instant.now(clock) }
                val preview = withContext(io) { engine.preview(state.current.cardState.toFsrsState(), now) }
                // Persist the revealed card id so process death can resume
                // here (spec "Interrupt and Resume").
                withContext(io) {
                    val settings = userSettingsRepository.get()
                    if (settings.lastRevealedCardId != state.current.cardState.questionId) {
                        userSettingsRepository.update(
                            settings.copy(lastRevealedCardId = state.current.cardState.questionId),
                        )
                    }
                }
                _uiState.value = state.copy(revealed = true, preview = preview)
            } catch (t: Throwable) {
                val msg = t.message
                    ?: getApplication<Application>().getString(R.string.review_error_unknown)
                _uiState.value = ReviewUiState.Error(msg)
            }
        }
    }

    fun onRate(rating: Rating) {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (!state.revealed) return
        viewModelScope.launch {
            try {
                val now = withContext(io) { Instant.now(clock) }
                commitRating(
                    questionId = state.current.cardState.questionId,
                    rating = rating,
                    now = now,
                )
                advance(now)
            } catch (t: Throwable) {
                val msg = t.message
                    ?: getApplication<Application>().getString(R.string.review_error_unknown)
                _uiState.value = ReviewUiState.Error(msg)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun advance(now: Instant) {
        val next = cursor + 1
        cursor = next
        if (next >= queue.size) {
            _uiState.value = ReviewUiState.Empty
            _uiEvents.tryEmit(ReviewUiEvent.SessionEnd)
            return
        }
        val nextCard = queue[next]
        _uiState.value = ReviewUiState.Active(
            queuePosition = next + 1,
            queueSize = queue.size,
            current = nextCard,
            // A freshly-advanced card is not yet revealed. The user must
            // tap "Mostrar respuesta" again. The CommitRatingUseCase
            // already cleared `lastRevealedCardId` in `UserSettings`.
            revealed = false,
            preview = null,
        )
    }

    private fun CardState.toFsrsState(): FsrsState = FsrsState(
        stability = stability,
        difficulty = difficulty,
        dueAt = dueAt,
        lastReviewedAt = lastReviewedAt,
        reps = reps,
        lapses = lapses,
        phase = phase,
        scheduledDays = scheduledDays,
        elapsedDays = elapsedDays,
        learningSteps = learningSteps,
        schedulerVersion = schedulerVersion,
    )

    private companion object {
        const val DEFAULT_QUEUE_LIMIT: Int = 200
    }
}
