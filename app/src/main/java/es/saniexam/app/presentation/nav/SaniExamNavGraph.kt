package es.saniexam.app.presentation.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import es.saniexam.app.presentation.exam.ExamRoute
import es.saniexam.app.presentation.home.HomeRoute
import es.saniexam.app.presentation.review.ReviewRoute
import es.saniexam.app.presentation.settings.SettingsRoute
import es.saniexam.app.presentation.stats.StatsRoute

/**
 * Top-level navigation graph.
 *  - PR4 wires Home, Stats and Settings as direct siblings.
 *  - PR5 adds the Review destination. The session-end callback pops
 *    back to Home and asks the Stats view model to refresh (when the
 *    user next visits Stats, the new row counts are visible).
 *  - PR6 adds the Exam destination. The "Iniciar simulación" CTA on
 *    Home is enabled when at least one question is in the active
 *    pack; the exam session is in-memory only and the session-end
 *    callback pops back to Home without touching the FSRS state.
 */
@Composable
fun SaniExamNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SaniExamDestinations.HOME_ROUTE) {
        composable(SaniExamDestinations.HOME_ROUTE) {
            HomeRoute(
                onOpenStats = { navController.navigate(SaniExamDestinations.STATS_ROUTE) },
                onOpenSettings = { navController.navigate(SaniExamDestinations.SETTINGS_ROUTE) },
                onOpenReview = { navController.navigate(SaniExamDestinations.REVIEW_ROUTE) },
                onOpenExam = { navController.navigate(SaniExamDestinations.EXAM_ROUTE) },
            )
        }
        composable(SaniExamDestinations.STATS_ROUTE) {
            StatsRoute(onBack = { navController.popBackStack() })
        }
        composable(SaniExamDestinations.SETTINGS_ROUTE) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(SaniExamDestinations.REVIEW_ROUTE) {
            ReviewRoute(
                onBack = { navController.popBackStack() },
                onSessionEnd = { navController.popBackStack() },
            )
        }
        composable(SaniExamDestinations.EXAM_ROUTE) {
            ExamRoute(
                onBack = { navController.popBackStack() },
                onSessionEnd = { navController.popBackStack() },
            )
        }
    }
}
