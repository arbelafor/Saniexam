package es.saniexam.app.domain.repository

import es.saniexam.app.domain.model.Option
import es.saniexam.app.domain.model.Question
import es.saniexam.app.domain.model.Topic
import kotlinx.coroutines.flow.Flow

interface QuestionRepository {
    fun observeAll(packId: String): Flow<List<Question>>
    suspend fun get(id: String): Question?
    suspend fun count(packId: String): Int
}

interface OptionRepository {
    suspend fun forQuestion(questionId: String): List<Option>
}

interface TopicRepository {
    fun observeAll(packId: String): Flow<List<Topic>>
    suspend fun exists(topicId: String, packId: String): Boolean
}
