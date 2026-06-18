package es.saniexam.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import es.saniexam.app.domain.model.Option
import es.saniexam.app.domain.model.Question

@Entity(
    tableName = "question",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = SubjectPackEntity::class,
        parentColumns = ["id", "version"],
        childColumns = ["pack_id", "pack_version"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["pack_id", "pack_version"]),
        Index(value = ["topic_id"]),
    ],
)
data class QuestionEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "pack_version") val packVersion: Int,
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "prompt") val prompt: String,
    @ColumnInfo(name = "explanation") val explanation: String?,
    @ColumnInfo(name = "official_year") val officialYear: Int?,
    @ColumnInfo(name = "official_source_ref") val officialSourceRef: String?,
)

internal fun QuestionEntity.toDomain() = Question(
    id, packId, packVersion, topicId, prompt, explanation, officialYear, officialSourceRef,
)
internal fun Question.toEntity() = QuestionEntity(
    id, packId, packVersion, topicId, prompt, explanation, officialYear, officialSourceRef,
)

@Entity(
    tableName = "option",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = QuestionEntity::class,
        parentColumns = ["id"],
        childColumns = ["question_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["question_id"])],
)
data class OptionEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "ordinal") val ordinal: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
)

internal fun OptionEntity.toDomain() = Option(id, questionId, ordinal, text, isCorrect)
internal fun Option.toEntity() = OptionEntity(id, questionId, ordinal, text, isCorrect)
