package es.saniexam.app.domain.model

/**
 * Single answer option. v1 schema requires exactly one `isCorrect=true`
 * per question (enforced by `DatasetImporter`); multi-correct is a v1.x
 * extension gated on a future spec change.
 */
data class Option(
    val id: String,
    val questionId: String,
    val ordinal: Int,
    val text: String,
    val isCorrect: Boolean,
)
