package es.saniexam.app.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Composable entry point for the Home destination. Hilt-injects the
 * [HomeViewModel], subscribes to its `StateFlow` with lifecycle
 * awareness, and forwards the stateless [HomeScreen] the resolved
 * state plus the navigation callbacks.
 */
@Composable
fun HomeRoute(
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenExam: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onRetry = viewModel::refresh,
        onOpenStats = onOpenStats,
        onOpenSettings = onOpenSettings,
        onOpenReview = onOpenReview,
        onOpenExam = onOpenExam,
    )
}
