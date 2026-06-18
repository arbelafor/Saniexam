package es.saniexam.app.domain.model

import java.time.Instant

/**
 * Identity is the natural key (`id`) plus the source `packId` + `packVersion`
 * pair (questions are immutable within a version; see `dataset-import` spec).
 * [officialYear] and [officialSourceRef] are nullable; Settings shows them
 * only when present.
 */
data class Question(
    val id: String,
    val packId: String,
    val packVersion: Int,
    val topicId: String,
    val prompt: String,
    val explanation: String?,
    val officialYear: Int?,
    val officialSourceRef: String?,
)
