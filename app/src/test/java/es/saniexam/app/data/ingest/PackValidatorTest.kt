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
 *  - PR-A: "Question missing provenance" (rejected with
 *    [DatasetImportException.Reason.ProvenanceMissing] — every
 *    question must carry a non-blank `officialSourceRef`)
 *  - PR-A: "Pack missing category" (rejected with
 *    [DatasetImportException.Reason.MissingCategory] — the pack
 *    must carry a non-blank `category` field)
 */
class PackValidatorTest {

    private val pack = SubjectPack(
        id = "sanidad-v1", version = 1,
        sourceAttribution = "test", publishedAt = "2026-06-22",
        license = "cleared-of-rights", licenseNotes = "n/a",
        category = "TCAE",
    )

    @Test
    fun `valid pack produces question, option, and topic entities`() {
        val out = PackValidator.validate(
            pack = pack,
            topics = listOf(PackTopicView("t1", "Topic 1")),
            questions = listOf(
                question("q1", "t1", correctOrdinal = 1, sourceRef = "BOE-A-2024-1-preg17"),
                question("q2", "t1", correctOrdinal = 0, sourceRef = "BOE-A-2024-1-preg18"),
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
                listOf(questionWithCorrectCount("q1", "t1", correctCount = 0, sourceRef = "BOE-A-2024-1-preg17")),
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
                listOf(questionWithCorrectCount("q1", "t1", correctCount = 2, sourceRef = "BOE-A-2024-1-preg17")),
            )
        }
        assertEquals(DatasetImportException.Reason.ZeroOrMultipleCorrectOptions, ex.reason)
    }

    @Test
    fun `orphan topic reference is rejected`() {
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                pack, listOf(PackTopicView("t1", "Topic 1")),
                listOf(question("q1", topicId = "t-missing", correctOrdinal = 0, sourceRef = "BOE-A-2024-1-preg17")),
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
                    question("q1", "t1", correctOrdinal = 0, sourceRef = "BOE-A-2024-1-preg17"),
                    question("q1", "t1", correctOrdinal = 1, sourceRef = "BOE-A-2024-1-preg18"),
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
                    explanation = null, officialYear = null, officialSourceRef = "BOE-A-2024-1-preg17",
                    options = listOf(PackOptionView("q1-a", 0, "a", true)),
                )),
            )
        }
        assertEquals(DatasetImportException.Reason.QuestionMissingFields, ex.reason)
    }

    /**
     * PR-A: a question with a blank or null `officialSourceRef` is
     * rejected with [DatasetImportException.Reason.ProvenanceMissing].
     * This is the per-Q half of the closed-loop provenance gate:
     * the release-pipeline gate refuses a manifest without a
     * pack-level `category`; the validator refuses a pack where
     * any question lacks a non-blank `officialSourceRef`. Together
     * they cover both the pack-level and the per-Q layer.
     */
    @Test
    fun `question with null officialSourceRef is rejected with ProvenanceMissing`() {
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                pack, listOf(PackTopicView("t1", "Topic 1")),
                listOf(PackQuestionView(
                    id = "q1", topicId = "t1", prompt = "Prompt",
                    explanation = null, officialYear = 2024, officialSourceRef = null,
                    options = listOf(
                        PackOptionView("q1-a", 0, "A", true),
                        PackOptionView("q1-b", 1, "B", false),
                        PackOptionView("q1-c", 2, "C", false),
                    ),
                )),
            )
        }
        assertEquals(DatasetImportException.Reason.ProvenanceMissing, ex.reason)
        assertEquals("q1", ex.questionId)
    }

    @Test
    fun `question with blank officialSourceRef is rejected with ProvenanceMissing`() {
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                pack, listOf(PackTopicView("t1", "Topic 1")),
                listOf(PackQuestionView(
                    id = "q1", topicId = "t1", prompt = "Prompt",
                    explanation = null, officialYear = 2024, officialSourceRef = "   ",
                    options = listOf(
                        PackOptionView("q1-a", 0, "A", true),
                        PackOptionView("q1-b", 1, "B", false),
                        PackOptionView("q1-c", 2, "C", false),
                    ),
                )),
            )
        }
        assertEquals(DatasetImportException.Reason.ProvenanceMissing, ex.reason)
        assertEquals("q1", ex.questionId)
    }

    /**
     * PR-A: a pack with a blank `category` is rejected with
     * [DatasetImportException.Reason.MissingCategory] before any
     * question is inspected. The release-pipeline license gate
     * refuses the same manifest at the manifest layer; the
     * validator's check is the second line of defence.
     */
    @Test
    fun `pack with blank category is rejected with MissingCategory`() {
        val packNoCategory = pack.copy(category = "  ")
        val ex = assertThrows(DatasetImportException::class.java) {
            PackValidator.validate(
                packNoCategory, listOf(PackTopicView("t1", "Topic 1")),
                listOf(question("q1", "t1", correctOrdinal = 0, sourceRef = "BOE-A-2024-1-preg17")),
            )
        }
        assertEquals(DatasetImportException.Reason.MissingCategory, ex.reason)
    }

    // --- Helpers ---

    private fun question(
        id: String,
        topicId: String,
        correctOrdinal: Int,
        sourceRef: String,
        officialYear: Int? = 2024,
    ) = PackQuestionView(
        id = id, topicId = topicId, prompt = "Prompt for $id",
        explanation = null, officialYear = officialYear, officialSourceRef = sourceRef,
        options = listOf(
            PackOptionView("$id-a", 0, "Option A", isCorrect = 0 == correctOrdinal),
            PackOptionView("$id-b", 1, "Option B", isCorrect = 1 == correctOrdinal),
            PackOptionView("$id-c", 2, "Option C", isCorrect = 2 == correctOrdinal),
        ),
    )

    private fun questionWithCorrectCount(
        id: String,
        topicId: String,
        correctCount: Int,
        sourceRef: String,
    ): PackQuestionView {
        val opts = (0 until 3).map { ord ->
            PackOptionView("$id-$ord", ord, "Option $ord", isCorrect = ord < correctCount)
        }
        return PackQuestionView(
            id = id, topicId = topicId, prompt = "Prompt",
            explanation = null, officialYear = 2024, officialSourceRef = sourceRef,
            options = opts,
        )
    }
}
