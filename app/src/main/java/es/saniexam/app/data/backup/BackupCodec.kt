package es.saniexam.app.data.backup

import es.saniexam.app.domain.repository.BackupException
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.model.UserSettings
import es.saniexam.app.scheduler.CardPhase
import es.saniexam.app.scheduler.Rating
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-Kotlin encode / decode of the [BackupDocument] format. Has no
 * Android dependencies so it is unit-testable on the plain JVM.
 *
 * The codec is the single source of truth for the file format. The
 * repository (`BackupRepositoryImpl`) only adds I/O (Room transaction,
 * SAF URI) on top of it.
 *
 * **Checksum.** The `checksum` field is the lowercase hex SHA-256 of
 * the JSON serialization of every other field, in declaration order,
 * with the `checksum` field set to the empty string during the hash
 * computation. This keeps the algorithm trivially auditable.
 */
@Singleton
class BackupCodec @Inject constructor(
    private val json: Json,
) {

    /**
     * Encode the given user state to a self-describing [BackupDocument]
     * serialized as UTF-8 JSON. The bytes include the final `checksum`.
     */
    fun encode(
        cardStates: List<CardState>,
        reviewLogs: List<ReviewLog>,
        userSettings: UserSettings,
        exportedAt: Instant,
        appVersion: String,
    ): ByteArray {
        val withoutChecksum = BackupDocument(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAt = exportedAt.toString(),
            appVersion = appVersion,
            cardStates = cardStates.map(::toDto),
            reviewLogs = reviewLogs.map(::toDto),
            userSettings = toDto(userSettings),
            checksum = "",
        )
        val checksum = sha256Hex(json.encodeToString(BackupDocument.serializer(), withoutChecksum).toByteArray(Charsets.UTF_8))
        val withChecksum = withoutChecksum.copy(checksum = checksum)
        return json.encodeToString(BackupDocument.serializer(), withChecksum).toByteArray(Charsets.UTF_8)
    }

    /**
     * Decode the file. Throws [BackupException] on every supported
     * failure (checksum mismatch, unsupported schemaVersion, malformed
     * payload). On success returns a typed [DecodedBackup] so the
     * repository can apply it inside a transaction.
     */
    fun decode(bytes: ByteArray): DecodedBackup {
        val text = try {
            bytes.toString(Charsets.UTF_8)
        } catch (t: Throwable) {
            throw BackupException(BackupException.Reason.MalformedPayload, t)
        }
        val doc = try {
            json.decodeFromString(BackupDocument.serializer(), text)
        } catch (t: Throwable) {
            throw BackupException(BackupException.Reason.MalformedPayload, t)
        }
        if (doc.schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw BackupException(BackupException.Reason.UnsupportedSchemaVersion)
        }
        val withoutChecksum = doc.copy(checksum = "")
        val expected = sha256Hex(json.encodeToString(BackupDocument.serializer(), withoutChecksum).toByteArray(Charsets.UTF_8))
        if (!expected.equals(doc.checksum, ignoreCase = true)) {
            throw BackupException(BackupException.Reason.ChecksumMismatch)
        }
        return DecodedBackup(
            cardStates = doc.cardStates.map(::fromDto),
            reviewLogs = doc.reviewLogs.map(::fromDto),
            userSettings = fromDto(doc.userSettings),
        )
    }

    companion object {
        /** Bump on every intentional format change. The repository refuses newer files. */
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

data class DecodedBackup(
    val cardStates: List<CardState>,
    val reviewLogs: List<ReviewLog>,
    val userSettings: UserSettings,
)

// --- DTO <-> domain mappers --------------------------------------------------

private fun toDto(c: CardState): CardStateDto = CardStateDto(
    questionId = c.questionId, packId = c.packId, packVersion = c.packVersion,
    stability = c.stability, difficulty = c.difficulty,
    dueAt = c.dueAt.toEpochMilli(),
    lastReviewedAt = c.lastReviewedAt?.toEpochMilli(),
    reps = c.reps, lapses = c.lapses,
    phase = c.phase.name.lowercase(),
    scheduledDays = c.scheduledDays, elapsedDays = c.elapsedDays,
    learningSteps = c.learningSteps, schedulerVersion = c.schedulerVersion,
)

private fun fromDto(d: CardStateDto): CardState = CardState(
    questionId = d.questionId, packId = d.packId, packVersion = d.packVersion,
    stability = d.stability, difficulty = d.difficulty,
    dueAt = Instant.ofEpochMilli(d.dueAt),
    lastReviewedAt = d.lastReviewedAt?.let(Instant::ofEpochMilli),
    reps = d.reps, lapses = d.lapses,
    phase = when (d.phase.lowercase()) {
        "new" -> CardPhase.New
        "learning" -> CardPhase.Learning
        "review" -> CardPhase.Review
        "relearning" -> CardPhase.Relearning
        else -> CardPhase.New
    },
    scheduledDays = d.scheduledDays, elapsedDays = d.elapsedDays,
    learningSteps = d.learningSteps, schedulerVersion = d.schedulerVersion,
)

private fun toDto(r: ReviewLog): ReviewLogDto = ReviewLogDto(
    questionId = r.questionId,
    reviewedAt = r.reviewedAt.toEpochMilli(),
    rating = r.rating.name,
    elapsedDays = r.elapsedDays, scheduledDays = r.scheduledDays,
    previousIntervalDays = r.previousIntervalDays, newIntervalDays = r.newIntervalDays,
)

private fun fromDto(d: ReviewLogDto): ReviewLog = ReviewLog(
    questionId = d.questionId,
    reviewedAt = Instant.ofEpochMilli(d.reviewedAt),
    rating = when (d.rating.uppercase()) {
        "AGAIN" -> Rating.Again
        "HARD" -> Rating.Hard
        "GOOD" -> Rating.Good
        "EASY" -> Rating.Easy
        else -> Rating.Good
    },
    elapsedDays = d.elapsedDays, scheduledDays = d.scheduledDays,
    previousIntervalDays = d.previousIntervalDays, newIntervalDays = d.newIntervalDays,
)

private fun toDto(u: UserSettings): UserSettingsDto = UserSettingsDto(
    lastRevealedCardId = u.lastRevealedCardId,
    lastSessionQueuePosition = u.lastSessionQueuePosition,
    lastSessionAt = u.lastSessionAt?.toEpochMilli(),
)

private fun fromDto(d: UserSettingsDto): UserSettings = UserSettings(
    lastRevealedCardId = d.lastRevealedCardId,
    lastSessionQueuePosition = d.lastSessionQueuePosition,
    lastSessionAt = d.lastSessionAt?.let(Instant::ofEpochMilli),
)

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = StringBuilder(digest.size * 2)
    digest.forEach { b -> hex.append(String.format("%02x", b)) }
    return hex.toString()
}
