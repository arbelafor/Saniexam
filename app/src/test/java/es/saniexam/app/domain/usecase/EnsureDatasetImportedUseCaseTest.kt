package es.saniexam.app.domain.usecase

import es.saniexam.app.data.ingest.DatasetImportException
import es.saniexam.app.data.ingest.DatasetImporter
import es.saniexam.app.data.ingest.PackAssetSource
import es.saniexam.app.domain.model.DatasetVersion
import es.saniexam.app.domain.model.UserSettings
import es.saniexam.app.domain.repository.DatasetRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
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
 * PR-A: the use case now resolves the active category through
 * [UserSettingsRepository]. The tests inject a fake
 * [UserSettingsRepository] that returns [UserSettings.Default]
 * (`activeCategory="TCAE"`). PR-B keeps the same default; future
 * categories (Enfermería, Medicina) are a value change.
 *
 * Uses hand-rolled fakes so the cold-vs-warm branch is explicit. The
 * importer is exercised on the plain JVM via `Dispatchers.Unconfined`.
 */
class EnsureDatasetImportedUseCaseTest {

    @Test
    fun `cold db invokes importer exactly once`() = runBlocking {
        val repo = FakeDatasetRepository(applied = false)
        val userSettingsRepo = FakeUserSettingsRepository(UserSettings.Default)
        val importer = RecordingImporter(returnValue = 42)
        val useCase = EnsureDatasetImportedUseCase(
            datasetRepository = repo,
            userSettingsRepository = userSettingsRepo,
            importer = importer,
            io = Dispatchers.Unconfined,
        )

        assertEquals(42, useCase())
        assertEquals(1, importer.invocations)
        // PR-A: the use case must call the importer with the
        // spec-mandated `sanidad-v1` pack id (and the bundled v1).
        assertEquals(1, importer.callIds.size)
        assertEquals(EnsureDatasetImportedUseCase.PACK_ID, importer.callIds.first().first)
        assertEquals(EnsureDatasetImportedUseCase.PACK_VERSION, importer.callIds.first().second)
        assertEquals(UserSettings.TCAE, importer.callIds.first().third)
    }

    @Test
    fun `warm db with same version is a no-op`() = runBlocking {
        val repo = FakeDatasetRepository(applied = true)
        val userSettingsRepo = FakeUserSettingsRepository(UserSettings.Default)
        val importer = RecordingImporter(returnValue = 99)
        val useCase = EnsureDatasetImportedUseCase(
            datasetRepository = repo,
            userSettingsRepository = userSettingsRepo,
            importer = importer,
            io = Dispatchers.Unconfined,
        )

        assertEquals(0, useCase())
        assertEquals(0, importer.invocations)
    }

    @Test
    fun `importer failure propagates as DatasetImportException`() {
        val repo = FakeDatasetRepository(applied = false)
        val userSettingsRepo = FakeUserSettingsRepository(UserSettings.Default)
        val importer = ThrowingImporter(
            DatasetImportException(DatasetImportException.Reason.ChecksumMismatch),
        )
        val useCase = EnsureDatasetImportedUseCase(
            datasetRepository = repo,
            userSettingsRepository = userSettingsRepo,
            importer = importer,
            io = Dispatchers.Unconfined,
        )

        val ex = assertThrows(DatasetImportException::class.java) {
            runBlocking { useCase() }
        }
        assertEquals(DatasetImportException.Reason.ChecksumMismatch, ex.reason)
    }

    @Test
    fun `cold db passes active category to importer so mismatch fails closed`() {
        val repo = FakeDatasetRepository(applied = false)
        val userSettingsRepo = FakeUserSettingsRepository(
            UserSettings.Default.copy(activeCategory = "MEDICINA"),
        )
        val importer = CategoryCheckingImporter(manifestCategory = UserSettings.TCAE)
        val useCase = EnsureDatasetImportedUseCase(
            datasetRepository = repo,
            userSettingsRepository = userSettingsRepo,
            importer = importer,
            io = Dispatchers.Unconfined,
        )

        val ex = assertThrows(DatasetImportException::class.java) {
            runBlocking { useCase() }
        }
        assertEquals(DatasetImportException.Reason.CategoryMismatch, ex.reason)
        assertEquals("MEDICINA", importer.expectedCategories.single())
    }

    // --- Fakes ---

    private class FakeDatasetRepository(private val applied: Boolean) : DatasetRepository {
        override fun observeActivePacks(): Flow<List<es.saniexam.app.domain.model.SubjectPack>> =
            MutableStateFlow(emptyList())
        override fun observeAppliedVersions(): Flow<List<DatasetVersion>> =
            MutableStateFlow(emptyList())
        override suspend fun isApplied(packId: String, packVersion: Int): Boolean = applied
        override suspend fun recordVersion(version: DatasetVersion) = Unit
        override fun observeActivePacksByCategory(category: String): Flow<List<es.saniexam.app.domain.model.SubjectPack>> =
            MutableStateFlow(emptyList())
        override suspend fun countActivePacksByCategory(category: String): Int = 0
    }

    private class FakeUserSettingsRepository(
        private val settings: UserSettings,
    ) : UserSettingsRepository {
        override suspend fun get(): UserSettings = settings
        override suspend fun update(settings: UserSettings) = Unit
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
        val callIds: MutableList<Triple<String, Int, String>> = mutableListOf()
        override suspend fun importBundled(packId: String, packVersion: Int, expectedCategory: String): Int {
            invocations += 1
            callIds += Triple(packId, packVersion, expectedCategory)
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
        override suspend fun importBundled(packId: String, packVersion: Int, expectedCategory: String): Int {
            throw error
        }
    }

    private class CategoryCheckingImporter(private val manifestCategory: String) : DatasetImporter(
        assetSource = InMemoryAssetSource(emptyMap()),
        json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        packDao = UnusedSubjectPackDao,
        topicDao = UnusedTopicDao,
        questionDao = UnusedQuestionDao,
        optionDao = UnusedOptionDao,
        versionDao = UnusedDatasetVersionDao,
        db = null,
    ) {
        val expectedCategories: MutableList<String> = mutableListOf()
        override suspend fun importBundled(packId: String, packVersion: Int, expectedCategory: String): Int {
            expectedCategories += expectedCategory
            if (manifestCategory != expectedCategory) {
                throw DatasetImportException(DatasetImportException.Reason.CategoryMismatch)
            }
            return 1
        }
    }
}

private class InMemoryAssetSource(private val files: Map<String, ByteArray>) : PackAssetSource {
    override fun read(path: String): ByteArray = files[path]
        ?: throw java.io.FileNotFoundException("no such asset: $path")
}
