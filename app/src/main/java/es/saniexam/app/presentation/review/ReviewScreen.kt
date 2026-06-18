package es.saniexam.app.presentation.review

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
import es.saniexam.app.domain.model.CardStateWithQuestion
import es.saniexam.app.domain.model.Option
import es.saniexam.app.scheduler.FsrsPreview
import es.saniexam.app.scheduler.Rating
import java.time.Duration
import java.time.Instant

/**
 * Stateless Review surface. State and events are passed in by the
 * `ReviewRoute` wrapper. Mirrors the Home/Stats/Settings pattern:
 *  - The Composable renders one of the four [ReviewUiState] branches
 *    explicitly.
 *  - The reveal action has a Spanish `contentDescription` so TalkBack
 *    announces it ("Mostrar respuesta").
 *  - Each rating button has a Spanish `contentDescription` of the form
 *    "Calificar como <Again|Hard|Good|Easy>" (spec `review-session`
 *    "Reveal-on-Tap and Rating Flow" / a11y).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.review_title)) },
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
                ReviewUiState.Loading -> ReviewLoading()
                ReviewUiState.Empty -> ReviewEmpty()
                is ReviewUiState.Error -> ReviewError(state)
                is ReviewUiState.Active -> ReviewActive(state, onReveal, onRate)
            }
        }
    }
}

@Composable
private fun ReviewLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.review_loading))
    }
}

@Composable
private fun ReviewEmpty() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.review_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.review_empty_message),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReviewError(state: ReviewUiState.Error) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.review_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewActive(
    state: ReviewUiState.Active,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PositionRow(state.queuePosition, state.queueSize)
        PromptCard(state.current)
        OptionsList(state.current.options, state.revealed)
        if (state.revealed) {
            state.current.question.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                ExplanationCard(explanation)
            }
            RatingRow(
                preview = state.preview,
                onRate = onRate,
            )
        } else {
            RevealButton(onReveal)
        }
    }
}

@Composable
private fun PositionRow(position: Int, total: Int) {
    Text(
        text = stringResource(id = R.string.review_position, position, total),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun PromptCard(card: CardStateWithQuestion) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = card.question.prompt,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun OptionsList(options: List<Option>, revealed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options.sortedBy { it.ordinal }) {
            val isCorrect = option.isCorrect
            // Pre-reveal: never mark the correct option visually.
            // Post-reveal: highlight the correct option(s).
            val containerColor = if (revealed && isCorrect) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            val textColor = if (revealed && isCorrect) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = containerColor),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        fontWeight = if (revealed && isCorrect) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplanationCard(explanation: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.review_explanation_label),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RevealButton(onReveal: () -> Unit) {
    val desc = stringResource(id = R.string.review_reveal_desc)
    Button(
        onClick = onReveal,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = desc },
    ) {
        Text(text = stringResource(id = R.string.review_reveal))
    }
}

@Composable
private fun RatingRow(preview: FsrsPreview?, onRate: (Rating) -> Unit) {
    // Four buttons in a 2x2 grid; the order matches the enum (which
    // encodes `Again < Hard < Good < Easy`). Each carries a TalkBack
    // description "Calificar como X" + an interval hint label.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RatingButton(Rating.Again, preview, onRate, modifier = Modifier.weight(1f))
            RatingButton(Rating.Hard, preview, onRate, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RatingButton(Rating.Good, preview, onRate, modifier = Modifier.weight(1f))
            RatingButton(Rating.Easy, preview, onRate, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RatingButton(
    rating: Rating,
    preview: FsrsPreview?,
    onRate: (Rating) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (rating) {
        Rating.Again -> stringResource(id = R.string.rating_again)
        Rating.Hard -> stringResource(id = R.string.rating_hard)
        Rating.Good -> stringResource(id = R.string.rating_good)
        Rating.Easy -> stringResource(id = R.string.rating_easy)
    }
    val now: Instant = Instant.now()
    val interval = preview?.get(rating)?.dueAt
    val hintLabel = interval?.let { intervalHintLabel(it, now) }.orEmpty()
    val desc = stringResource(id = R.string.rating_content_description, label, hintLabel)
    OutlinedButton(
        onClick = { onRate(rating) },
        modifier = modifier.semantics { contentDescription = desc },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            if (hintLabel.isNotEmpty()) {
                Text(
                    text = hintLabel,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Spanish-localised interval hint. Examples:
 *  - < 1 min   → "ahora"  (defensive)
 *  - < 60 min  → "5m"
 *  - < 1 day   → "12h"
 *  - < 30 days → "5d"
 *  - < 365 d   → "3mes"
 *  - else      → "1a"
 */
internal fun intervalHintLabel(dueAt: Instant, now: Instant): String {
    val seconds = Duration.between(now, dueAt).seconds
    if (seconds < 60) return "ahora"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h"
    val days = hours / 24
    if (days < 30) return "${days}d"
    val months = days / 30
    if (months < 12) return "${months}mes"
    val years = days / 365
    return "${years}a"
}
