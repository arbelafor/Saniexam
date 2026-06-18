package es.saniexam.app.domain.model

import java.time.Instant

/**
 * One row per pack import attempt. Powers the "first-launch only" guard
 * and the Settings "Última actualización" badge.
 */
data class DatasetVersion(
    val packId: String,
    val packVersion: Int,
    val status: DatasetStatus,
    val bytes: Long,
    val checksumSha256: String,
    val importedAt: Instant,
)

enum class DatasetStatus { Applied, Rejected, Unknown }
