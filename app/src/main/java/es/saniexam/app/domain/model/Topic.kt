package es.saniexam.app.domain.model

/**
 * Logical topic inside a pack. Renames require a new `packVersion`.
 */
data class Topic(
    val id: String,
    val packId: String,
    val name: String,
)
