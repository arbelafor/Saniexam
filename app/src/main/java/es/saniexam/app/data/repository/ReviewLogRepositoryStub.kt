package es.saniexam.app.data.repository

import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.repository.ReviewLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **PR4 stub.** PR5 swaps this for a Room-backed implementation
 * ([ReviewLogRepositoryImpl]) that reads the `review_log` table
 * written by `CommitRatingUseCase`. The stub is kept in the source
 * tree so reviewers can see what PR4 shipped; the [BackupModule]
 * binds the real impl now and the stub is no longer injected.
 */
@Singleton
class ReviewLogRepositoryStub @Inject constructor() : ReviewLogRepository {
    private val empty = MutableStateFlow<List<ReviewLog>>(emptyList())
    override fun observeAll(): Flow<List<ReviewLog>> = empty.asStateFlow()
    override suspend fun count(): Int = 0
    override suspend fun append(log: ReviewLog) = error("ReviewLogRepositoryStub.append: use ReviewLogRepositoryImpl")
    override suspend fun snapshot(): List<ReviewLog> = emptyList()
    override suspend fun replaceAll(logs: List<ReviewLog>) =
        error("ReviewLogRepositoryStub.replaceAll: use ReviewLogRepositoryImpl")
}
