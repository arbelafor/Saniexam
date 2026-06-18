package es.saniexam.app.domain.usecase

import androidx.room.withTransaction
import es.saniexam.app.data.db.SaniExamDb
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.repository.CardStateRepository
import es.saniexam.app.domain.repository.ReviewLogRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
import es.saniexam.app.scheduler.FsrsEngine
import es.saniexam.app.scheduler.FsrsPreview
import es.saniexam.app.scheduler.FsrsState
import es.saniexam.app.scheduler.Rating
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * The **only** mutation path for [CardState] and [ReviewLog]
 * (spec `review-session` "Persisted Rating and Append-Only ReviewLog"
 * + design D5 "Review writes; Exam never writes"). Each commit:
 *
 *  1. Loads the current [CardState] (must exist — the queue is built
 *     from existing rows).
 *  2. Converts to [FsrsState] and calls [FsrsEngine.commit].
 *  3. Upserts the resulting [CardState] (new `dueAt`, `stability`,
 *     `difficulty`, `reps`, `lastReviewedAt = now`).
 *  4. Appends exactly one immutable [ReviewLog] row with
 *     `rating`, `reviewedAt = now`, `elapsedDays`, `scheduledDays`,
 *     `previousIntervalDays`, `newIntervalDays`.
 *  5. Updates [es.saniexam.app.domain.model.UserSettings] so the next
 *     cold launch resumes the in-flight session.
 *
 * Steps 3–5 run inside a single [SaniExamDb.withTransaction] so the
 * write is atomic. Spec "Review writes; Exam never writes" is enforced
 * structurally: there is no other caller of `cardStateRepository.upsert`
 * in the production code path.
 */
class CommitRatingUseCase @Inject constructor(
    private val db: SaniExamDb?,
    private val cardStateRepository: CardStateRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val engine: FsrsEngine,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Commit [rating] on [questionId] at [now]. Throws
     * [IllegalArgumentException] when the card does not exist or when
     * the engine's [es.saniexam.app.scheduler.SchedulerVersion] guard
     * refuses a stale row.
     */
    suspend operator fun invoke(
        questionId: String,
        rating: Rating,
        now: Instant,
    ): CommitResult = withContext(io) {
        val previous = cardStateRepository.get(questionId)
            ?: error("CommitRatingUseCase: no CardState for questionId=$questionId")
        val fsrsState = previous.toFsrsState()
        val committed = engine.commit(fsrsState, rating, now)
        val preview = engine.preview(fsrsState, now)
        val newCard = committed.toCardState(previous)
        val log = ReviewLog(
            questionId = questionId,
            reviewedAt = now,
            rating = rating,
            elapsedDays = committed.elapsedDays,
            scheduledDays = committed.scheduledDays,
            previousIntervalDays = previous.scheduledDays,
            newIntervalDays = committed.scheduledDays,
        )
        val tx: suspend (suspend () -> Unit) -> Unit = if (db != null) {
            { block -> db.withTransaction(block) }
        } else {
            // Test path: no real DB → run the block inline. The use case
            // still exercises the upsert / append / update sequence so
            // the assertion surface is the same.
            { block -> block() }
        }
        tx {
            cardStateRepository.upsert(newCard)
            reviewLogRepository.append(log)
            // Session resume: the user has just committed; the next cold
            // launch should start at the queue head (lastRevealedCardId =
            // null) and bump the position counter.
            val current = userSettingsRepository.get()
            userSettingsRepository.update(
                current.copy(
                    lastRevealedCardId = null,
                    lastSessionQueuePosition = current.lastSessionQueuePosition + 1,
                    lastSessionAt = now,
                ),
            )
        }
        CommitResult(
            newCardState = newCard,
            preview = preview,
            log = log,
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

    private fun FsrsState.toCardState(previous: CardState): CardState = CardState(
        questionId = previous.questionId,
        packId = previous.packId,
        packVersion = previous.packVersion,
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
}

/** Returned by [CommitRatingUseCase] so the ViewModel can advance
 *  the queue and emit the next-card previews. */
data class CommitResult(
    val newCardState: CardState,
    val preview: FsrsPreview,
    val log: ReviewLog,
)
