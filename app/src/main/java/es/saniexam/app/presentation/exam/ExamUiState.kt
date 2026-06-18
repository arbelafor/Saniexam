package es.saniexam.app.presentation.exam

import es.saniexam.app.domain.model.ExamResults
import es.saniexam.app.domain.model.ExamSession

/**
 * Sealed state machine for the Exam surface. The Composable renders
 * the four documented branches explicitly (spec `exam-simulation`):
 *  - [Loading] — the in-memory session is being built from disk.
 *  - [Active] — a session is in progress; [remainingSeconds] is the
 *    countdown. The ViewModel ticks this value; the spec
 *    "Timer expires" path auto-submits at zero and the state
 *    transitions to [Results].
 *  - [Results] — the session is over; the screen shows the summary
 *    and the per-question review list.
 *  - [Error] — the use case could not start the session (no pack,
 *    empty pack, disk failure). The screen offers a "Volver al inicio"
 *    action; the navigation graph pops back to Home.
 */
sealed interface ExamUiState {
    data object Loading : ExamUiState

    data class Active(
        val session: ExamSession,
        val remainingSeconds: Long,
        val currentIndex: Int,
    ) : ExamUiState

    data class Results(
        val session: ExamSession,
        val results: ExamResults,
    ) : ExamUiState

    data class Error(val message: String) : ExamUiState
}

/** One-shot side effects emitted alongside the persistent UI state.
 *  Consumed by the [ExamRoute] wrapper to drive navigation
 *  ("Volver al inicio" → pop back to Home). */
sealed interface ExamUiEvent {
    /** The user has acknowledged the results (or the timer has expired)
     *  and wants to leave the exam. The navigation graph should pop
     *  back to Home. */
    data object SessionEnd : ExamUiEvent
}
