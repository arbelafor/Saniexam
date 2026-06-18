package es.saniexam.app.scheduler

import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec `fsrs-scheduler` / "Golden Tests" fuzz half: 1000 random
 * `(state, rating, now)` triples, three trials each (3000 commits).
 * Must never throw, never produce NaN/Infinity, and preserve the
 * `Again < Hard < Good < Easy` ordering for non-New states. PR2 budget
 * keeps the loops inline; a follow-up PR may swap to jqwik if we need
 * shrinking or per-byte mutation coverage.
 */
class FsrsSchedulerFuzzTest {

    private val engine = FsrsEngine()
    private val baseNow: Instant = Instant.parse("2025-06-15T10:00:00Z")
    private val cases = 1000
    private val trials = 3

    @Test
    fun `1000 random triples times 3 trials never throw and preserve invariants`() {
        val rng = Random(0x5A_71_BA_D5L)
        for (i in 0 until cases) {
            for (t in 0 until trials) {
                val state = randomNonNewState(rng)
                val rating = Rating.entries.random(rng)
                val now = baseNow.plusSeconds(rng.nextLong(-86_400L, 86_400L * 365L))
                try {
                    val next = engine.commit(state, rating, now)
                    assertFinite("stability", next.stability)
                    assertFinite("difficulty", next.difficulty)
                    assertTrue("difficulty in [1,10] was=${next.difficulty}", next.difficulty in 1.0..10.0)
                    assertTrue("stability>=0 was=${next.stability}", next.stability >= 0.0)
                    assertTrue("dueAt>=now-1s", !next.dueAt.isBefore(now.minusSeconds(1)))
                    assertTrue("reps bumped", next.reps == state.reps + 1)
                    assertTrue("version stamped", next.schedulerVersion == SchedulerVersion.CURRENT)
                } catch (t2: Throwable) {
                    throw AssertionError(
                        "fuzz failed i=$i trial=$t state=$state rating=$rating now=$now: " +
                            "${t2::class.simpleName}: ${t2.message}",
                        t2,
                    )
                }
            }
        }
    }

    @Test
    fun `1000 random previews preserve the Again less than Hard less than Good less than Easy ordering`() {
        val rng = Random(0xC0_CA_BA_BEL)
        for (i in 0 until cases) {
            val state = randomNonNewState(rng)
            val now = baseNow.plusSeconds(rng.nextLong(-86_400L, 86_400L * 365L))
            val p = engine.preview(state, now)
            val a = p[Rating.Again]
            val h = p[Rating.Hard]
            val g = p[Rating.Good]
            val e = p[Rating.Easy]
            assertTrue("Again<Hard i=$i", a.dueAt.isBefore(h.dueAt))
            assertTrue("Hard<Good i=$i", h.dueAt.isBefore(g.dueAt))
            assertTrue("Good<Easy i=$i", g.dueAt.isBefore(e.dueAt))
        }
    }

    private fun randomNonNewState(rng: Random): FsrsState {
        val phase = when (rng.nextInt(3)) {
            0 -> CardPhase.Learning
            1 -> CardPhase.Review
            else -> CardPhase.Relearning
        }
        val reps = rng.nextInt(1, 30)
        val lapses = if (phase == CardPhase.Review) rng.nextInt(0, 5) else 0
        val stability = rng.nextDouble(0.1, 100.0)
        val difficulty = rng.nextDouble(1.0, 10.0)
        val last = baseNow.minusSeconds(rng.nextLong(0L, 86_400L * 60L))
        val elapsed = rng.nextInt(0, 60)
        val due = last.plusSeconds(elapsed.toLong() * 86_400L)
        return FsrsState(
            stability = stability, difficulty = difficulty,
            dueAt = due, lastReviewedAt = last,
            reps = reps, lapses = lapses, phase = phase,
            scheduledDays = rng.nextInt(0, 30), elapsedDays = elapsed,
            learningSteps = rng.nextInt(0, 3),
            schedulerVersion = SchedulerVersion.CURRENT,
        )
    }

    private fun assertFinite(label: String, v: Double) {
        assertFalse("$label NaN", v.isNaN())
        assertFalse("$label Inf", v.isInfinite())
    }
}
