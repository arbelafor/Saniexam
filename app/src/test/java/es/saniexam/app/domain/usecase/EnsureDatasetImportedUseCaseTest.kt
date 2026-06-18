package es.saniexam.app.domain.usecase

import es.saniexam.app.data.ingest.DatasetImportException
import es.saniexam.app.data.ingest.DatasetImporter
import es.saniexam.app.data.ingest.PackAssetSource
import es.saniexam.app.domain.model.DatasetVersion
import es.saniexam.app.domain.repository.DatasetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Verifies the idempotency contract of `EnsureDatasetImportedUseCase`:
 *  - on a cold DB (no `Applied` row) the importer is invoked;
 *  - on a warm DB with the same `(packId, packVersion)` applied the
 *    importer is NOT invoked and the use case returns 0;
 *  - [DatasetImportException] propagates so the caller (PR4 onboarding)
 *    can render a localized error.
 *
 * Uses hand-rolled fakes so the cold-vs-warm branch is explicit. The
 * importer is exercised on the plain JVM via `Dispatchers.Unconfined`.
 */
class EnsureDatasetImportedUseCaseTest {

    @Test
    fun `cold db invokes importer exactly once`() = runBlocking {
        val repo = FakeDatasetRepository(applied = false)
        val importer = RecordingImporter(returnValue = 42)
        val useCase = EnsureDatasetImportedUseCase(repo, importer, Dispatchers.Unconfined)

        assertEquals(42, useCase())
        assertEquals(1, importer.invocations)
    }

    @Test
    fun `warm db with same version is a no-op`() = runBlocking {
        val repo = FakeDatasetRepository(applied = true)
        val importer = RecordingImporter(returnValue = 99)
        val useCase = EnsureDatasetImportedUseCase(repo, importer, Dispatchers.Unconfined)

        assertEquals(0, useCase())
        assertEquals(0, importer.invocations)
    }

    @Test
    fun `importer failure propagates as DatasetImportException`() {
        val repo = FakeDatasetRepository(applied = false)
        val importer = ThrowingImporter(
            DatasetImportException(DatasetImportException.Reason.ChecksumMismatch),
        )
        val useCase = EnsureDatasetImportedUseCase(repo, importer, Dispatchers.Unconfined)

        val ex = assertThrows(DatasetImportException::class.java) {
            runBlocking { useCase() }
        }
        assertEquals(DatasetImportException.Reason.ChecksumMismatch, ex.reason)
    }

    // --- Fakes ---

    private class FakeDatasetRepository(private val applied: Boolean) : DatasetRepository {
        override fun observeActivePacks(): Flow<List<es.saniexam.app.domain.model.SubjectPack>> =
            MutableStateFlow(emptyList())
        override fun observeAppliedVersions(): Flow<List<DatasetVersion>> =
            MutableStateFlow(emptyList())
        override suspend fun isApplied(packId: String, packVersion: Int): Boolean = applied
        override suspend fun recordVersion(version: DatasetVersion) = Unit
    }

    private class RecordingImporter(private val returnValue: Int) : DatasetImporter(
        assetSource = InMemoryAssetSource(emptyMap()),
        json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        packDao = UnusedSubjectPackDao,
        topicDao = UnusedTopicDao,
        questionDao = UnusedQuestionDao,
        optionDao = UnusedOptionDao,
        versionDao = UnusedDatasetVersionDao,
        db = null,
    ) {
        var invocations: Int = 0
        override suspend fun importBundled(packId: String, packVersion: Int): Int {
            invocations += 1
            return returnValue
        }
    }

    private class ThrowingImporter(private val error: Throwable) : DatasetImporter(
        assetSource = InMemoryAssetSource(emptyMap()),
        json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        packDao = UnusedSubjectPackDao,
        topicDao = UnusedTopicDao,
        questionDao = UnusedQuestionDao,
        optionDao = UnusedOptionDao,
        versionDao = UnusedDatasetVersionDao,
        db = null,
    ) {
        override suspend fun importBundled(packId: String, packVersion: Int): Int {
            throw error
        }
    }
}

private class InMemoryAssetSource(private val files: Map<String, ByteArray>) : PackAssetSource {
    override fun read(path: String): ByteArray = files[path]
        ?: throw java.io.FileNotFoundException("no such asset: $path")
}
