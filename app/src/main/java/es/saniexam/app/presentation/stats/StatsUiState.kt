package es.saniexam.app.presentation.stats

/**
 * State of the Stats surface. The screen is read-only; the only
 * non-static content is the three values from [es.saniexam.app.domain.usecase.Stats].
 * The sealed hierarchy mirrors the spec scenarios:
 *  - [Loading] before the first emission.
 *  - [Empty] when `totalReviews == 0` (no data yet, friendly es-ES message).
 *  - [Insufficient] when `0 < totalReviews < 5` ("Datos insuficientes" for retention).
 *  - [Ready] once we have at least the 5-row threshold.
 */
sealed interface StatsUiState {
    data object Loading : StatsUiState
    data object Empty : StatsUiState
    data class Insufficient(val totalReviews: Int) : StatsUiState
    data class Ready(
        val streakDays: Int,
        val totalReviews: Int,
        /** 0f..1f, always present in [Ready] (>= MIN_RETENTION_SAMPLE rows). */
        val retention30d: Float,
    ) : StatsUiState
}
