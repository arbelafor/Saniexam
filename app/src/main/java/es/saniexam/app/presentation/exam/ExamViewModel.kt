package es.saniexam.app.presentation.exam

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.saniexam.app.R
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.model.ExamSession
import es.saniexam.app.domain.usecase.EmptyPackException
import es.saniexam.app.domain.usecase.NoActivePackException
import es.saniexam.app.domain.usecase.RunExamSessionUseCase
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
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Owns the in-memory Exam session (spec `exam-simulation`).
 *
 * **No FSRS Perturbation** — the only collaborators are
 * [RunExamSessionUseCase] (read + score) and the in-process
 * [Clock]. The ViewModel never injects
 * [es.saniexam.app.domain.repository.CardStateRepository] or
 * [es.saniexam.app.domain.repository.ReviewLogRepository] and never
 * calls [es.saniexam.app.domain.usecase.CommitRatingUseCase]. The
 * `ExamViewModelTest` and `RunExamSessionUseCaseTest` assert this
 * contract.
 *
 * Lifecycle:
 *  1. [start] asks the use case to build an [ExamSession]. The
 *     in-memory [ExamSession] is the only source of truth.
 *  2. [tick] is called by the [ExamRoute] coroutine every ~500ms.
 *     It recomputes the remaining seconds; when it crosses zero,
 *     the ViewModel auto-submits and transitions to [ExamUiState.Results].
 *  3. [selectSingle] / [toggleOption] update the session's `answers`
 *     map immutably. The Composable re-renders.
 *  4. [submitEarly] / [acknowledgeResults] produce
 *     [ExamUiState.Results] and (on acknowledge) emit
 *     [ExamUiEvent.SessionEnd] for the NavGraph to pop.
 */
@HiltViewModel
class ExamViewModel @Inject constructor(
    application: Application,
    private val runExamSession: RunExamSessionUseCase,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ExamUiState>(ExamUiState.Loading)
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ExamUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<ExamUiEvent> = _uiEvents.asSharedFlow()

    /** In-memory session. Mutated only via [selectSingle] /
     *  [toggleOption] which call the use case's pure [withSelection]
     *  helper. Never persisted to disk. */
    private var session: ExamSession? = null

    /** Last `Instant.now(clock)` the timer saw. The [ExamRoute]
     *  coroutine updates this every tick. */
    private var lastTick: Instant? = null

    init { start() }

    /**
     * Expose the injected [Clock] to the [ExamRoute] coroutine so the
     * tick is computed from the same time source as the use case. Prior
     * to PR7 the route used [Clock.systemDefaultZone] directly which
     * could drift on devices where the system zone is not UTC. Tests
     * continue to drive [tick] directly with a controlled [Instant]
     * via this VM, so the contract is unchanged.
     */
    fun now(): Instant = Instant.now(clock)

    fun start() {
        _uiState.value = ExamUiState.Loading
        viewModelScope.launch {
            try {
                val now = withContext(io) { Instant.now(clock) }
                val s = runExamSession.start(now = now)
                session = s
                lastTick = now
                _uiState.value = ExamUiState.Active(
                    session = s,
                    remainingSeconds = s.durationSeconds,
                    currentIndex = 0,
                )
            } catch (e: NoActivePackException) {
                _uiState.value = ExamUiState.Error(
                    getApplication<Application>().getString(R.string.exam_error_no_active_pack),
                )
            } catch (e: EmptyPackException) {
                _uiState.value = ExamUiState.Error(
                    getApplication<Application>().getString(R.string.exam_error_empty_pack),
                )
            } catch (t: Throwable) {
                val msg = t.message
                _uiState.value = ExamUiState.Error(
                    msg ?: getApplication<Application>().getString(R.string.exam_error_unknown),
                )
            }
        }
    }

    /**
     * Recompute the countdown. If the timer has expired, auto-submit
     * (spec "Timer expires — unanswered questions are scored as
     * incorrect"). The coroutine in [ExamRoute] calls this on a
     * fixed cadence; the unit tests call it directly with a
     * controlled `now` to exercise the auto-submit path.
     */
    fun tick(now: Instant) {
        val active = _uiState.value as? ExamUiState.Active ?: return
        val s = active.session
        val elapsed = Duration.between(s.startedAt, now).seconds.coerceAtLeast(0L)
        val remaining = (s.durationSeconds - elapsed).coerceAtLeast(0L)
        lastTick = now
        if (remaining == 0L) {
            submit(now = now, emitEvent = false)
        } else if (remaining != active.remainingSeconds) {
            _uiState.value = active.copy(remainingSeconds = remaining)
        }
    }

    /** Move the current-card cursor. Clamped to `[0, size-1]`. */
    fun goTo(index: Int) {
        val active = _uiState.value as? ExamUiState.Active ?: return
        val clamped = index.coerceIn(0, active.session.questions.size - 1)
        if (clamped == active.currentIndex) return
        _uiState.value = active.copy(currentIndex = clamped)
    }

    /** Single-correct selection: replaces the current selection. */
    fun selectSingle(questionId: String, optionId: String) {
        val current = session ?: return
        val updated = runExamSession.withSelection(current, questionId, setOf(optionId))
        session = updated
        publishActive(updated)
    }

    /** Multi-correct selection: toggles [optionId] in the current
     *  selection. An empty selection is the "blank" state (scored
     *  incorrect by the use case). */
    fun toggleOption(questionId: String, optionId: String) {
        val current = session ?: return
        val before = current.answers[questionId].orEmpty()
        val after = if (optionId in before) before - optionId else before + optionId
        val updated = runExamSession.withSelection(current, questionId, after)
        session = updated
        publishActive(updated)
    }

    /** "Entregar" early submit (spec "User submits early"). */
    fun submitEarly() {
        submit(now = Instant.now(clock), emitEvent = false)
    }

    /** User tapped "Volver al inicio" on the results screen. */
    fun acknowledgeResults() {
        viewModelScope.launch { _uiEvents.tryEmit(ExamUiEvent.SessionEnd) }
    }

    private fun submit(now: Instant, emitEvent: Boolean) {
        val s = session ?: return
        val results = runExamSession.score(s, now)
        _uiState.value = ExamUiState.Results(session = s, results = results)
        if (emitEvent) {
            viewModelScope.launch { _uiEvents.tryEmit(ExamUiEvent.SessionEnd) }
        }
    }

    private fun publishActive(updated: ExamSession) {
        val active = _uiState.value as? ExamUiState.Active ?: return
        val elapsed = lastTick?.let { Duration.between(updated.startedAt, it).seconds }
            ?: active.remainingSeconds
        val remaining = (updated.durationSeconds - elapsed).coerceAtLeast(0L)
        _uiState.value = active.copy(session = updated, remainingSeconds = remaining)
    }
}
