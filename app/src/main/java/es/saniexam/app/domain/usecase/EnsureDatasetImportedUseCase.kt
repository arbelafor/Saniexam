package es.saniexam.app.domain.usecase

import es.saniexam.app.data.ingest.DatasetImporter
import es.saniexam.app.data.ingest.DatasetImportException
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.repository.DatasetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Idempotent: on a cold DB it imports the bundled pack; on a warm DB with
 * the same `(packId, packVersion)` already applied it is a no-op. Re-import
 * of a *new* version is out of scope for PR3 and lands with the slice-3
 * update flow.
 *
 * Returns the number of questions imported on a cold run, or 0 on a
 * no-op. Throws [DatasetImportException] on validation failure; the
 * importer runs everything inside a Room transaction so the DB is
 * unchanged on failure.
 */
class EnsureDatasetImportedUseCase @Inject constructor(
    private val datasetRepository: DatasetRepository,
    private val importer: DatasetImporter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(): Int = withContext(io) {
        if (datasetRepository.isApplied(PACK_ID, PACK_VERSION)) return@withContext 0
        importer.importBundled(packId = PACK_ID, packVersion = PACK_VERSION)
    }

    companion object {
        // Must match `pack-manifest.json` shipped under `assets/`. The dev
        // placeholder pack is what makes the importer non-trivial in PR3.
        const val PACK_ID = "sanidad-dev-placeholder"
        const val PACK_VERSION = 1
    }
}
