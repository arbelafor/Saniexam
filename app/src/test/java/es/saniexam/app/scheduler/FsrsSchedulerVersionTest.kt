package es.saniexam.app.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin tests for the [SchedulerVersion] constant. Spec "schedulerVersion
 * persisted" + "Version mismatch handled" both rely on a stable version
 * int that the engine writes into every `CardState` it produces.
 *
 * If you intentionally bump [SchedulerVersion.CURRENT], the migration
 * story (PR3+) is: a Room migration re-initialises the affected rows
 * rather than mixing v1 and v2 math on the same card.
 */
class FsrsSchedulerVersionTest {

    @Test
    fun `CURRENT is a positive stable int`() {
        assertTrue("CURRENT must be >= 1", SchedulerVersion.CURRENT >= 1)
        assertEquals(1, SchedulerVersion.CURRENT)
    }

    @Test
    fun `FsrsState_newCard stamps CURRENT`() {
        val s = FsrsState.newCard(java.time.Instant.parse("2025-01-01T00:00:00Z"))
        assertEquals(SchedulerVersion.CURRENT, s.schedulerVersion)
    }

    @Test
    fun `FsrsState_newCard starts in New phase with zeroed learning steps`() {
        val s = FsrsState.newCard(java.time.Instant.parse("2025-01-01T00:00:00Z"))
        assertEquals(CardPhase.New, s.phase)
        assertEquals(0, s.learningSteps)
        assertEquals(0, s.reps)
        assertEquals(0, s.lapses)
    }
}
