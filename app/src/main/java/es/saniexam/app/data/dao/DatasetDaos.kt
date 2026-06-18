package es.saniexam.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.saniexam.app.data.entity.DatasetVersionEntity
import es.saniexam.app.data.entity.SubjectPackEntity
import es.saniexam.app.data.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAOs for the dataset metadata tables. PR3 does not yet expose the
 * derived `QuestionWithOptions` projection — the Review UI (PR5) adds
 * that when the read path is wired.
 */
@Dao
interface SubjectPackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pack: SubjectPackEntity)

    @Query("SELECT * FROM subject_pack")
    fun observeAll(): Flow<List<SubjectPackEntity>>

    @Query("SELECT * FROM subject_pack WHERE id = :packId AND version = :packVersion LIMIT 1")
    suspend fun get(packId: String, packVersion: Int): SubjectPackEntity?
}

@Dao
interface TopicDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(topics: List<TopicEntity>)

    @Query("SELECT * FROM topic WHERE pack_id = :packId")
    fun observeAll(packId: String): Flow<List<TopicEntity>>

    @Query("SELECT 1 FROM topic WHERE id = :topicId AND pack_id = :packId LIMIT 1")
    suspend fun exists(topicId: String, packId: String): Boolean
}

@Dao
interface DatasetVersionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: DatasetVersionEntity)

    @Query("SELECT * FROM dataset_version ORDER BY imported_at DESC")
    fun observeAll(): Flow<List<DatasetVersionEntity>>

    @Query(
        "SELECT 1 FROM dataset_version WHERE pack_id = :packId " +
            "AND pack_version = :packVersion AND status = 'applied' LIMIT 1"
    )
    suspend fun isApplied(packId: String, packVersion: Int): Boolean
}
