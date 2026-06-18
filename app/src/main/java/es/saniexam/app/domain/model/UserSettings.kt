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
 */
data class UserSettings(
    val lastRevealedCardId: String?,
    val lastSessionQueuePosition: Int,
    val lastSessionAt: Instant?,
) {
    companion object {
        /** Returns the empty/default singleton the app uses before PR5. */
        val Default: UserSettings = UserSettings(
            lastRevealedCardId = null,
            lastSessionQueuePosition = 0,
            lastSessionAt = null,
        )
    }
}
