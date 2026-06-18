package es.saniexam.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import es.saniexam.app.data.backup.BackupRepositoryImpl
import es.saniexam.app.data.ingest.AndroidPackAssetSource
import es.saniexam.app.data.ingest.PackAssetSource
import es.saniexam.app.data.repository.ReviewLogRepositoryImpl
import es.saniexam.app.data.repository.UserSettingsRepositoryImpl
import es.saniexam.app.domain.repository.BackupRepository
import es.saniexam.app.domain.repository.ReviewLogRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
import javax.inject.Singleton

/**
 * PR5 bindings:
 *  - `BackupRepository` → the codec + Room transaction impl.
 *  - `ReviewLogRepository` → Room-backed impl reading the `review_log`
 *    table written by `CommitRatingUseCase`.
 *  - `UserSettingsRepository` → Room-backed singleton on the
 *    `user_settings` table; the writer is the Review session
 *    resume path.
 *  - `PackAssetSource` → the production `AndroidPackAssetSource`.
 *
 * The PR4 stubs are kept in the source tree for reviewability but are
 * no longer injected; the Room-backed impls own the binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds @Singleton
    abstract fun bindReviewLogRepository(impl: ReviewLogRepositoryImpl): ReviewLogRepository

    @Binds @Singleton
    abstract fun bindUserSettingsRepository(impl: UserSettingsRepositoryImpl): UserSettingsRepository

    @Binds @Singleton
    abstract fun bindPackAssetSource(impl: AndroidPackAssetSource): PackAssetSource
}
