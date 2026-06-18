package es.saniexam.app.presentation.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.saniexam.app.R
import es.saniexam.app.domain.model.ExamQuestion

/**
 * Stateless Exam surface (spec `exam-simulation`). State and events
 * are passed in by [ExamRoute]. Mirrors the [ReviewScreen] pattern
 * (state hoisting + sealed UI state machine + a Hilt-injected
 * `ViewModel` owned by the `Route` wrapper).
 *
 *  - The active screen hides the correct option(s) until the user
 *    taps an option (binary "select → scored at submit"). The spec
 *    does not require a reveal step for the exam surface; the
 *    Reveal-on-Tap rule belongs to the Review surface.
 *  - The "Entregar" button is the only way to leave the active
 *    state during a session; the ViewModel's [ExamViewModel.tick]
 *    handles the auto-submit-on-zero path.
 *  - The Results branch is a separate sub-composable
 *    ([ExamResultsScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    state: ExamUiState,
    onSelectSingle: (questionId: String, optionId: String) -> Unit,
    onToggleOption: (questionId: String, optionId: String) -> Unit,
    onSubmitEarly: () -> Unit,
    onNavigateQuestion: (index: Int) -> Unit,
    onAcknowledge: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.exam_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                ExamUiState.Loading -> ExamLoading()
                is ExamUiState.Error -> ExamError(state)
                is ExamUiState.Active -> ExamActive(
                    state = state,
                    onSelectSingle = onSelectSingle,
                    onToggleOption = onToggleOption,
                    onSubmitEarly = onSubmitEarly,
                    onNavigateQuestion = onNavigateQuestion,
                )
                is ExamUiState.Results -> ExamResultsScreen(
                    state = state,
                    onAcknowledge = onAcknowledge,
                )
            }
        }
    }
}

@Composable
private fun ExamLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.exam_loading))
    }
}

@Composable
private fun ExamError(state: ExamUiState.Error) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.exam_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExamActive(
    state: ExamUiState.Active,
    onSelectSingle: (questionId: String, optionId: String) -> Unit,
    onToggleOption: (questionId: String, optionId: String) -> Unit,
    onSubmitEarly: () -> Unit,
    onNavigateQuestion: (index: Int) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ExamHeader(
            index = state.currentIndex,
            total = state.session.totalQuestions,
            remainingSeconds = state.remainingSeconds,
        )
        val current = state.session.questions[state.currentIndex]
        QuestionCard(
            examQuestion = current,
            selectedIds = state.session.answers[current.question.id].orEmpty(),
            onSelectSingle = { onSelectSingle(current.question.id, it) },
            onToggleOption = { onToggleOption(current.question.id, it) },
        )
        NavRow(
            index = state.currentIndex,
            total = state.session.questions.size,
            onNavigate = onNavigateQuestion,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SubmitButton(onSubmitEarly = onSubmitEarly)
    }
}

@Composable
private fun ExamHeader(index: Int, total: Int, remainingSeconds: Long) {
    val positionText = stringResource(id = R.string.exam_position, index + 1, total)
    val timerText = stringResource(id = R.string.exam_timer, formatTimer(remainingSeconds))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = positionText,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = timerText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { contentDescription = timerText },
        )
    }
}

@Composable
private fun QuestionCard(
    examQuestion: ExamQuestion,
    selectedIds: Set<String>,
    onSelectSingle: (optionId: String) -> Unit,
    onToggleOption: (optionId: String) -> Unit,
) {
    val isMulti = examQuestion.isMultiCorrect
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = examQuestion.question.prompt,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        for (option in examQuestion.options.sortedBy { it.ordinal }) {
            val isSelected = option.id in selectedIds
            OptionRow(
                text = option.text,
                isSelected = isSelected,
                onClick = {
                    if (isMulti) onToggleOption(option.id) else onSelectSingle(option.id)
                },
            )
        }
    }
}

@Composable
private fun OptionRow(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val desc = stringResource(id = R.string.exam_select_option_desc, text)
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = textColor,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun NavRow(index: Int, total: Int, onNavigate: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { onNavigate(index - 1) },
            enabled = index > 0,
            modifier = Modifier.weight(1f),
        ) { Text(text = stringResource(id = R.string.exam_prev)) }
        OutlinedButton(
            onClick = { onNavigate(index + 1) },
            enabled = index < total - 1,
            modifier = Modifier.weight(1f),
        ) { Text(text = stringResource(id = R.string.exam_next)) }
    }
}

@Composable
private fun SubmitButton(onSubmitEarly: () -> Unit) {
    val desc = stringResource(id = R.string.exam_submit_desc)
    Button(
        onClick = onSubmitEarly,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
    ) { Text(text = stringResource(id = R.string.exam_submit)) }
}

/**
 * Format the countdown as `MM:SS` (or `HH:MM:SS` for sessions longer
 * than one hour). Negative values are clamped to zero.
 */
internal fun formatTimer(remainingSeconds: Long): String {
    val safe = remainingSeconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
