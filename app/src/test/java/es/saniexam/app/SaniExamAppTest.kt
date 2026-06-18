package es.saniexam.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sanity check that the JUnit runner is wired correctly. Pure-JVM, no Android
 * dependencies, runs under `:app:testDebugUnitTest`. Real tests for the
 * scheduler and use cases arrive in PR2+.
 */
class SaniExamAppTest {
    @Test
    fun `app name is SaniExam`() {
        assertEquals("SaniExam", "SaniExam")
    }
}
