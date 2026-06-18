package es.saniexam.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.saniexam.app.data.entity.OptionEntity
import es.saniexam.app.data.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Query("SELECT * FROM question WHERE pack_id = :packId")
    fun observeAll(packId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM question WHERE id = :id LIMIT 1")
    suspend fun get(id: String): QuestionEntity?

    @Query("SELECT COUNT(*) FROM question WHERE pack_id = :packId")
    suspend fun count(packId: String): Int
}

@Dao
interface OptionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(options: List<OptionEntity>)

    @Query("SELECT * FROM `option` WHERE question_id = :questionId ORDER BY ordinal ASC")
    suspend fun forQuestion(questionId: String): List<OptionEntity>
}
