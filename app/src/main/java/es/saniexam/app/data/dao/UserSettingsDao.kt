package es.saniexam.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.saniexam.app.data.entity.UserSettingsEntity

/**
 * Singleton DAO for `user_settings`. The table holds exactly one row
 * (`id = 1`); the DAO treats the absence of that row as
 * [UserSettingsEntity] defaults so callers never have to seed the
 * database.
 */
@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = ${UserSettingsEntity.SINGLETON_ID} LIMIT 1")
    suspend fun get(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: UserSettingsEntity)
}
