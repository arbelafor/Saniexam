package es.saniexam.app.data.repository

import es.saniexam.app.data.dao.OptionDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.dao.TopicDao
import es.saniexam.app.data.entity.toDomain
import es.saniexam.app.domain.model.Option
import es.saniexam.app.domain.model.Question
import es.saniexam.app.domain.model.Topic
import es.saniexam.app.domain.repository.OptionRepository
import es.saniexam.app.domain.repository.QuestionRepository
import es.saniexam.app.domain.repository.TopicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepositoryImpl @Inject constructor(
    private val questionDao: QuestionDao,
) : QuestionRepository {
    override fun observeAll(packId: String): Flow<List<Question>> =
        questionDao.observeAll(packId).map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(id: String): Question? = questionDao.get(id)?.toDomain()
    override suspend fun count(packId: String): Int = questionDao.count(packId)

    override fun observeAllByCategory(category: String): Flow<List<Question>> =
        questionDao.observeAllByCategory(category).map { rows -> rows.map { it.toDomain() } }
    override suspend fun countByCategory(category: String): Int =
        questionDao.countByCategory(category)
}

@Singleton
class OptionRepositoryImpl @Inject constructor(
    private val optionDao: OptionDao,
) : OptionRepository {
    override suspend fun forQuestion(questionId: String): List<Option> =
        optionDao.forQuestion(questionId).map { it.toDomain() }
}

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val topicDao: TopicDao,
) : TopicRepository {
    override fun observeAll(packId: String): Flow<List<Topic>> =
        topicDao.observeAll(packId).map { rows -> rows.map { it.toDomain() } }
    override suspend fun exists(topicId: String, packId: String): Boolean =
        topicDao.exists(topicId, packId)
}
