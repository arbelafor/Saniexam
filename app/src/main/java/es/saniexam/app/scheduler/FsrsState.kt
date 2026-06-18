package es.saniexam.app.scheduler

import java.time.Instant

/**
 * Immutable card memory state owned by the FSRS engine. Pure data; no I/O,
 * no Android, no Room. PR3 persists this as a Room row on `CardState`.
 * Field semantics match ts-fsrs `Card`. [schedulerVersion] is a forward-
 * compatibility tag — a future re-tuned engine refuses to mix math
 * versions on the same card (spec "Version mismatch handled").
 *
 * [learningSteps] is an FSRS-6 schedule field (0..2) used by the engine
 * to choose which (re)learning step applies. It is only meaningful when
 * [phase] is [CardPhase.Learning] or [CardPhase.Relearning]; for
 * [CardPhase.New] and [CardPhase.Review] it is always 0. The Review UI
 * (PR5) does not need to read it.
 */
data class FsrsState(
    val stability: Double,    // FSRS S in days (interval at R=90%)
    val difficulty: Double,   // FSRS D in [1.0, 10.0]
    val dueAt: Instant,       // when this card is next due (UTC)
    val lastReviewedAt: Instant?, // null for never-reviewed (New) cards
    val reps: Int,            // total commits
    val lapses: Int,          // count of Again in Review only
    val phase: CardPhase,     // current FSM phase
    val scheduledDays: Int,   // integer-day interval at last commit (UI hint)
    val elapsedDays: Int,     // recomputed at commit time from lastReviewedAt
    val learningSteps: Int,   // 0..2: position in (re)learning step sequence
    val schedulerVersion: Int,
) {
    companion object {
        fun newCard(now: Instant): FsrsState = FsrsState(
            stability = 0.0, difficulty = 0.0,
            dueAt = now, lastReviewedAt = null,
            reps = 0, lapses = 0,
            phase = CardPhase.New,
            scheduledDays = 0, elapsedDays = 0,
            learningSteps = 0,
            schedulerVersion = SchedulerVersion.CURRENT,
        )
    }
}
