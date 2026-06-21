package es.saniexam.app.domain.repository

import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.CardStateWithQuestion
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Review (PR5) is the ONLY caller; Exam must never touch this repository
 * (spec "Modes stay independent"). PR4 reads the due count to power the
 * Home screen.
 */
interface CardStateRepository {
    fun observeDue(now: Instant, limit: Int): Flow<List<CardState>>
    suspend fun get(questionId: String): CardState?
    suspend fun upsert(state: CardState)
    suspend fun count(): Int
    suspend fun countDue(now: Instant): Int

    /**
     * Snapshot a single card together with its question + options. Used
     * by the Review use cases. Returns null if the [questionId] has no
     * card state row (PR5 requirement: a question without a state is
     * ignored, not lazily created — the bundled pack ships the questions
     * and the FSRS engine creates card states lazily on the first
     * commit; the Review queue only includes rows that already exist).
     */
    suspend fun getWithQuestion(questionId: String): CardStateWithQuestion?

    /**
     * Snapshot of the due queue used by [es.saniexam.app.domain.usecase.GetDueQueueUseCase].
     * The Flow variant is for the Home screen; the suspend variant is
     * for the Review screen which needs a deterministic snapshot to
     * walk while the user commits ratings.
     */
    suspend fun listDue(now: Instant, limit: Int): List<CardStateWithQuestion>

    suspend fun listDueByCategory(now: Instant, category: String, limit: Int): List<CardStateWithQuestion>

    /**
     * Backing-store wipe. Used only by the backup transaction (destructive
     * import). Not a spec-supported mutator of the schedule.
     */
    suspend fun deleteAll()

    /**
     * Bulk replace for backup import. Each row is upserted; the write
     * runs inside the caller's transaction.
     */
    suspend fun replaceAll(states: List<CardState>)
}
