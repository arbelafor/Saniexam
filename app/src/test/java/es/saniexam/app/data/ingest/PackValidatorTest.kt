package es.saniexam.app.data.ingest

import es.saniexam.app.data.ingest.PackValidator.PackOptionView
import es.saniexam.app.data.ingest.PackValidator.PackQuestionView
import es.saniexam.app.data.ingest.PackValidator.PackTopicView
import es.saniexam.app.domain.model.SubjectPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure-JVM tests for the pack schema validator. Covers the spec scenarios
 * from `dataset-import/spec.md`:
 *  - "Valid pack" (every row persisted, no exception)
 *  - "Question with zero or multiple correct options" (rejected)
 *  - "Orphan topic reference" (rejected)
 *  - "Duplicate question id" (rejected)
 *  - Missing fields (rejected)
 */
class PackValidatorTest {

    private val pack = SubjectPack(
        id = "sanidad-dev-placeholder", version = 1,
        sourceAttribution = "Dev", publishedAt = "2026-06-16",
        license = "dev-placeholder", licenseNotes = "n/a",
    )

    @Test
    fun `valid pack produces question, option, and topic entities`() {
        val out = PackValidator.validate(
            pack = pack,
            topics = listOf(PackTopicView("t1", "Topic 1")),
            questions = listOf(
                question("q1", "t1", correctOrdinal = 1),
                question("q2", "t1", correctOrdinal = 0),
            ),
        )
        assertEquals(1, out.topicEntities.size)
        assertEquals(2, out.questionEntities.size)
        assertEquals(6, out.optionEntities.size) // 3 options per question
    }

    @Test
    fun `question with zero correct options is rejected`() {
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                pack, listOf(PackTopicView("t1", "Topic 1")),
                listOf(questionWithCorrectCount("q1", "t1", correctCount = 0)),
            )
        }
        assertEquals(DatasetImportException.Reason.ZeroOrMultipleCorrectOptions, ex.reason)
        assertEquals("q1", ex.questionId)
    }

    @Test
    fun `question with multiple correct options is rejected`() {
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                pack, listOf(PackTopicView("t1", "Topic 1")),
                listOf(questionWithCorrectCount("q1", "t1", correctCount = 2)),
            )
        }
        assertEquals(DatasetImportException.Reason.ZeroOrMultipleCorrectOptions, ex.reason)
    }

    @Test
    fun `orphan topic reference is rejected`() {
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                pack, listOf(PackTopicView("t1", "Topic 1")),
                listOf(question("q1", topicId = "t-missing", correctOrdinal = 0)),
            )
        }
        assertEquals(DatasetImportException.Reason.OrphanTopicReference, ex.reason)
        assertEquals("q1", ex.questionId)
    }

    @Test
    fun `duplicate question id is rejected`() {
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                pack, listOf(PackTopicView("t1", "Topic 1")),
                listOf(
                    question("q1", "t1", correctOrdinal = 0),
                    question("q1", "t1", correctOrdinal = 1),
                ),
            )
        }
        assertEquals(DatasetImportException.Reason.DuplicateQuestionId, ex.reason)
    }

    @Test
    fun `question with missing required fields is rejected`() {
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                pack, listOf(PackTopicView("t1", "Topic 1")),
                listOf(PackQuestionView(
                    id = "q1", topicId = "t1", prompt = "  ",
                    explanation = null, officialYear = null, officialSourceRef = null,
                    options = listOf(PackOptionView("q1-a", 0, "a", true)),
                )),
            )
        }
        assertEquals(DatasetImportException.Reason.QuestionMissingFields, ex.reason)
    }

    // --- Helpers ---

    private fun question(id: String, topicId: String, correctOrdinal: Int) = PackQuestionView(
        id = id, topicId = topicId, prompt = "Prompt for $id",
        explanation = null, officialYear = null, officialSourceRef = null,
        options = listOf(
            PackOptionView("$id-a", 0, "Option A", isCorrect = 0 == correctOrdinal),
            PackOptionView("$id-b", 1, "Option B", isCorrect = 1 == correctOrdinal),
            PackOptionView("$id-c", 2, "Option C", isCorrect = 2 == correctOrdinal),
        ),
    )

    private fun questionWithCorrectCount(id: String, topicId: String, correctCount: Int): PackQuestionView {
        val opts = (0 until 3).map { ord ->
            PackOptionView("$id-$ord", ord, "Option $ord", isCorrect = ord < correctCount)
        }
        return PackQuestionView(
            id = id, topicId = topicId, prompt = "Prompt",
            explanation = null, officialYear = null, officialSourceRef = null,
            options = opts,
        )
    }
}
