package es.saniexam.app.domain.model

/**
 * Pack-level metadata. The attribution fields are REQUIRED by the
 * `dataset-import` "Official-Source Metadata and Provenance" scenario;
 * the importer rejects the pack if any are blank. The release pipeline
 * fails closed when `license` is `unknown`, empty, or null.
 *
 * PR-A: [category] is the new `professional-categories` field that
 * makes the data model multi-category-aware from v1. TCAE is the
 * MVP's only registered category; future categories (e.g. Enfermería)
 * reuse the same column without a Room migration. The release gate
 * also fails closed when [category] is blank — see
 * [es.saniexam.app.build.validateManifest].
 */
data class SubjectPack(
    val id: String,
    val version: Int,
    val sourceAttribution: String,
    val publishedAt: String, // ISO-8601
    val license: String,
    val licenseNotes: String,
    val category: String,
)
