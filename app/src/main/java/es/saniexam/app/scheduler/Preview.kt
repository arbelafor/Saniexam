package es.saniexam.app.scheduler

import java.time.Instant

/**
 * Immutable `Rating -> FsrsState` map from [FsrsEngine.preview]. The
 * Review UI renders one button per rating; this is the candidate state
 * for that button. Preview and commit share the same code path, so
 * `preview(state, now)[g] == commit(state, g, now)` is structural.
 */
data class FsrsPreview(
    val byRating: Map<Rating, FsrsState>,
) {
    operator fun get(rating: Rating): FsrsState = byRating.getValue(rating)

    /** Spec "Rating ordering invariant": `Again < Hard < Good < Easy`. */
    fun satisfiesOrdering(): Boolean {
        val a = byRating[Rating.Again] ?: return false
        val h = byRating[Rating.Hard] ?: return false
        val g = byRating[Rating.Good] ?: return false
        val e = byRating[Rating.Easy] ?: return false
        return a.dueAt.isBefore(h.dueAt) &&
            h.dueAt.isBefore(g.dueAt) &&
            g.dueAt.isBefore(e.dueAt)
    }
}

/** Spec `fsrs-scheduler` "Reschedule Preview" — typed tuple. */
data class Preview(
    val now: Instant,
    val again: FsrsState,
    val hard: FsrsState,
    val good: FsrsState,
    val easy: FsrsState,
)
