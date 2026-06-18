package es.saniexam.app.domain.model

/**
 * Scored result of a finished [ExamSession]. Pure data; the ViewModel
 * emits it inside [es.saniexam.app.presentation.exam.ExamUiState.Results]
 * and the results Composable renders it.
 *
 * The "No FSRS Perturbation" spec requirement is enforced **before**
 * the results are computed: [RunExamSessionUseCase] never mutates
 * [CardState] / [ReviewLog] and the ViewModel never calls
 * [es.saniexam.app.domain.usecase.CommitRatingUseCase]. The tests
 * in `RunExamSessionUseCaseTest` and `ExamViewModelTest` assert
 * that contract structurally and dynamically.
 */
data class ExamResults(
    val total: Int,
    val correct: Int,
    val incorrect: Int,
    val blank: Int,
    val percentage: Double,
    val elapsedSeconds: Long,
    val perQuestion: List<ExamResultRow>,
)

/**
 * One row in the per-question review list (spec "Results summary"
 * + "scrollable list of 50 rows, each with the prompt, the user's
 * selection, and the correct answer").
 */
data class ExamResultRow(
    val questionId: String,
    val prompt: String,
    val correctOptionIds: Set<String>,
    val correctOptionTexts: List<String>,
    val selectedOptionIds: Set<String>,
    val selectedOptionTexts: List<String>,
    val isCorrect: Boolean,
    val isBlank: Boolean,
)
