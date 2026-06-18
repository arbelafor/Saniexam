package es.saniexam.app.domain.usecase

import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.repository.ReviewLogRepository
import es.saniexam.app.scheduler.Rating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Verifies the spec scenarios for `GetStatsUseCase` (PR4 slice). The
 * use case reads from [ReviewLogRepository] which is currently a stub
 * returning an empty list; the tests use hand-rolled fakes to simulate
 * the post-PR5 state where `CommitRatingUseCase` appends rows.
 *
 *  - **Streak with activity today** → `streakDays = 7` after 7 consecutive
 *    days ending today with at least one commit per day.
 *  - **Streak broken by a missed day** → `streakDays = 1` (today only).
 *  - **No activity ever** → `streakDays = 0`, `totalReviews = 0`, `retention30d = null`.
 *  - **All Good or Easy in window** → `retention30d = 1.0f`.
 *  - **Mixed ratings** → `retention30d = 0.7f` (70% Good+Easy).
 *  - **Insufficient history** → `retention30d = null`, `totalReviews > 0`,
 *    `totalReviews < 5`.
 */
class GetStatsUseCaseTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.parse("2026-06-16")

    @Test
    fun `empty review log yields zero streak and zero total and null retention`() = runBlocking {
        val repo = FakeReviewLogRepository(logs = emptyList())
        val useCase = GetStatsUseCase(repo, Dispatchers.Unconfined, fixedClock(today), zone)

        val stats = useCase()
        assertEquals(0, stats.streakDays)
        assertEquals(0, stats.totalReviews)
        assertNull(stats.retention30d)
    }

    @Test
    fun `streak counts seven consecutive days ending today`() = runBlocking {
        val logs = (0..6).map { offset ->
            log(
                qid = "q$offset",
                rating = Rating.Good,
                at = today.minusDays(offset.toLong()).atStartOfDay(zone).toInstant().plusSeconds(60),
            )
        }
        val repo = FakeReviewLogRepository(logs = logs)
        val useCase = GetStatsUseCase(repo, Dispatchers.Unconfined, fixedClock(today), zone)

        assertEquals(7, useCase().streakDays)
    }

    @Test
    fun `streak broken by a missed day yields one`() = runBlocking {
        val logs = listOf(
            log("q1", Rating.Good, today.atStartOfDay(zone).toInstant().plusSeconds(60)),
            // Day D-2 (yesterday) missed.
            log("q2", Rating.Good, today.minusDays(2).atStartOfDay(zone).toInstant().plusSeconds(60)),
        )
        val repo = FakeReviewLogRepository(logs = logs)
        val useCase = GetStatsUseCase(repo, Dispatchers.Unconfined, fixedClock(today), zone)

        assertEquals(1, useCase().streakDays)
    }

    @Test
    fun `streak with no commit today is zero`() = runBlocking {
        val logs = listOf(
            log("q1", Rating.Good, today.minusDays(1).atStartOfDay(zone).toInstant().plusSeconds(60)),
            log("q2", Rating.Good, today.minusDays(2).atStartOfDay(zone).toInstant().plusSeconds(60)),
        )
        val repo = FakeReviewLogRepository(logs = logs)
        val useCase = GetStatsUseCase(repo, Dispatchers.Unconfined, fixedClock(today), zone)

        assertEquals(0, useCase().streakDays)
    }

    @Test
    fun `all Good or Easy in last 30 days yields 100 percent`() = runBlocking {
        val logs = (0 until 100).map { idx ->
            val rating = if (idx % 2 == 0) Rating.Good else Rating.Easy
            log("q$idx", rating, today.minusDays((idx % 25).toLong()).atStartOfDay(zone).toInstant().plusSeconds(idx.toLong()))
        }
        val repo = FakeReviewLogRepository(logs = logs)
        val useCase = GetStatsUseCase(repo, Dispatchers.Unconfined, fixedClock(today), zone)

        val stats = useCase()
        assertEquals(100, stats.totalReviews)
        assertNotNull(stats.retention30d)
        assertEquals(1.0f, stats.retention30d!!, 0.0001f)
    }

    @Test
    fun `mixed ratings 70 Good 20 Hard 10 Again yields 70 percent`() = runBlocking {
        val now = today.atStartOfDay(zone).toInstant()
        val logs = buildList {
            repeat(70) { add(log("g$it", Rating.Good, now.plusSeconds(it.toLong()))) }
            repeat(20) { add(log("h$it", Rating.Hard, now.plusSeconds((70 + it).toLong()))) }
            repeat(10) { add(log("a$it", Rating.Again, now.plusSeconds((90 + it).toLong()))) }
        }
        val repo = FakeReviewLogRepository(logs = logs)
        val useCase = GetStatsUseCase(repo, Dispatchers.Unconfined, fixedClock(today), zone)

        val stats = useCase()
        assertEquals(100, stats.totalReviews)
        assertNotNull(stats.retention30d)
        assertEquals(0.7f, stats.retention30d!!, 0.0001f)
    }

    @Test
    fun `fewer than 5 reviews yields null retention (insufficient history)`() = runBlocking {
        val logs = listOf(
            log("q1", Rating.Good, today.atStartOfDay(zone).toInstant()),
            log("q2", Rating.Good, today.minusDays(1).atStartOfDay(zone).toInstant()),
            log("q3", Rating.Good, today.minusDays(2).atStartOfDay(zone).toInstant()),
            log("q4", Rating.Good, today.minusDays(3).atStartOfDay(zone).toInstant()),
        )
        val repo = FakeReviewLogRepository(logs = logs)
        val useCase = GetStatsUseCase(repo, Dispatchers.Unconfined, fixedClock(today), zone)

        val stats = useCase()
        assertEquals(4, stats.totalReviews)
        assertNull(stats.retention30d)
    }

    @Test
    fun `rows older than 30 days do not affect retention`() = runBlocking {
        val now = today.atStartOfDay(zone).toInstant()
        val recent = (0 until 10).map { log("ok$it", Rating.Good, now.minusSeconds((it * 60).toLong())) }
        val old = log("old", Rating.Again, today.minusDays(60).atStartOfDay(zone).toInstant())
        val logs = recent + old
        val repo = FakeReviewLogRepository(logs = logs)
        val useCase = GetStatsUseCase(repo, Dispatchers.Unconfined, fixedClock(today), zone)

        val stats = useCase()
        assertEquals(11, stats.totalReviews)
        assertNotNull(stats.retention30d)
        // Retention counts only the 10 in-window rows (all Good) → 100%.
        assertEquals(1.0f, stats.retention30d!!, 0.0001f)
    }

    // --- helpers ---

    private fun log(qid: String, rating: Rating, at: Instant): ReviewLog = ReviewLog(
        questionId = qid,
        reviewedAt = at,
        rating = rating,
        elapsedDays = 0,
        scheduledDays = 0,
        previousIntervalDays = 0,
        newIntervalDays = 0,
    )

    private fun fixedClock(today: LocalDate): Clock = Clock.fixed(
        today.atStartOfDay(zone).toInstant().plusSeconds(3600),
        zone,
    )

    private class FakeReviewLogRepository(logs: List<ReviewLog>) : ReviewLogRepository {
        private val state = MutableStateFlow(logs)
        override fun observeAll(): Flow<List<ReviewLog>> = state
        override suspend fun count(): Int = state.value.size
        override suspend fun append(log: ReviewLog) { state.value = state.value + log }
        override suspend fun snapshot(): List<ReviewLog> = state.value
        override suspend fun replaceAll(logs: List<ReviewLog>) { state.value = logs }
    }
}
