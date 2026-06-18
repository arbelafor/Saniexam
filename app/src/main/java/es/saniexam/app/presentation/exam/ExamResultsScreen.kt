package es.saniexam.app.presentation.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.saniexam.app.R
import es.saniexam.app.domain.model.ExamResultRow

/**
 * Results surface (spec `exam-simulation` "Results Screen"). Shows
 * the summary (correct, incorrect, blank, percentage, elapsed time)
 * and a scrollable per-question review list. The "Volver al inicio"
 * button emits [ExamUiEvent.SessionEnd]; the NavGraph pops back to
 * Home. Per spec "Re-attempt guard", the Home screen's FSRS due
 * queue is unchanged because the Exam never wrote to [CardState] or
 * [ReviewLog].
 */
@Composable
fun ExamResultsScreen(
    state: ExamUiState.Results,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(id = R.string.exam_results_title),
            style = MaterialTheme.typography.titleLarge,
        )
        SummaryRow(state)
        PerQuestionList(state.results.perQuestion)
        Spacer(modifier = Modifier.height(8.dp))
        AcknowledgeButton(onAcknowledge = onAcknowledge)
    }
}

@Composable
private fun SummaryRow(state: ExamUiState.Results) {
    val r = state.results
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryLine(
                label = stringResource(id = R.string.exam_correct_label),
                value = r.correct.toString(),
            )
            SummaryLine(
                label = stringResource(id = R.string.exam_incorrect_label),
                // Spec `exam-simulation` "Timer expires → unanswered =
                // incorrect": the `incorrect` total includes the blank
                // count. The breakdown below re-states the blank
                // subset so the user can see how many of the
                // `incorrect` were simply left unanswered.
                value = r.incorrect.toString(),
            )
            if (r.blank > 0) {
                SummaryLine(
                    label = stringResource(id = R.string.exam_blank_label),
                    value = stringResource(
                        id = R.string.exam_blank_of_incorrect,
                        r.blank,
                        r.incorrect,
                    ),
                )
            }
            SummaryLine(
                label = stringResource(id = R.string.exam_score_label),
                value = stringResource(id = R.string.exam_score_value, r.percentage),
            )
            SummaryLine(
                label = stringResource(id = R.string.exam_elapsed_label),
                value = stringResource(id = R.string.exam_elapsed_value, r.elapsedSeconds / 60, r.elapsedSeconds % 60),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PerQuestionList(rows: List<ExamResultRow>) {
    Text(
        text = stringResource(id = R.string.exam_review_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((index, row) in rows.withIndex()) {
            QuestionReviewCard(index = index + 1, row = row)
        }
    }
}

@Composable
private fun QuestionReviewCard(index: Int, row: ExamResultRow) {
    val containerColor = when {
        row.isCorrect -> MaterialTheme.colorScheme.secondaryContainer
        row.isBlank -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(id = R.string.exam_prompt_index, index),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = row.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (row.isBlank) {
                Text(
                    text = stringResource(id = R.string.exam_no_answer),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = stringResource(id = R.string.exam_your_answer) + ":",
                    style = MaterialTheme.typography.labelSmall,
                )
                row.selectedOptionTexts.forEach { text ->
                    Text(
                        text = "• $text",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.exam_correct_answer) + ":",
                style = MaterialTheme.typography.labelSmall,
            )
            row.correctOptionTexts.forEach { text ->
                Text(
                    text = "• $text",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AcknowledgeButton(onAcknowledge: () -> Unit) {
    val desc = stringResource(id = R.string.exam_back_desc)
    Button(
        onClick = onAcknowledge,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
    ) { Text(text = stringResource(id = R.string.exam_back_to_home)) }
}
