package es.saniexam.app.presentation.nav

/**
 * Route constants for the navigation graph. Centralised so the graph,
 * the tests, and the back-stack handling all reference the same string
 * keys. Strings (not typed NavType wrappers) keep the graph readable
 * for the size-budget review.
 */
object SaniExamDestinations {
    const val HOME_ROUTE: String = "home"
    const val STATS_ROUTE: String = "stats"
    const val SETTINGS_ROUTE: String = "settings"
    const val REVIEW_ROUTE: String = "review"
    const val EXAM_ROUTE: String = "exam"
}
