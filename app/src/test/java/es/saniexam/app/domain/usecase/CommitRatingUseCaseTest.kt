package es.saniexam.app.domain.usecase

import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.model.UserSettings
import es.saniexam.app.domain.repository.CardStateRepository
import es.saniexam.app.domain.repository.ReviewLogRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
import es.saniexam.app.scheduler.CardPhase
import es.saniexam.app.scheduler.FsrsEngine
import es.saniexam.app.scheduler.Rating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Spec `review-session` "Persisted Rating and Append-Only ReviewLog" +
 * `fsrs-scheduler` "Four-Button Rating Contract" + "Reschedule Preview".
 *
 *  - **Commit Good**: CardState replaced with the engine's output; one
 *    `ReviewLog` row appended with `rating=Good`, `reviewedAt=now`,
 *    `elapsedDays`, `scheduledDays`, `previousIntervalDays`,
 *    `newIntervalDays`.
 *  - **Again in Review** increments `lapses` by 1 (and only on the
 *    Review path).
 *  - **Append-only**: no existing `ReviewLog` row is mutated or
 *    deleted; the count grows by exactly 1.
 *  - **Preview == commit within `epsilon`**: `preview[Good]` equals
 *    the committed state when the same `(state, now)` is used.
 *  - **Session resume**: `UserSettings.lastRevealedCardId` is cleared
 *    and `lastSessionQueuePosition` is bumped.
 */
class CommitRatingUseCaseTest {

    private val now: Instant = Instant.parse("2026-06-16T10:00:00Z")
    private val io = Dispatchers.Unconfined
    private val engine = FsrsEngine()

    @Test
    fun `commit Good replaces cardState and appends one reviewLog row`() = runBlocking {
        val previous = matureReviewCardState()
        val cardRepo = FakeCardStateRepository(previous)
        val logRepo = FakeReviewLogRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val useCase = CommitRatingUseCase(
            db = null, // test path: no real DB
            cardStateRepository = cardRepo,
            reviewLogRepository = logRepo,
            userSettingsRepository = settingsRepo,
            engine = engine,
            io = io,
        )

        val result = useCase(previous.questionId, Rating.Good, now)
        // The new card state has been upserted.
        assertEquals(1, cardRepo.upserts.size)
        val newState = cardRepo.upserts.first()
        assertEquals(previous.reps + 1, newState.reps)
        assertEquals(previous.schedulerVersion, newState.schedulerVersion)
        // Exactly one ReviewLog row appended.
        assertEquals(1, logRepo.appends.size)
        val log = logRepo.appends.first()
        assertEquals(previous.questionId, log.questionId)
        assertEquals(Rating.Good, log.rating)
        assertEquals(now, log.reviewedAt)
        assertEquals(previous.scheduledDays, log.previousIntervalDays)
        assertEquals(newState.scheduledDays, log.newIntervalDays)
        // Returned result carries the new state + the full preview.
        assertEquals(newState, result.newCardState)
        assertNotNull(result.preview[Rating.Good])
    }

    @Test
    fun `commit Again in Review increments lapses by 1`() = runBlocking {
        val previous = matureReviewCardState()
        val cardRepo = FakeCardStateRepository(previous)
        val logRepo = FakeReviewLogRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val useCase = CommitRatingUseCase(
            db = null,
            cardStateRepository = cardRepo,
            reviewLogRepository = logRepo,
            userSettingsRepository = settingsRepo,
            engine = engine,
            io = io,
        )

        useCase(previous.questionId, Rating.Again, now)
        val newState = cardRepo.upserts.first()
        assertEquals(previous.lapses + 1, newState.lapses)
        assertTrue("Again must lower stability", newState.stability < previous.stability)
    }

    @Test
    fun `commit Again in New does not increment lapses`() = runBlocking {
        val previous = newCardState()
        val cardRepo = FakeCardStateRepository(previous)
        val logRepo = FakeReviewLogRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val useCase = CommitRatingUseCase(
            db = null,
            cardStateRepository = cardRepo,
            reviewLogRepository = logRepo,
            userSettingsRepository = settingsRepo,
            engine = engine,
            io = io,
        )

        useCase(previous.questionId, Rating.Again, now)
        val newState = cardRepo.upserts.first()
        assertEquals("New card lapses must stay at 0", 0, newState.lapses)
    }

    @Test
    fun `commit is append-only and does not mutate existing reviewLog rows`() = runBlocking {
        val previous = matureReviewCardState()
        val cardRepo = FakeCardStateRepository(previous)
        val existingLog = ReviewLog(
            questionId = "other", reviewedAt = now.minusSeconds(86_400L),
            rating = Rating.Hard, elapsedDays = 0, scheduledDays = 0,
            previousIntervalDays = 0, newIntervalDays = 0,
        )
        val logRepo = FakeReviewLogRepository(initial = listOf(existingLog))
        val settingsRepo = FakeUserSettingsRepository()
        val useCase = CommitRatingUseCase(
            db = null,
            cardStateRepository = cardRepo,
            reviewLogRepository = logRepo,
            userSettingsRepository = settingsRepo,
            engine = engine,
            io = io,
        )

        useCase(previous.questionId, Rating.Good, now)
        // The log snapshot now has the existing row + one new row.
        assertEquals(2, logRepo.snapshot().size)
        // The original row is still present with the same data.
        val originals = logRepo.snapshot().filter { it.questionId == "other" }
        assertEquals(1, originals.size)
        assertEquals(existingLog, originals.first())
    }

    @Test
    fun `commit preview equals the persisted committed state for Good`() = runBlocking {
        val previous = matureReviewCardState()
        val cardRepo = FakeCardStateRepository(previous)
        val logRepo = FakeReviewLogRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val useCase = CommitRatingUseCase(
            db = null,
            cardStateRepository = cardRepo,
            reviewLogRepository = logRepo,
            userSettingsRepository = settingsRepo,
            engine = engine,
            io = io,
        )

        val result = useCase(previous.questionId, Rating.Good, now)
        val previewed = result.preview[Rating.Good]
        val persisted = result.newCardState
        // Preview is computed against the same `now` so it must equal
        // the committed state (the engine shares the code path).
        assertEquals(previewed.stability, persisted.stability, 0.0)
        assertEquals(previewed.difficulty, persisted.difficulty, 0.0)
        assertEquals(previewed.dueAt.toEpochMilli(), persisted.dueAt.toEpochMilli())
        assertEquals(previewed.scheduledDays, persisted.scheduledDays)
        assertEquals(previewed.reps, persisted.reps)
        assertEquals(previewed.lapses, persisted.lapses)
    }

    @Test
    fun `commit clears lastRevealedCardId and bumps queue position in userSettings`() = runBlocking {
        val previous = matureReviewCardState()
        val cardRepo = FakeCardStateRepository(previous)
        val logRepo = FakeReviewLogRepository()
        val initialSettings = UserSettings(
            lastRevealedCardId = previous.questionId,
            lastSessionQueuePosition = 3,
            lastSessionAt = now.minusSeconds(3600),
            activeCategory = UserSettings.TCAE,
        )
        val settingsRepo = FakeUserSettingsRepository(initial = initialSettings)
        val useCase = CommitRatingUseCase(
            db = null,
            cardStateRepository = cardRepo,
            reviewLogRepository = logRepo,
            userSettingsRepository = settingsRepo,
            engine = engine,
            io = io,
        )

        useCase(previous.questionId, Rating.Good, now)
        val updated = settingsRepo.current()
        assertNull("lastRevealedCardId must be cleared on commit", updated.lastRevealedCardId)
        assertEquals(4, updated.lastSessionQueuePosition)
        assertEquals(now, updated.lastSessionAt)
    }

    @Test
    fun `commit on a stale scheduler version throws`() = runBlocking {
        val stale = matureReviewCardState().copy(schedulerVersion = 99)
        val cardRepo = FakeCardStateRepository(stale)
        val logRepo = FakeReviewLogRepository()
        val settingsRepo = FakeUserSettingsRepository()
        val useCase = CommitRatingUseCase(
            db = null,
            cardStateRepository = cardRepo,
            reviewLogRepository = logRepo,
            userSettingsRepository = settingsRepo,
            engine = engine,
            io = io,
        )

        try {
            useCase(stale.questionId, Rating.Good, now)
            assertTrue("expected IllegalArgumentException for stale version", false)
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
        // Nothing was written: no CardState upsert, no ReviewLog append.
        assertEquals(0, cardRepo.upserts.size)
        assertEquals(0, logRepo.appends.size)
    }

    // --- helpers + fakes ---

    private fun matureReviewCardState(): CardState = CardState(
        questionId = "q1",
        packId = "sanidad-v1",
        packVersion = 1,
        stability = 14.0,
        difficulty = 5.0,
        dueAt = now,
        lastReviewedAt = now.minusSeconds(14 * 86_400L),
        reps = 4,
        lapses = 0,
        phase = CardPhase.Review,
        scheduledDays = 14,
        elapsedDays = 14,
        learningSteps = 0,
        schedulerVersion = 1,
    )

    private fun newCardState(): CardState = CardState(
        questionId = "q2",
        packId = "sanidad-v1",
        packVersion = 1,
        stability = 0.0,
        difficulty = 0.0,
        dueAt = now,
        lastReviewedAt = null,
        reps = 0,
        lapses = 0,
        phase = CardPhase.New,
        scheduledDays = 0,
        elapsedDays = 0,
        learningSteps = 0,
        schedulerVersion = 1,
    )

    private class FakeCardStateRepository(initial: CardState) : CardStateRepository {
        val upserts: MutableList<CardState> = mutableListOf()
        private var state: CardState = initial
        override fun observeDue(now: Instant, limit: Int): Flow<List<CardState>> = MutableStateFlow(listOf(state))
        override suspend fun get(questionId: String): CardState? = if (state.questionId == questionId) state else null
        override suspend fun upsert(state: CardState) { upserts.add(state); this.state = state }
        override suspend fun count(): Int = 1
        override suspend fun countDue(now: Instant): Int = 1
        override suspend fun getWithQuestion(questionId: String) = null
        override suspend fun listDue(now: Instant, limit: Int): List<es.saniexam.app.domain.model.CardStateWithQuestion> = emptyList()
        override suspend fun listDueByCategory(
            now: Instant,
            category: String,
            limit: Int,
        ): List<es.saniexam.app.domain.model.CardStateWithQuestion> = emptyList()
        override suspend fun deleteAll() { state = state.copy(reps = 0) }
        override suspend fun replaceAll(states: List<CardState>) { if (states.isNotEmpty()) state = states.first() }
    }

    private class FakeReviewLogRepository(initial: List<ReviewLog> = emptyList()) : ReviewLogRepository {
        val appends: MutableList<ReviewLog> = mutableListOf()
        private val state = MutableStateFlow(initial)
        override fun observeAll(): Flow<List<ReviewLog>> = state
        override suspend fun count(): Int = state.value.size
        override suspend fun append(log: ReviewLog) { appends.add(log); state.value = state.value + log }
        override suspend fun snapshot(): List<ReviewLog> = state.value
        override suspend fun replaceAll(logs: List<ReviewLog>) { state.value = logs }
    }

    private class FakeUserSettingsRepository(initial: UserSettings = UserSettings.Default) : UserSettingsRepository {
        private var state: UserSettings = initial
        fun current(): UserSettings = state
        override suspend fun get(): UserSettings = state
        override suspend fun update(settings: UserSettings) { state = settings }
    }
}
