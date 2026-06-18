package es.saniexam.app.domain.repository

import es.saniexam.app.domain.model.UserSettings

/**
 * Singleton preferences. PR5 is the first writer (Review session
 * resume on `commit` + on `reveal`); PR4 only reads for the backup
 * codec and Settings UI.
 *
 * The PR4 implementation in `data/repository/UserSettingsRepositoryStub`
 * returns [UserSettings.Default]; the contract surface is here so PR5
 * can swap in a Room-backed implementation without touching Backup or
 * Settings.
 */
interface UserSettingsRepository {
    suspend fun get(): UserSettings
    suspend fun update(settings: UserSettings)
}
