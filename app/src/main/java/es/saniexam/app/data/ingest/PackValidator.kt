package es.saniexam.app.data.ingest

import es.saniexam.app.data.entity.OptionEntity
import es.saniexam.app.data.entity.QuestionEntity
import es.saniexam.app.data.entity.TopicEntity
import es.saniexam.app.domain.model.SubjectPack

internal data class ValidatedPack(
    val topicEntities: List<TopicEntity>,
    val questionEntities: List<QuestionEntity>,
    val optionEntities: List<OptionEntity>,
)

/**
 * Pure-Kotlin validator. No I/O, no Android, no Room. Spec scenarios:
 *  - "Valid pack" (every row well-formed, one `isCorrect=true` per Q)
 *  - "Question with zero or multiple correct options" (rejected)
 *  - "Orphan topic reference" (rejected)
 *  - "Duplicate question id" (rejected)
 *  - "Question with missing required fields" (rejected)
 *  - PR-A: "Question missing provenance" (rejected with
 *    [DatasetImportException.Reason.ProvenanceMissing]) — every
 *    question must carry a non-blank `officialSourceRef` (spec
 *    `dataset-import` "Official-Source Metadata and Provenance"
 *    and spec `licensed-content-packs` "Per-Question Provenance").
 *    A blank or null `officialSourceRef` is the release-time
 *    failure path: the validator rejects the entire pack and the
 *    release pipeline license gate fails closed.
 */
internal object PackValidator {

    data class PackTopicView(val id: String, val name: String)
    data class PackQuestionView(
        val id: String,
        val topicId: String,
        val prompt: String,
        val explanation: String?,
        val officialYear: Int?,
        val officialSourceRef: String?,
        val options: List<PackOptionView>,
    )
    data class PackOptionView(
        val id: String,
        val ordinal: Int,
        val text: String,
        val isCorrect: Boolean,
    )

    fun validate(
        pack: SubjectPack,
        topics: List<PackTopicView>,
        questions: List<PackQuestionView>,
    ): ValidatedPack {
        // PR-A: pack-level `category` MUST be present and non-blank.
        // The release gate already refuses a manifest with a missing
        // category; the ingest path is the second line of defence so
        // a manifest that slipped through (or a hand-edited asset
        // bundle) cannot persist rows without provenance.
        if (pack.category.isBlank()) {
            throw DatasetImportException(
                DatasetImportException.Reason.MissingCategory,
            )
        }

        val topicIds = topics.map { it.id }.toSet()
        if (questions.any { it.topicId !in topicIds }) {
            val orphan = questions.first { it.topicId !in topicIds }
            throw DatasetImportException(
                DatasetImportException.Reason.OrphanTopicReference, orphan.id,
            )
        }

        val seenQuestionIds = mutableSetOf<String>()
        val questionEntities = mutableListOf<QuestionEntity>()
        val optionEntities = mutableListOf<OptionEntity>()

        questions.forEach { q ->
            if (q.id.isBlank() || q.prompt.isBlank() || q.options.isEmpty()) {
                throw DatasetImportException(
                    DatasetImportException.Reason.QuestionMissingFields, q.id,
                )
            }
            // PR-A: provenance is mandatory at the per-question level.
            // The release gate refuses a manifest without a pack-level
            // `category`; the validator refuses a pack where any
            // question lacks a non-blank `officialSourceRef`. Together
            // they form a closed loop: a pack with no provenance
            // cannot ship and cannot be ingested.
            if (q.officialSourceRef.isNullOrBlank()) {
                throw DatasetImportException(
                    DatasetImportException.Reason.ProvenanceMissing, q.id,
                )
            }
            if (!seenQuestionIds.add(q.id)) {
                throw DatasetImportException(
                    DatasetImportException.Reason.DuplicateQuestionId, q.id,
                )
            }
            val correct = q.options.count { it.isCorrect }
            if (correct != 1) {
                throw DatasetImportException(
                    DatasetImportException.Reason.ZeroOrMultipleCorrectOptions, q.id,
                )
            }
            questionEntities += QuestionEntity(
                id = q.id, packId = pack.id, packVersion = pack.version,
                topicId = q.topicId, prompt = q.prompt,
                explanation = q.explanation,
                officialYear = q.officialYear,
                officialSourceRef = q.officialSourceRef,
            )
            q.options.forEach { o ->
                optionEntities += OptionEntity(
                    id = o.id, questionId = q.id, ordinal = o.ordinal,
                    text = o.text, isCorrect = o.isCorrect,
                )
            }
        }

        return ValidatedPack(
            topicEntities = topics.map { t ->
                TopicEntity(id = t.id, packId = pack.id, packVersion = pack.version, name = t.name)
            },
            questionEntities = questionEntities,
            optionEntities = optionEntities,
        )
    }
}
