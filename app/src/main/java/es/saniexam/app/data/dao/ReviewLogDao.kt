package es.saniexam.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.saniexam.app.data.entity.ReviewLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Append-only DAO for `review_log`. The spec mandates that no existing
 * row is ever mutated or deleted; the DAO therefore exposes
 * [insert] (PK auto-increment, `OnConflict.IGNORE`) and **never** an
 * update or delete method. Backup import is the one exception (a
 * destructive single-table truncate inside the backup transaction);
 * that path is exercised by the codec test and lives on the DAO as
 * a package-private helper.
 */
@Dao
interface ReviewLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: ReviewLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<ReviewLogEntity>)

    @Query("SELECT * FROM review_log ORDER BY reviewed_at ASC, id ASC")
    fun observeAll(): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_log ORDER BY reviewed_at ASC, id ASC")
    suspend fun getAll(): List<ReviewLogEntity>

    @Query("SELECT COUNT(*) FROM review_log")
    suspend fun count(): Int

    /** Backing-store wipe. Used by the backup import transaction; not a
     *  spec-supported mutator of the log. */
    @Query("DELETE FROM review_log")
    suspend fun deleteAll()
}
