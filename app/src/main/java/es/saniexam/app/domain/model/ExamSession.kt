package es.saniexam.app.domain.model

import java.time.Instant

/**
 * In-memory snapshot of a single mock-exam session. The session is
 * **read-only with respect to FSRS**: it never mutates [CardState],
 * never appends to [ReviewLog], and never calls
 * [es.saniexam.app.domain.usecase.CommitRatingUseCase]. The struct
 * lives only in the [es.saniexam.app.presentation.exam.ExamViewModel]
 * for the duration of the session and is discarded when the user
 * navigates back.
 *
 * @property packId source pack identifier (immutable for the lifetime
 *   of the session; used by the results screen for context).
 * @property packVersion source pack version (same lifetime).
 * @property totalQuestions number of questions in the session
 *   (`<= 50`; deterministic for a given seed).
 * @property durationSeconds countdown length in seconds; the spec
 *   defaults to 50 minutes for a 50-question exam, the dev pack
 *   uses a shorter value for fast smoke tests.
 * @property startedAt wall-clock instant the session began; combined
 *   with [durationSeconds] this drives the auto-submit-on-zero path.
 * @property questions deterministic, shuffled subset of the pack.
 *   Order is stable for the session lifetime.
 * @property answers map from `questionId` to the set of selected
 *   option ids. An absent key means "blank" (the user did not
 *   select any option for that question).
 */
data class ExamSession(
    val packId: String,
    val packVersion: Int,
    val totalQuestions: Int,
    val durationSeconds: Long,
    val startedAt: Instant,
    val questions: List<ExamQuestion>,
    val answers: Map<String, Set<String>> = emptyMap(),
)
