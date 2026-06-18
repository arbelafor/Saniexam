package es.saniexam.app.presentation.exam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * Composable entry point for the Exam destination. Hilt-injects the
 * [ExamViewModel], subscribes to its `StateFlow` + `SharedFlow` with
 * lifecycle awareness, and forwards the stateless [ExamScreen] the
 * resolved state plus the navigation callbacks.
 *
 * The countdown tick is driven by a `LaunchedEffect` coroutine: it
 * fires every [TICK_INTERVAL_MS] and calls [ExamViewModel.tick] with
 * a fresh `Instant` produced by [ExamViewModel.now] (the same Hilt
 * injected [java.time.Clock] the use case sees, so the time source
 * is single-sourced — PR7 fix for the previous W5 deviation). The
 * ViewModel itself is `tick(now)`-agnostic — tests drive `tick`
 * directly with a controlled `now`.
 */
@Composable
fun ExamRoute(
    onBack: () -> Unit,
    onSessionEnd: () -> Unit,
    viewModel: ExamViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            if (event is ExamUiEvent.SessionEnd) onSessionEnd()
        }
    }
    // Countdown tick. Cancelled automatically when the destination
    // leaves the composition (NavBackStackEntry disposal).
    LaunchedEffect(viewModel) {
        while (true) {
            delay(TICK_INTERVAL_MS)
            viewModel.tick(viewModel.now())
        }
    }
    ExamScreen(
        state = state,
        onSelectSingle = viewModel::selectSingle,
        onToggleOption = viewModel::toggleOption,
        onSubmitEarly = viewModel::submitEarly,
        onNavigateQuestion = viewModel::goTo,
        onAcknowledge = viewModel::acknowledgeResults,
        onBack = onBack,
    )
}

private const val TICK_INTERVAL_MS: Long = 500L
