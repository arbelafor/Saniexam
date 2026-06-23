package es.saniexam.app.data.repository

import es.saniexam.app.data.dao.CardStateDao
import es.saniexam.app.data.dao.OptionDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.entity.toDomain
import es.saniexam.app.data.entity.toEntity
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.CardStateWithQuestion
import es.saniexam.app.domain.repository.CardStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardStateRepositoryImpl @Inject constructor(
    private val dao: CardStateDao,
    private val questionDao: QuestionDao,
    private val optionDao: OptionDao,
) : CardStateRepository {
    override fun observeDue(now: Instant, limit: Int): Flow<List<CardState>> =
        dao.observeDue(now.toEpochMilli(), limit).map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(questionId: String): CardState? = dao.get(questionId)?.toDomain()
    override suspend fun upsert(state: CardState) { dao.upsert(state.toEntity()) }
    override suspend fun count(): Int = dao.count()
    override suspend fun countDue(now: Instant): Int = dao.countDue(now.toEpochMilli())

    override suspend fun getWithQuestion(questionId: String): CardStateWithQuestion? {
        val card = dao.get(questionId)?.toDomain() ?: return null
        val question = questionDao.get(card.questionId)?.toDomain() ?: return null
        val options = optionDao.forQuestion(card.questionId).map { it.toDomain() }
        return CardStateWithQuestion(card, question, options)
    }

    override suspend fun listDue(now: Instant, limit: Int): List<CardStateWithQuestion> {
        // Snapshot the due flow, then bundle each row with its question + options.
        // The Flow's first emission is the current Room state; the suspend read
        // here is safe because the DAO sorts by due_at ASC and the caller does
        // not race with concurrent writes.
        val cards = dao.observeDue(now.toEpochMilli(), limit).first()
        return cards.mapNotNull { entity ->
            val card = entity.toDomain()
            val question = questionDao.get(card.questionId)?.toDomain() ?: return@mapNotNull null
            val options = optionDao.forQuestion(card.questionId).map { it.toDomain() }
            CardStateWithQuestion(card, question, options)
        }
    }

    override suspend fun listDueByCategory(
        now: Instant,
        category: String,
        limit: Int,
    ): List<CardStateWithQuestion> {
        val cards = dao.listDueByCategory(now.toEpochMilli(), category, limit)
        return cards.mapNotNull { entity ->
            val card = entity.toDomain()
            val question = questionDao.get(card.questionId)?.toDomain() ?: return@mapNotNull null
            val options = optionDao.forQuestion(card.questionId).map { it.toDomain() }
            CardStateWithQuestion(card, question, options)
        }
    }

    override suspend fun deleteAll() { dao.deleteAll() }

    override suspend fun replaceAll(states: List<CardState>) {
        dao.deleteAll()
        if (states.isNotEmpty()) dao.upsertAll(states.map { it.toEntity() })
    }
}
