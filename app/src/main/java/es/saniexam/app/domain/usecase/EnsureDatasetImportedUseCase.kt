package es.saniexam.app.domain.usecase

import es.saniexam.app.data.ingest.DatasetImporter
import es.saniexam.app.data.ingest.DatasetImportException
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.model.UserSettings
import es.saniexam.app.domain.repository.DatasetRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Idempotent: on a cold DB it imports the bundled pack; on a warm DB with
 * the same `(packId, packVersion)` already applied it is a no-op. Re-import
 * of a *new* version is out of scope for PR3 and lands with the slice-3
 * update flow.
 *
 * PR-A: [PACK_ID] is now `sanidad-v1` (the spec-mandated id; the dev
 * placeholder `sanidad-dev-placeholder` was the PR3 stand-in). The
 * active category resolves through [UserSettings.activeCategory] so
 * future categories (Enfermería, Medicina) reuse the same use case
 * with no change to the call site.
 *
 * Returns the number of questions imported on a cold run, or 0 on a
 * no-op. Throws [DatasetImportException] on validation failure; the
 * importer runs everything inside a Room transaction so the DB is
 * unchanged on failure.
 */
class EnsureDatasetImportedUseCase @Inject constructor(
    private val datasetRepository: DatasetRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val importer: DatasetImporter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(): Int = withContext(io) {
        if (datasetRepository.isApplied(PACK_ID, PACK_VERSION)) return@withContext 0
        val activeCategory = userSettingsRepository.get().activeCategory
        importer.importBundled(
            packId = PACK_ID,
            packVersion = PACK_VERSION,
            expectedCategory = activeCategory,
        )
    }

    companion object {
        // PR-A: the bundled pack id is the spec-mandated `sanidad-v1`
        // (the dev placeholder `sanidad-dev-placeholder` is removed in
        // this slice). The release-pipeline Gradle gate and the two
        // CI scripts mirror the same id; PR-B will not change it.
        const val PACK_ID = "sanidad-v1"
        const val PACK_VERSION = 1

        /**
         * The MVP's only registered professional category. PR-B
         * keeps the same default; future categories (Enfermería,
         * Medicina) are a value change, not a structural change.
         */
        val DEFAULT_CATEGORY: String = UserSettings.TCAE
    }
}
