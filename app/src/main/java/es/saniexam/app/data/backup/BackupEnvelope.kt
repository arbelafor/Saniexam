package es.saniexam.app.data.backup

import kotlinx.serialization.Serializable

/**
 * On-disk shape of a SaniExam backup. Schema version 1. The codec
 * (`BackupCodec`) is the only writer/reader; the DTOs are `internal`
 * so the domain layer never sees serialization types.
 *
 * Layout:
 * ```
 * {
 *   "schemaVersion": 1,
 *   "exportedAt": "<ISO-8601 instant>",
 *   "appVersion": "0.1.0",
 *   "cardStates": [ ... ],
 *   "reviewLogs": [],           // empty until PR5 lands ReviewLog
 *   "userSettings": { ... }     // default until PR5 lands UserSettings
 *   "checksum": "<sha256 hex of the payload WITHOUT this field>"
 * }
 * ```
 *
 * The checksum is computed over the JSON serialization of every other
 * field in a stable order; see [BackupCodec] for the exact algorithm.
 */
@Serializable
internal data class BackupDocument(
    val schemaVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val cardStates: List<CardStateDto> = emptyList(),
    val reviewLogs: List<ReviewLogDto> = emptyList(),
    val userSettings: UserSettingsDto = UserSettingsDto(),
    val checksum: String,
)

@Serializable
internal data class CardStateDto(
    val questionId: String,
    val packId: String,
    val packVersion: Int,
    val stability: Double,
    val difficulty: Double,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val reps: Int,
    val lapses: Int,
    val phase: String,
    val scheduledDays: Int,
    val elapsedDays: Int,
    val learningSteps: Int,
    val schedulerVersion: Int,
)

@Serializable
internal data class ReviewLogDto(
    val questionId: String,
    val reviewedAt: Long,
    val rating: String,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val previousIntervalDays: Int,
    val newIntervalDays: Int,
)

@Serializable
internal data class UserSettingsDto(
    val lastRevealedCardId: String? = null,
    val lastSessionQueuePosition: Int = 0,
    val lastSessionAt: Long? = null,
)
