package es.saniexam.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.saniexam.app.data.entity.CardStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface CardStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CardStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<CardStateEntity>)

    @Query("SELECT * FROM card_state WHERE question_id = :questionId LIMIT 1")
    suspend fun get(questionId: String): CardStateEntity?

    @Query("SELECT * FROM card_state WHERE due_at <= :nowMs ORDER BY due_at ASC LIMIT :limit")
    fun observeDue(nowMs: Long, limit: Int): Flow<List<CardStateEntity>>

    @Query("SELECT COUNT(*) FROM card_state")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM card_state WHERE due_at <= :nowMs")
    suspend fun countDue(nowMs: Long): Int

    @Query("SELECT * FROM card_state")
    fun observeAll(): Flow<List<CardStateEntity>>

    @Query("SELECT * FROM card_state")
    suspend fun getAll(): List<CardStateEntity>

    @Query("DELETE FROM card_state")
    suspend fun deleteAll()
}

/** Snapshot the full `card_state` table into a list. Used by backup export + import. */
suspend fun CardStateDao.observeAllOnce(): List<CardStateEntity> = observeAll().first()
