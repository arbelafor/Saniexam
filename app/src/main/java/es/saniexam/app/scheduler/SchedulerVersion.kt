package es.saniexam.app.scheduler

/**
 * Version of the scheduler math that produced a `CardState`. The engine
 * writes [CURRENT] into every `CardState` it produces so a future re-tuned
 * engine can detect and refuse to mix v1 and v2 math on the same card
 * (see spec `fsrs-scheduler` / "Version mismatch handled").
 *
 * `CURRENT = 1` corresponds to **FSRS v6** (FSRS-6 / Free Spaced Repetition
 * Scheduler, version 6), the same algorithm `ts-fsrs@5.4.1` `default_w`
 * ships and the `open-spaced-repetition/fsrs-kotlin` README documents.
 * 21-element `w` vector; `enable_fuzz=false`, `enable_short_term=true`.
 *
 * Bump this constant when the algorithm or its parameter vector changes
 * non-additively so existing `CardState` rows can be migrated explicitly
 * instead of being silently re-evaluated with new math.
 */
object SchedulerVersion {
    const val CURRENT: Int = 1
}
