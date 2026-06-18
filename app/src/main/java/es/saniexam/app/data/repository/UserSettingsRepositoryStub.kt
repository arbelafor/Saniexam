package es.saniexam.app.data.repository

import es.saniexam.app.domain.model.UserSettings
import es.saniexam.app.domain.repository.UserSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **PR4 stub.** PR5 swaps this for a Room-backed singleton on the
 * `user_settings` table; the writer is the Review session resume path.
 *
 * Returns [UserSettings.Default] so the backup codec's optional
 * `userSettings` field always round-trips with a concrete value. The
 * contract shape is final; only the source changes in PR5.
 */
@Singleton
class UserSettingsRepositoryStub @Inject constructor() : UserSettingsRepository {
    override suspend fun get(): UserSettings = UserSettings.Default
    override suspend fun update(settings: UserSettings) = Unit
}
