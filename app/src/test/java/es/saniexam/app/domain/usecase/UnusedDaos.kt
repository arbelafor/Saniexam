package es.saniexam.app.domain.usecase

import es.saniexam.app.data.dao.DatasetVersionDao
import es.saniexam.app.data.dao.OptionDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.dao.SubjectPackDao
import es.saniexam.app.data.dao.TopicDao
import es.saniexam.app.data.entity.DatasetVersionEntity
import es.saniexam.app.data.entity.OptionEntity
import es.saniexam.app.data.entity.QuestionEntity
import es.saniexam.app.data.entity.SubjectPackEntity
import es.saniexam.app.data.entity.TopicEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * DAO stubs that throw on every method call. The use-case test subclass
 * overrides every method it exercises, so these stubs exist only to
 * satisfy the constructor and are never invoked at runtime.
 */
internal object UnusedSubjectPackDao : SubjectPackDao {
    override suspend fun insert(pack: SubjectPackEntity) = error("UnusedSubjectPackDao.insert")
    override suspend fun deleteById(packId: String) = error("UnusedSubjectPackDao.deleteById")
    override fun observeAll(): Flow<List<SubjectPackEntity>> = MutableStateFlow(emptyList())
    override suspend fun get(packId: String, packVersion: Int): SubjectPackEntity? = error("UnusedSubjectPackDao.get")
    override fun observeByCategory(category: String): Flow<List<SubjectPackEntity>> = MutableStateFlow(emptyList())
    override suspend fun countByCategory(category: String): Int = error("UnusedSubjectPackDao.countByCategory")
}

internal object UnusedTopicDao : TopicDao {
    override suspend fun insertAll(topics: List<TopicEntity>) = error("UnusedTopicDao.insertAll")
    override fun observeAll(packId: String): Flow<List<TopicEntity>> = MutableStateFlow(emptyList())
    override suspend fun exists(topicId: String, packId: String): Boolean = error("UnusedTopicDao.exists")
}

internal object UnusedQuestionDao : QuestionDao {
    override suspend fun insertAll(questions: List<QuestionEntity>) = error("UnusedQuestionDao.insertAll")
    override fun observeAll(packId: String): Flow<List<QuestionEntity>> = MutableStateFlow(emptyList())
    override suspend fun get(id: String): QuestionEntity? = error("UnusedQuestionDao.get")
    override suspend fun count(packId: String): Int = error("UnusedQuestionDao.count")
    override fun observeAllByCategory(category: String): Flow<List<QuestionEntity>> = MutableStateFlow(emptyList())
    override suspend fun countByCategory(category: String): Int = error("UnusedQuestionDao.countByCategory")
}

internal object UnusedOptionDao : OptionDao {
    override suspend fun insertAll(options: List<OptionEntity>) = error("UnusedOptionDao.insertAll")
    override suspend fun forQuestion(questionId: String): List<OptionEntity> = error("UnusedOptionDao.forQuestion")
}

internal object UnusedDatasetVersionDao : DatasetVersionDao {
    override suspend fun insert(row: DatasetVersionEntity) = error("UnusedDatasetVersionDao.insert")
    override fun observeAll(): Flow<List<DatasetVersionEntity>> = MutableStateFlow(emptyList())
    override suspend fun isApplied(packId: String, packVersion: Int): Boolean = error("UnusedDatasetVersionDao.isApplied")
}
