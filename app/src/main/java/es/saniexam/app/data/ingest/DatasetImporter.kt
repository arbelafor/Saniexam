package es.saniexam.app.data.ingest

import androidx.room.withTransaction
import es.saniexam.app.data.dao.DatasetVersionDao
import es.saniexam.app.data.dao.OptionDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.dao.SubjectPackDao
import es.saniexam.app.data.dao.TopicDao
import es.saniexam.app.data.db.SaniExamDb
import es.saniexam.app.data.entity.DatasetVersionEntity
import es.saniexam.app.data.entity.toEntity
import es.saniexam.app.domain.model.SubjectPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

class DatasetImportException(
    val reason: Reason,
    val questionId: String? = null,
    cause: Throwable? = null,
) : Exception("DatasetImportException(reason=$reason${questionId?.let { " questionId=$it" } ?: ""})", cause) {
    enum class Reason {
        AssetMissing, AssetUnreadable, ManifestMissing, ManifestMismatch,
        PackIdMismatch, VersionMismatch, ChecksumMismatch, MissingAttribution,
        QuestionMissingFields, ZeroOrMultipleCorrectOptions,
        OrphanTopicReference, DuplicateQuestionId,
    }
}

@Serializable private data class PackManifest(
    val id: String, val version: Int,
    val sourceAttribution: String, val publishedAt: String,
    val license: String, val licenseNotes: String,
    val sha256: String, val packFile: String,
)

@Serializable private data class PackFile(
    val packId: String, val packVersion: Int,
    val topics: List<PackTopic> = emptyList(),
    val questions: List<PackQuestion>,
)

@Serializable private data class PackTopic(val id: String, val name: String)

@Serializable private data class PackQuestion(
    val id: String, val topicId: String, val prompt: String,
    val explanation: String? = null,
    val officialYear: Int? = null,
    val officialSourceRef: String? = null,
    val options: List<PackOption>,
)

@Serializable private data class PackOption(
    val id: String, val ordinal: Int, val text: String, val isCorrect: Boolean,
)

/**
 * Reads the bundled manifest + pack, validates the schema and FKs, then
 * writes everything inside a Room transaction. On any failure the entire
 * transaction is rolled back. Idempotency is the caller's concern
 * (`EnsureDatasetImportedUseCase` checks `dataset_version` first).
 */
@Singleton
open class DatasetImporter @Inject constructor(
    private val assetSource: PackAssetSource,
    private val json: Json,
    private val packDao: SubjectPackDao,
    private val topicDao: TopicDao,
    private val questionDao: QuestionDao,
    private val optionDao: OptionDao,
    private val versionDao: DatasetVersionDao,
    private val db: SaniExamDb?,
) {
    open suspend fun importBundled(packId: String, packVersion: Int): Int = withContext(Dispatchers.IO) {
        val manifest = readManifest(MANIFEST_PATH)
        if (manifest.id != packId) throw DatasetImportException(DatasetImportException.Reason.PackIdMismatch)
        if (manifest.version != packVersion) throw DatasetImportException(DatasetImportException.Reason.VersionMismatch)

        val packBytes = assetSource.read(manifest.packFile)
        val expected = manifest.sha256.lowercase()
        val actual = sha256Hex(packBytes)
        if (expected != actual) throw DatasetImportException(DatasetImportException.Reason.ChecksumMismatch)

        val packFile = json.decodeFromString(PackFile.serializer(), packBytes.toString(Charsets.UTF_8))
        if (packFile.packId != manifest.id || packFile.packVersion != manifest.version) {
            throw DatasetImportException(DatasetImportException.Reason.ManifestMismatch)
        }
        validateAttribution(manifest)

        val pack = SubjectPack(
            id = manifest.id, version = manifest.version,
            sourceAttribution = manifest.sourceAttribution,
            publishedAt = manifest.publishedAt,
            license = manifest.license, licenseNotes = manifest.licenseNotes,
        )
        val validated = PackValidator.validate(
            pack = pack,
            topics = packFile.topics.map { PackValidator.PackTopicView(it.id, it.name) },
            questions = packFile.questions.map { q ->
                PackValidator.PackQuestionView(
                    id = q.id, topicId = q.topicId, prompt = q.prompt,
                    explanation = q.explanation,
                    officialYear = q.officialYear,
                    officialSourceRef = q.officialSourceRef,
                    options = q.options.map { o ->
                        PackValidator.PackOptionView(
                            id = o.id, ordinal = o.ordinal,
                            text = o.text, isCorrect = o.isCorrect,
                        )
                    },
                )
            },
        )

        (db ?: error("DatasetImporter requires a non-null SaniExamDb in production")).withTransaction {
            packDao.insert(pack.toEntity())
            topicDao.insertAll(validated.topicEntities)
            questionDao.insertAll(validated.questionEntities)
            optionDao.insertAll(validated.optionEntities)
            versionDao.insert(
                DatasetVersionEntity(
                    packId = pack.id, packVersion = pack.version,
                    status = "applied",
                    bytes = packBytes.size.toLong(),
                    checksumSha256 = actual,
                    importedAt = Instant.now().toEpochMilli(),
                    errorMessage = null,
                ),
            )
        }
        validated.questionEntities.size
    }

    private fun readManifest(path: String): PackManifest = try {
        json.decodeFromString(PackManifest.serializer(), assetSource.read(path).toString(Charsets.UTF_8))
    } catch (_: java.io.FileNotFoundException) {
        throw DatasetImportException(DatasetImportException.Reason.ManifestMissing)
    } catch (t: Throwable) {
        throw DatasetImportException(DatasetImportException.Reason.AssetUnreadable, cause = t)
    }

    private fun validateAttribution(manifest: PackManifest) {
        if (manifest.sourceAttribution.isBlank() ||
            manifest.publishedAt.isBlank() ||
            manifest.license.isBlank() ||
            manifest.licenseNotes.isBlank()
        ) {
            throw DatasetImportException(DatasetImportException.Reason.MissingAttribution)
        }
    }

    companion object {
        const val MANIFEST_PATH = "pack-manifest.json"
    }
}

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = StringBuilder(digest.size * 2)
    digest.forEach { b -> hex.append(String.format("%02x", b)) }
    return hex.toString()
}
