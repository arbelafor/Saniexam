package es.saniexam.app.domain.model

import es.saniexam.app.scheduler.Rating
import java.time.Instant

/**
 * One immutable row per rating the user commits in Review mode. The
 * `progress-stats` spec derives every displayed stat (streak, total,
 * 30-day retention) exclusively from this append-only log.
 *
 * Deferred to PR5 (Review): the data-layer table, the writer
 * (`CommitRatingUseCase`) and the concrete repository implementation
 * are PR5 work. PR4 ships the [es.saniexam.app.domain.model.UserSettings]
 * + [ReviewLog] domain shapes plus stub repositories that return empty
 * data so the Stats screen can be wired and exercised now.
 */
data class ReviewLog(
    val questionId: String,
    val reviewedAt: Instant,
    val rating: Rating,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val previousIntervalDays: Int,
    val newIntervalDays: Int,
)
