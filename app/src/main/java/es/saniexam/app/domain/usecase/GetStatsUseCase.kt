package es.saniexam.app.domain.usecase

import es.saniexam.app.di.DefaultDispatcher
import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.repository.ReviewLogRepository
import es.saniexam.app.scheduler.Rating
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Computes the three numbers the Stats screen renders, all derived
 * exclusively from the append-only [ReviewLog] (`progress-stats`
 * "Read-Only Derivation From ReviewLog"). The use case is pure read;
 * opening the Stats screen is a side-effect-free read of the log.
 *
 *  - **streakDays** — consecutive days ending today (local time) on
 *    which the user committed at least one rating. A day with zero
 *    commits breaks the streak.
 *  - **totalReviews** — `COUNT(*)` over `ReviewLog`.
 *  - **retention30d** — percentage of `ReviewLog` rows in the last 30
 *    days (inclusive of today, local timezone) whose `rating` is
 *    `Good` or `Easy`. Returns `null` when fewer than
 *    [MIN_RETENTION_SAMPLE] rows exist ("Datos insuficientes").
 *
 * PR4 notes: the [ReviewLogRepository] is currently a stub returning
 * an empty list (the `ReviewLog` Room table is PR5 work). Every stat
 * is therefore the empty-state value, which is exactly the honest
 * "no data yet" UI.
 */
class GetStatsUseCase @Inject constructor(
    private val reviewLogRepository: ReviewLogRepository,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend operator fun invoke(): Stats = withContext(cpu) {
        val logs = reviewLogRepository.observeAllOnce()
        val today = LocalDate.now(clock.withZone(zone))
        Stats(
            streakDays = computeStreak(logs, today),
            totalReviews = logs.size,
            retention30d = computeRetention30d(logs, today),
        )
    }

    private fun computeStreak(logs: List<ReviewLog>, today: LocalDate): Int {
        if (logs.isEmpty()) return 0
        val days = logs.asSequence()
            .map { Instant.ofEpochMilli(it.reviewedAt.toEpochMilli()).atZone(zone).toLocalDate() }
            .toSortedSet(reverseOrder())
        var cursor = today
        var streak = 0
        while (days.contains(cursor)) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun computeRetention30d(logs: List<ReviewLog>, today: LocalDate): Float? {
        if (logs.size < MIN_RETENTION_SAMPLE) return null
        val windowStart = today.minusDays(29) // inclusive of today → 30 days
        var inWindow = 0
        var goodOrEasy = 0
        for (log in logs) {
            val day = Instant.ofEpochMilli(log.reviewedAt.toEpochMilli()).atZone(zone).toLocalDate()
            if (day.isBefore(windowStart) || day.isAfter(today)) continue
            inWindow += 1
            if (log.rating == Rating.Good || log.rating == Rating.Easy) goodOrEasy += 1
        }
        if (inWindow == 0) return null
        return goodOrEasy.toFloat() / inWindow.toFloat()
    }

    companion object {
        /** `progress-stats` "Insufficient history": fewer than 5 reviews total. */
        const val MIN_RETENTION_SAMPLE: Int = 5
    }
}

data class Stats(
    val streakDays: Int,
    val totalReviews: Int,
    /** Null when fewer than [GetStatsUseCase.MIN_RETENTION_SAMPLE] reviews exist. */
    val retention30d: Float?,
)

private suspend fun ReviewLogRepository.observeAllOnce(): List<ReviewLog> = observeAll().first()
