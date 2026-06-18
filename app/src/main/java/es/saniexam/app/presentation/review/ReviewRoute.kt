package es.saniexam.app.presentation.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.saniexam.app.scheduler.Rating

/**
 * Composable entry point for the Review destination. Hilt-injects the
 * [ReviewViewModel], subscribes to its `StateFlow` + `SharedFlow` with
 * lifecycle awareness, and forwards the stateless [ReviewScreen] the
 * resolved state plus the navigation callbacks.
 *
 * `onSessionEnd` is invoked exactly once when the queue is exhausted;
 * the caller (NavGraph) is responsible for popping back to Home and
 * asking the Stats screen to refresh.
 */
@Composable
fun ReviewRoute(
    onBack: () -> Unit,
    onSessionEnd: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            if (event is ReviewUiEvent.SessionEnd) onSessionEnd()
        }
    }
    ReviewScreen(
        state = state,
        onReveal = viewModel::onReveal,
        onRate = { rating: Rating -> viewModel.onRate(rating) },
        onBack = onBack,
    )
}
