package es.saniexam.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import es.saniexam.app.data.dao.CardStateDao
import es.saniexam.app.data.dao.DatasetVersionDao
import es.saniexam.app.data.dao.OptionDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.dao.ReviewLogDao
import es.saniexam.app.data.dao.SubjectPackDao
import es.saniexam.app.data.dao.TopicDao
import es.saniexam.app.data.dao.UserSettingsDao
import es.saniexam.app.data.db.SaniExamDb
import javax.inject.Singleton

/**
 * Database wiring.
 *  - PR3 installs the v1 -> v2 migration that drops the bootstrap
 *    `schema_marker` table.
 *  - PR4 adds explicit DAO providers so the `HomeViewModel` and the
 *    `BackupRepositoryImpl` can inject the DAOs directly.
 *  - PR5 adds the v2 -> v3 migration (review_log + user_settings
 *    tables) and the corresponding DAO providers.
 *  - PR-A (`licensed-question-pack`) adds the v3 -> v4 migration
 *    (category + active_category columns, spec
 *    `professional-categories`) so the data model is
 *    multi-category-aware from v1.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): SaniExamDb = Room.databaseBuilder(
        context,
        SaniExamDb::class.java,
        SaniExamDb.NAME,
    )
        .addMigrations(
            SaniExamDb.MIGRATION_1_2,
            SaniExamDb.MIGRATION_2_3,
            SaniExamDb.MIGRATION_3_4,
        )
        .build()

    @Provides
    fun provideSubjectPackDao(db: SaniExamDb): SubjectPackDao = db.subjectPackDao()

    @Provides
    fun provideTopicDao(db: SaniExamDb): TopicDao = db.topicDao()

    @Provides
    fun provideQuestionDao(db: SaniExamDb): QuestionDao = db.questionDao()

    @Provides
    fun provideOptionDao(db: SaniExamDb): OptionDao = db.optionDao()

    @Provides
    fun provideDatasetVersionDao(db: SaniExamDb): DatasetVersionDao = db.datasetVersionDao()

    @Provides
    fun provideCardStateDao(db: SaniExamDb): CardStateDao = db.cardStateDao()

    @Provides
    fun provideReviewLogDao(db: SaniExamDb): ReviewLogDao = db.reviewLogDao()

    @Provides
    fun provideUserSettingsDao(db: SaniExamDb): UserSettingsDao = db.userSettingsDao()
}
