package es.saniexam.app.presentation.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.saniexam.app.R
import es.saniexam.app.domain.model.SubjectPack

/**
 * Stateless Home surface. State and events are passed in by the
 * `HomeRoute` wrapper (which owns the ViewModel hookup).
 *
 * The "Repasar" CTA is enabled when at least one card is due
 * (PR5 work). The "Iniciar simulación" CTA is enabled when at least
 * one question is in the active pack (PR6 work). When the pack is
 * empty / not yet applied, the Exam CTA is rendered as a disabled
 * placeholder so the layout is stable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenExam: () -> Unit,
    onShowBack: Boolean = false,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.home_title)) },
                navigationIcon = {
                    if (onShowBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.cd_back),
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(text = stringResource(id = R.string.settings_title))
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
                HomeUiState.Loading -> HomeLoading()
                is HomeUiState.Empty -> HomeEmpty(state.pack, onRetry)
                is HomeUiState.Error -> HomeError(state, onRetry)
                is HomeUiState.Ready -> HomeReady(state, onOpenStats, onOpenReview, onOpenExam)
            }
        }
    }
}

@Composable
private fun HomeLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.home_loading))
    }
}

@Composable
private fun HomeEmpty(pack: SubjectPack?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = pack?.let { stringResource(id = R.string.home_empty_pack_label, it.id) }
                ?: stringResource(id = R.string.home_empty_no_pack),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) { Text(text = stringResource(id = R.string.home_retry)) }
    }
}

@Composable
private fun HomeError(state: HomeUiState.Error, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.home_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorReasonText(state.reason),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.questionId?.let { qid ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.home_error_question, qid),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) { Text(text = stringResource(id = R.string.home_retry)) }
    }
}

@Composable
private fun HomeReady(
    state: HomeUiState.Ready,
    onOpenStats: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenExam: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(id = R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        PackCard(state.pack)
        CountsRow(state)
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryCtas(
            onOpenStats = onOpenStats,
            onOpenReview = onOpenReview,
            onOpenExam = onOpenExam,
            canStartReview = state.dueToday > 0,
            canStartExam = state.totalQuestions > 0,
        )
    }
}

@Composable
private fun PackCard(pack: SubjectPack) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(id = R.string.home_pack_id, pack.id),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(id = R.string.home_pack_version, pack.version),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(id = R.string.home_pack_attribution, pack.sourceAttribution),
                style = MaterialTheme.typography.bodySmall,
            )
            if (pack.license.isNotBlank()) {
                Text(
                    text = stringResource(id = R.string.home_pack_license, pack.license),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CountsRow(state: HomeUiState.Ready) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CountCard(
            label = stringResource(id = R.string.home_count_questions),
            value = state.totalQuestions.toString(),
            modifier = Modifier.weight(1f),
        )
        CountCard(
            label = stringResource(id = R.string.home_count_due_today),
            value = state.dueToday.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium)
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PrimaryCtas(
    onOpenStats: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenExam: () -> Unit,
    canStartReview: Boolean,
    canStartExam: Boolean,
) {
    // The "Repasar" CTA is the first writing surface. It is enabled
    // when at least one card is due; the spec "Daily Due Queue" empty
    // path is exercised by leaving it disabled (so a user with no
    // reviews lands on the Stats screen).
    val reviewDesc = stringResource(id = R.string.home_review_desc)
    Button(
        onClick = onOpenReview,
        enabled = canStartReview,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = reviewDesc },
    ) { Text(text = stringResource(id = R.string.home_review_cta)) }
    val statsDesc = stringResource(id = R.string.home_open_stats_desc)
    Button(
        onClick = onOpenStats,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = statsDesc },
    ) { Text(text = stringResource(id = R.string.home_open_stats)) }
    // Exam (PR6). Enabled when the active pack has at least one
    // question (spec `exam-simulation` "Start an exam" requires a
    // deterministic question set). When the pack is empty, the CTA
    // stays disabled so the affordance is honest.
    val examDesc = if (canStartExam) {
        stringResource(id = R.string.home_exam_desc)
    } else {
        stringResource(id = R.string.home_exam_unavailable_desc)
    }
    OutlinedButton(
        onClick = onOpenExam,
        enabled = canStartExam,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = examDesc },
    ) { Text(text = stringResource(id = R.string.home_exam_cta)) }
}

@Composable
private fun errorReasonText(reason: es.saniexam.app.data.ingest.DatasetImportException.Reason): String = when (reason) {
    es.saniexam.app.data.ingest.DatasetImportException.Reason.AssetMissing -> stringResource(id = R.string.import_err_asset_missing)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.AssetUnreadable -> stringResource(id = R.string.import_err_asset_unreadable)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.ManifestMissing -> stringResource(id = R.string.import_err_manifest_missing)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.ManifestMismatch -> stringResource(id = R.string.import_err_manifest_mismatch)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.PackIdMismatch -> stringResource(id = R.string.import_err_pack_id_mismatch)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.VersionMismatch -> stringResource(id = R.string.import_err_version_mismatch)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.ChecksumMismatch -> stringResource(id = R.string.import_err_checksum_mismatch)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.MissingAttribution -> stringResource(id = R.string.import_err_missing_attribution)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.QuestionMissingFields -> stringResource(id = R.string.import_err_question_missing_fields)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.ZeroOrMultipleCorrectOptions -> stringResource(id = R.string.import_err_zero_or_multi_correct)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.OrphanTopicReference -> stringResource(id = R.string.import_err_orphan_topic)
    es.saniexam.app.data.ingest.DatasetImportException.Reason.DuplicateQuestionId -> stringResource(id = R.string.import_err_duplicate_question)
}
