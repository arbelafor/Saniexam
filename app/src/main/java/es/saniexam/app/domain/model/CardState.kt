package es.saniexam.app.domain.model

import es.saniexam.app.scheduler.CardPhase
import es.saniexam.app.scheduler.SchedulerVersion
import java.time.Instant

/**
 * Pure-Kotlin projection of the FSRS engine's [es.saniexam.app.scheduler.FsrsState]
 * for persistence. Carries [packId] + [packVersion] so future pack/algorithm
 * updates are non-destructive. [schedulerVersion] guards against silent
 * mixing of math versions on the same card.
 *
 * [learningSteps] is the FSRS-6 schedule field (0..2). Only meaningful
 * when [phase] is `Learning` or `Relearning`.
 */
data class CardState(
    val questionId: String,
    val packId: String,
    val packVersion: Int,
    val stability: Double,
    val difficulty: Double,
    val dueAt: Instant,
    val lastReviewedAt: Instant?,
    val reps: Int,
    val lapses: Int,
    val phase: CardPhase,
    val scheduledDays: Int,
    val elapsedDays: Int,
    val learningSteps: Int,
    val schedulerVersion: Int = SchedulerVersion.CURRENT,
)
