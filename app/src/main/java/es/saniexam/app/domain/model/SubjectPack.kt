package es.saniexam.app.domain.model

/**
 * Pack-level metadata. The four attribution fields are REQUIRED by the
 * `dataset-import` "Official-Source Metadata and Provenance" scenario;
 * the importer rejects the pack if any are blank. The release pipeline
 * fails closed when `license` is `unknown`, empty, or null.
 */
data class SubjectPack(
    val id: String,
    val version: Int,
    val sourceAttribution: String,
    val publishedAt: String, // ISO-8601
    val license: String,
    val licenseNotes: String,
)
