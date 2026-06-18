package es.saniexam.app.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.saniexam.app.R

/**
 * Stateless Stats surface. The four [StatsUiState] branches each map
 * to a single spec scenario from `openspec/.../progress-stats/spec.md`:
 *  - [StatsUiState.Loading] → first paint.
 *  - [StatsUiState.Empty] → "0 días" + friendly empty-state message.
 *  - [StatsUiState.Insufficient] → "Datos insuficientes" for retention.
 *  - [StatsUiState.Ready] → the three numbers reconcile with `ReviewLog`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.stats_title)) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                StatsUiState.Loading -> Loading()
                StatsUiState.Empty -> EmptyBlock()
                is StatsUiState.Insufficient -> InsufficientBlock(state.totalReviews)
                is StatsUiState.Ready -> ReadyBlock(state)
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun EmptyBlock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = pluralStringResource(id = R.plurals.stats_streak_label, 0, 0),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer16()
        Text(
            text = pluralStringResource(id = R.plurals.stats_total_label, 0, 0),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer16()
        Text(
            text = stringResource(id = R.string.stats_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InsufficientBlock(total: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpacerH(8)
        StatCard(
            label = stringResource(id = R.string.stats_streak_label_short),
            value = stringResource(id = R.string.stats_value_pending),
        )
        StatCard(
            label = stringResource(id = R.string.stats_total_label_short),
            value = total.toString(),
        )
        StatCard(
            label = stringResource(id = R.string.stats_retention_label),
            value = stringResource(id = R.string.stats_insufficient),
        )
    }
}

@Composable
private fun ReadyBlock(state: StatsUiState.Ready) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpacerH(8)
        StatCard(
            label = stringResource(id = R.string.stats_streak_label_short),
            value = pluralDays(state.streakDays),
        )
        StatCard(
            label = stringResource(id = R.string.stats_total_label_short),
            value = pluralReviews(state.totalReviews),
        )
        StatCard(
            label = stringResource(id = R.string.stats_retention_label),
            value = formatRetention(state.retention30d),
        )
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Spacer16() { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp)) }
@Composable private fun SpacerH(dp: Int) { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(dp.dp)) }

@Composable
private fun pluralDays(days: Int): String = when (days) {
    0 -> stringResource(id = R.string.stats_days_zero, days)
    1 -> stringResource(id = R.string.stats_days_one, days)
    else -> stringResource(id = R.string.stats_days_other, days)
}

@Composable
private fun pluralReviews(total: Int): String = when (total) {
    1 -> stringResource(id = R.string.stats_reviews_one, total)
    else -> stringResource(id = R.string.stats_reviews_other, total)
}

@Composable
private fun formatRetention(value: Float): String {
    val pct = (value * 100f).let { (it * 10f).toLong() / 10f } // 1 decimal
    return stringResource(id = R.string.stats_retention_value, pct)
}
