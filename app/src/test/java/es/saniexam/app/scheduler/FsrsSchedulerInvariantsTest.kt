package es.saniexam.app.scheduler

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec `fsrs-scheduler` functional invariants. The 1000-triple fuzz
 * volume is in [FsrsSchedulerFuzzTest]; this suite covers the structural
 * invariants a small input should already satisfy.
 */
class FsrsSchedulerInvariantsTest {

    private val engine = FsrsEngine()
    private val now: Instant = Instant.parse("2025-06-15T10:00:00Z")

    private fun matureReviewState() = FsrsState(
        stability = 14.0, difficulty = 5.0,
        dueAt = now, lastReviewedAt = now.minusSeconds(14 * 86_400L),
        reps = 4, lapses = 0, phase = CardPhase.Review,
        scheduledDays = 14, elapsedDays = 14,
        learningSteps = 0,
        schedulerVersion = SchedulerVersion.CURRENT,
    )
    private fun learningState() = FsrsState(
        stability = 1.5, difficulty = 5.0,
        dueAt = now.plusSeconds(60), lastReviewedAt = now,
        reps = 1, lapses = 0, phase = CardPhase.Learning,
        scheduledDays = 0, elapsedDays = 0,
        learningSteps = 0,
        schedulerVersion = SchedulerVersion.CURRENT,
    )
    private fun relearningState() = FsrsState(
        stability = 4.0, difficulty = 6.5,
        dueAt = now.plusSeconds(60), lastReviewedAt = now,
        reps = 5, lapses = 1, phase = CardPhase.Relearning,
        scheduledDays = 0, elapsedDays = 0,
        learningSteps = 0,
        schedulerVersion = SchedulerVersion.CURRENT,
    )
    private fun nonNewStates() = listOf(learningState(), matureReviewState(), relearningState())

    @Test
    fun `non-New states preserve Again less than Hard less than Good less than Easy`() {
        for (s in nonNewStates()) {
            val p = engine.preview(s, now)
            assertTrue("Again<Hard (${s.phase})", p[Rating.Again].dueAt.isBefore(p[Rating.Hard].dueAt))
            assertTrue("Hard<Good (${s.phase})", p[Rating.Hard].dueAt.isBefore(p[Rating.Good].dueAt))
            assertTrue("Good<Easy (${s.phase})", p[Rating.Good].dueAt.isBefore(p[Rating.Easy].dueAt))
        }
    }

    @Test
    fun `commit is byte-deterministic across repeated invocations`() {
        val s = matureReviewState()
        assertEquals(engine.commit(s, Rating.Good, now), engine.commit(s, Rating.Good, now))
    }

    @Test
    fun `preview and commit share the same code path`() {
        val s = matureReviewState()
        val p = engine.preview(s, now)
        for (r in Rating.entries) assertEquals(p[r], engine.commit(s, r, now))
    }

    @Test
    fun `commit and preview do not mutate the input state`() {
        val s = matureReviewState()
        val snap = s.copy()
        for (r in Rating.entries) engine.commit(s, r, now)
        engine.preview(s, now)
        assertEquals(snap, s)
    }

    @Test
    fun `Again in Review increments lapses and lowers stability`() {
        val s = matureReviewState()
        val after = engine.commit(s, Rating.Again, now)
        assertEquals(s.lapses + 1, after.lapses)
        assertTrue("Again must lower stability", after.stability < s.stability)
    }

    @Test
    fun `Again in Learning does not increment lapses`() {
        val s = learningState()
        assertEquals(0, engine.commit(s, Rating.Again, now).lapses)
    }

    @Test
    fun `Easy in Review yields strictly later dueAt than Good`() {
        val s = matureReviewState()
        val good = engine.commit(s, Rating.Good, now)
        val easy = engine.commit(s, Rating.Easy, now)
        assertTrue("Easy must be after Good", easy.dueAt.isAfter(good.dueAt))
    }

    @Test
    fun `every commit bumps reps by 1 and stamps schedulerVersion`() {
        for (s in nonNewStates()) for (r in Rating.entries) {
            val a = engine.commit(s, r, now)
            assertEquals("reps for $r on ${s.phase}", s.reps + 1, a.reps)
            assertEquals(SchedulerVersion.CURRENT, a.schedulerVersion)
        }
    }

    @Test
    fun `commit on a stale schedulerVersion throws IllegalArgumentException`() {
        val stale = matureReviewState().copy(schedulerVersion = SchedulerVersion.CURRENT + 99)
        try {
            engine.commit(stale, Rating.Good, now)
            assertFalse("expected IllegalArgumentException for stale version", true)
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }
}
