package es.saniexam.app.domain.repository

import es.saniexam.app.domain.model.ReviewLog
import kotlinx.coroutines.flow.Flow

/**
 * Append-only access to [ReviewLog]. The Review use case
 * ([es.saniexam.app.domain.usecase.CommitRatingUseCase]) is the **only**
 * writer; the backup codec is the **only** consumer of the
 * `snapshot` / `replaceAll` helpers.
 *
 * The `progress-stats` spec derives every displayed stat from
 * `observeAll`; the `review-session` "Append-Only ReviewLog" requirement
 * is enforced structurally (no `update` / `delete` methods on this
 * interface).
 */
interface ReviewLogRepository {
    fun observeAll(): Flow<List<ReviewLog>>
    suspend fun count(): Int

    /**
     * Append one immutable row.
     */
    suspend fun append(log: ReviewLog)

    /** Snapshot for backup export. */
    suspend fun snapshot(): List<ReviewLog>

    /**
     * Replace the entire log inside a caller's transaction. Used only
     * by the backup codec on import; not a spec-supported mutator.
     */
    suspend fun replaceAll(logs: List<ReviewLog>)
}
