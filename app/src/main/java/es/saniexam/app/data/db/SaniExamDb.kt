package es.saniexam.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import es.saniexam.app.data.dao.CardStateDao
import es.saniexam.app.data.dao.DatasetVersionDao
import es.saniexam.app.data.dao.OptionDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.dao.ReviewLogDao
import es.saniexam.app.data.dao.SubjectPackDao
import es.saniexam.app.data.dao.TopicDao
import es.saniexam.app.data.dao.UserSettingsDao
import es.saniexam.app.data.entity.CardStateEntity
import es.saniexam.app.data.entity.DatasetVersionEntity
import es.saniexam.app.data.entity.OptionEntity
import es.saniexam.app.data.entity.QuestionEntity
import es.saniexam.app.data.entity.ReviewLogEntity
import es.saniexam.app.data.entity.SubjectPackEntity
import es.saniexam.app.data.entity.TopicEntity
import es.saniexam.app.data.entity.UserSettingsEntity
import es.saniexam.app.scheduler.CardPhase
import java.time.Instant

/**
 * SSOT for offline data.
 *  - v2 replaces the v1 bootstrap `schema_marker` table with the real
 *    domain tables (`MIGRATION_1_2`).
 *  - v3 adds the `review_log` + `user_settings` tables that the Review
 *    use cases write into (`MIGRATION_2_3`).
 */
@Database(
    entities = [
        SubjectPackEntity::class, TopicEntity::class, QuestionEntity::class,
        OptionEntity::class, DatasetVersionEntity::class, CardStateEntity::class,
        ReviewLogEntity::class, UserSettingsEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(SaniExamDbConverters::class)
abstract class SaniExamDb : RoomDatabase() {
    abstract fun subjectPackDao(): SubjectPackDao
    abstract fun topicDao(): TopicDao
    abstract fun questionDao(): QuestionDao
    abstract fun optionDao(): OptionDao
    abstract fun datasetVersionDao(): DatasetVersionDao
    abstract fun cardStateDao(): CardStateDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        const val NAME = "saniexam.db"

        /** v1 -> v2: drop the bootstrap `schema_marker` table. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `schema_marker`")
            }
        }

        /**
         * v2 -> v3: add the `review_log` + `user_settings` tables that
         * `CommitRatingUseCase` writes into. `user_settings` is seeded
         * with the singleton `Default` row so the first
         * `userSettingsRepository.get()` call does not have to deal
         * with an empty table.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `review_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`question_id` TEXT NOT NULL, " +
                        "`reviewed_at` INTEGER NOT NULL, " +
                        "`rating` TEXT NOT NULL, " +
                        "`elapsed_days` INTEGER NOT NULL, " +
                        "`scheduled_days` INTEGER NOT NULL, " +
                        "`previous_interval_days` INTEGER NOT NULL, " +
                        "`new_interval_days` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_review_log_question_id` " +
                        "ON `review_log` (`question_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_review_log_reviewed_at` " +
                        "ON `review_log` (`reviewed_at`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_settings` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`last_revealed_card_id` TEXT, " +
                        "`last_session_queue_position` INTEGER NOT NULL, " +
                        "`last_session_at` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                // Seed the singleton with UserSettings.Default so the first
                // get() after the migration returns concrete values.
                db.execSQL(
                    "INSERT OR REPLACE INTO `user_settings` " +
                        "(`id`, `last_revealed_card_id`, `last_session_queue_position`, `last_session_at`) " +
                        "VALUES (${UserSettingsEntity.SINGLETON_ID}, NULL, 0, NULL)",
                )
            }
        }
    }
}

/** Type converters. Kept in a separate file to keep `@Database` compact. */
class SaniExamDbConverters {
    @TypeConverter fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun toEpochMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun fromPhase(phase: CardPhase?): String? = phase?.name?.lowercase()
    @TypeConverter fun toPhase(raw: String?): CardPhase? = when (raw?.lowercase()) {
        "new" -> CardPhase.New
        "learning" -> CardPhase.Learning
        "review" -> CardPhase.Review
        "relearning" -> CardPhase.Relearning
        else -> null
    }
}
