package es.saniexam.app.presentation.review

import es.saniexam.app.domain.model.CardStateWithQuestion
import es.saniexam.app.scheduler.FsrsPreview
import es.saniexam.app.scheduler.Rating

/**
 * Sealed state machine for the Review surface. The Composable renders
 * the four documented states explicitly (spec `review-session`):
 *  - [Loading] — the queue is being built.
 *  - [Empty] — no due cards; UI shows "no hay repasos pendientes" and
 *    the screen does **not** auto-advance.
 *  - [Active] — a card is on screen, [revealed] is false before the
 *    user taps "Mostrar respuesta" and true after. [preview] is null
 *    pre-reveal and populated post-reveal so the four rating buttons
 *    can show interval hints.
 *  - [Error] — unrecoverable; carries the underlying message. The
 *    surface is read-only with respect to questions/options, so the
 *    only error path is the engine's `IllegalArgumentException` on a
 *    stale [es.saniexam.app.scheduler.SchedulerVersion].
 */
sealed interface ReviewUiState {
    data object Loading : ReviewUiState

    data object Empty : ReviewUiState

    data class Active(
        val queuePosition: Int,
        val queueSize: Int,
        val current: CardStateWithQuestion,
        val revealed: Boolean,
        val preview: FsrsPreview?,
    ) : ReviewUiState

    data class Error(val message: String) : ReviewUiState
}

/**
 * One-shot side effects emitted alongside the persistent UI state.
 * Consumed by the `Route` wrapper to drive navigation (session end →
 * pop back + refresh Stats).
 */
sealed interface ReviewUiEvent {
    /** Queue exhausted; the navigation graph should pop back to Home
     *  and request a Stats refresh. */
    data object SessionEnd : ReviewUiEvent
}

/** Hint label for a rating, derived from a preview state. Pure data;
 *  the Composable maps each [Rating] to a localised string. */
data class IntervalHint(
    val rating: Rating,
    val label: String,
    val description: String,
)
