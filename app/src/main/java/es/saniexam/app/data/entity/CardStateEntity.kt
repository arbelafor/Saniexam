package es.saniexam.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.scheduler.CardPhase
import java.time.Instant

@Entity(
    tableName = "card_state",
    foreignKeys = [ForeignKey(
        entity = QuestionEntity::class,
        parentColumns = ["id"],
        childColumns = ["question_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["due_at"]), Index(value = ["question_id"], unique = true)],
)
data class CardStateEntity(
    @PrimaryKey @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "pack_version") val packVersion: Int,
    @ColumnInfo(name = "stability") val stability: Double,
    @ColumnInfo(name = "difficulty") val difficulty: Double,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "reps") val reps: Int,
    @ColumnInfo(name = "lapses") val lapses: Int,
    @ColumnInfo(name = "phase") val phase: String,
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Int,
    @ColumnInfo(name = "elapsed_days") val elapsedDays: Int,
    @ColumnInfo(name = "learning_steps") val learningSteps: Int,
    @ColumnInfo(name = "scheduler_version") val schedulerVersion: Int,
)

private fun phaseFromString(raw: String): CardPhase = when (raw.lowercase()) {
    "new" -> CardPhase.New
    "learning" -> CardPhase.Learning
    "review" -> CardPhase.Review
    "relearning" -> CardPhase.Relearning
    else -> CardPhase.New
}

internal fun CardStateEntity.toDomain() = CardState(
    questionId = questionId, packId = packId, packVersion = packVersion,
    stability = stability, difficulty = difficulty,
    dueAt = Instant.ofEpochMilli(dueAt),
    lastReviewedAt = lastReviewedAt?.let(Instant::ofEpochMilli),
    reps = reps, lapses = lapses,
    phase = phaseFromString(phase),
    scheduledDays = scheduledDays, elapsedDays = elapsedDays,
    learningSteps = learningSteps, schedulerVersion = schedulerVersion,
)

internal fun CardState.toEntity() = CardStateEntity(
    questionId = questionId, packId = packId, packVersion = packVersion,
    stability = stability, difficulty = difficulty,
    dueAt = dueAt.toEpochMilli(),
    lastReviewedAt = lastReviewedAt?.toEpochMilli(),
    reps = reps, lapses = lapses,
    phase = phase.name.lowercase(),
    scheduledDays = scheduledDays, elapsedDays = elapsedDays,
    learningSteps = learningSteps, schedulerVersion = schedulerVersion,
)
