package es.saniexam.app.domain.model

import java.time.Instant

/**
 * Singleton user-preference row. Used by Review to resume the in-flight
 * session (queue position, reveal state, draft selection) and by the
 * backup codec to round-trip user state across devices.
 *
 * Deferred to PR5 (Review): the data-layer table, the writer (Review
 * session resume logic) and the concrete repository implementation are
 * PR5 work. PR4 ships this domain shape plus a stub repository that
 * returns [Default] so Settings + Backup can be wired now.
 *
 * PR-A: [activeCategory] is the `professional-categories` field that
 * tells the read path which category to filter by. The default is
 * `"TCAE"` (the only registered category in the MVP). The MVP UX does
 * NOT expose a category picker — [activeCategory] is the seeded
 * default and the user has no in-app control over it. Future
 * categories (Enfermería, etc.) reuse the same column; the MVP's
 * single-active-category filter is wired through repositories, DAOs,
 * and use cases even though the UI does not surface a picker.
 */
data class UserSettings(
    val lastRevealedCardId: String?,
    val lastSessionQueuePosition: Int,
    val lastSessionAt: Instant?,
    val activeCategory: String,
) {
    companion object {
        /** The MVP's only registered professional category. */
        const val TCAE: String = "TCAE"

        /** Returns the empty/default singleton the app uses before PR5. */
        val Default: UserSettings = UserSettings(
            lastRevealedCardId = null,
            lastSessionQueuePosition = 0,
            lastSessionAt = null,
            activeCategory = TCAE,
        )
    }
}
