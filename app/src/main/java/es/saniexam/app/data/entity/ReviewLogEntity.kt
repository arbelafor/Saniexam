package es.saniexam.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.scheduler.Rating
import java.time.Instant

/**
 * Append-only log of every committed rating. Powers the `progress-stats`
 * spec ("Read-Only Derivation From ReviewLog") and the `review-session`
 * "Persisted Rating and Append-Only ReviewLog" requirement.
 *
 * The table has no `updated_at` / `deleted_at` columns because rows are
 * never mutated or deleted once written. The primary key is the auto-
 * generated `id` (Long) so the table is purely additive; all spec
 * scenarios that touch an existing row are forbidden by design.
 *
 * `reviewed_at` carries the same `Instant` the engine stamped into
 * `CardState.lastReviewedAt` on the same commit, so stats and
 * `UserSettings.lastSessionAt` are always consistent.
 */
@Entity(
    tableName = "review_log",
    indices = [
        Index(value = ["question_id"]),
        Index(value = ["reviewed_at"]),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    @ColumnInfo(name = "rating") val rating: String,
    @ColumnInfo(name = "elapsed_days") val elapsedDays: Int,
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Int,
    @ColumnInfo(name = "previous_interval_days") val previousIntervalDays: Int,
    @ColumnInfo(name = "new_interval_days") val newIntervalDays: Int,
)

private fun ratingFromString(raw: String): Rating = when (raw.uppercase()) {
    "AGAIN" -> Rating.Again
    "HARD" -> Rating.Hard
    "GOOD" -> Rating.Good
    "EASY" -> Rating.Easy
    else -> Rating.Good
}

internal fun ReviewLogEntity.toDomain() = ReviewLog(
    questionId = questionId,
    reviewedAt = Instant.ofEpochMilli(reviewedAt),
    rating = ratingFromString(rating),
    elapsedDays = elapsedDays,
    scheduledDays = scheduledDays,
    previousIntervalDays = previousIntervalDays,
    newIntervalDays = newIntervalDays,
)

internal fun ReviewLog.toEntity() = ReviewLogEntity(
    questionId = questionId,
    reviewedAt = reviewedAt.toEpochMilli(),
    rating = rating.name,
    elapsedDays = elapsedDays,
    scheduledDays = scheduledDays,
    previousIntervalDays = previousIntervalDays,
    newIntervalDays = newIntervalDays,
)
