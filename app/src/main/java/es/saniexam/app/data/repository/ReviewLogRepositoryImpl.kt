package es.saniexam.app.data.repository

import es.saniexam.app.data.dao.ReviewLogDao
import es.saniexam.app.data.entity.toDomain
import es.saniexam.app.data.entity.toEntity
import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.repository.ReviewLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [ReviewLogRepository]. The DAO only supports `insert` and
 * `observeAll` / `getAll` / `count` / `deleteAll` (the last is reserved
 * for the backup codec). The [append] entry point writes a single row
 * per commit and never mutates existing rows.
 */
@Singleton
class ReviewLogRepositoryImpl @Inject constructor(
    private val dao: ReviewLogDao,
) : ReviewLogRepository {
    override fun observeAll(): Flow<List<ReviewLog>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    override suspend fun count(): Int = dao.count()

    override suspend fun append(log: ReviewLog) { dao.insert(log.toEntity()) }

    override suspend fun snapshot(): List<ReviewLog> = dao.getAll().map { it.toDomain() }

    override suspend fun replaceAll(logs: List<ReviewLog>) {
        dao.deleteAll()
        if (logs.isNotEmpty()) dao.insertAll(logs.map { it.toEntity() })
    }
}
