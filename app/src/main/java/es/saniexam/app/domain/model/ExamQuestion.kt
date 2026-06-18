package es.saniexam.app.domain.model

/**
 * One question inside an in-memory [ExamSession]. The view-model pairs
 * the immutable [Question] with the immutable [Option] list so the
 * Composable can render both in a single source of truth.
 *
 * v1 ships single-correct questions (the `Option` model carries a
 * single `isCorrect=true` row per question). The spec
 * `exam-simulation` "Multi-correct schema" requires the scoring path
 * to be ready for v1.x schema bumps that allow N>=2 correct options
 * per question; the [correctOptionIds] helper below is the seam.
 */
data class ExamQuestion(
    val question: Question,
    val options: List<Option>,
) {
    /** True when the schema ships more than one `isCorrect=true` option. */
    val isMultiCorrect: Boolean
        get() = options.count { it.isCorrect } > 1

    /** The set of option ids that are correct for this question. */
    val correctOptionIds: Set<String>
        get() = options.asSequence().filter { it.isCorrect }.map { it.id }.toSet()
}
