package es.saniexam.app.data.repository

import es.saniexam.app.data.dao.UserSettingsDao
import es.saniexam.app.data.entity.toDomain
import es.saniexam.app.data.entity.toEntity
import es.saniexam.app.domain.model.UserSettings
import es.saniexam.app.domain.repository.UserSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [UserSettingsRepository]. The table holds exactly one row
 * (`id = 1`); [get] returns the [UserSettings.Default] when the row is
 * absent so the first cold launch is a no-op for callers.
 */
@Singleton
class UserSettingsRepositoryImpl @Inject constructor(
    private val dao: UserSettingsDao,
) : UserSettingsRepository {
    override suspend fun get(): UserSettings =
        dao.get()?.toDomain() ?: UserSettings.Default

    override suspend fun update(settings: UserSettings) {
        dao.upsert(settings.toEntity())
    }
}
