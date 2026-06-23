package es.saniexam.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import es.saniexam.app.domain.model.UserSettings
import java.time.Instant

/**
 * Singleton user-preference row. The table is hard-coded to `id = 1` so
 * the DAO can call `getOrInit()` without race conditions and so
 * `MIGRATION_2_3` can install a single default row.
 *
 * Used by Review to resume the in-flight session (queue position,
 * reveal state) and by the backup codec to round-trip user state
 * across devices.
 *
 * PR-A: `active_category` is the new `professional-categories` column.
 * The default is `TCAE` (the only registered category in the MVP).
 * The Room v3 -> v4 migration also adds the column on existing v3
 * installs; see [es.saniexam.app.data.db.SaniExamDb.MIGRATION_3_4].
 */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "last_revealed_card_id") val lastRevealedCardId: String?,
    @ColumnInfo(name = "last_session_queue_position") val lastSessionQueuePosition: Int,
    @ColumnInfo(name = "last_session_at") val lastSessionAt: Long?,
    @ColumnInfo(name = "active_category", defaultValue = "TCAE") val activeCategory: String,
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}

internal fun UserSettingsEntity.toDomain() = UserSettings(
    lastRevealedCardId = lastRevealedCardId,
    lastSessionQueuePosition = lastSessionQueuePosition,
    lastSessionAt = lastSessionAt?.let(Instant::ofEpochMilli),
    activeCategory = activeCategory,
)

internal fun UserSettings.toEntity() = UserSettingsEntity(
    id = UserSettingsEntity.SINGLETON_ID,
    lastRevealedCardId = lastRevealedCardId,
    lastSessionQueuePosition = lastSessionQueuePosition,
    lastSessionAt = lastSessionAt?.toEpochMilli(),
    activeCategory = activeCategory,
)
