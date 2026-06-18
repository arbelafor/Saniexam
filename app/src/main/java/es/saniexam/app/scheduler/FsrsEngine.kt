package es.saniexam.app.scheduler

import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Pure-Kotlin FSRS v6 engine. Math follows the official FSRS-6 spec as
 * implemented in `open-spaced-repetition/ts-fsrs@5.4.1`
 * (`algorithm.ts`, `default.ts`).
 *
 * Reference: FSRS v6 (Free Spaced Repetition Scheduler, version 6).
 *  - 21-element `w` vector (pinned in [FsrsParameters.W]).
 *  - `enable_fuzz=false`, `enable_short_term=true`.
 *  - Forgetting curve: `R(t,S) = (1 + FACTOR * t / S)^DECAY` (the `/9`
 *    divisor that appears in the FSRS-6 JSDoc comment is **not** what the
 *    current `ts-fsrs` code computes; we follow the actual code).
 *  - Short-term stability: gated by `enable_short_term && t==0`, uses
 *    `S * sinc` where
 *    `sinc = pow(S, -w[19]) * exp(w[17] * (G-3 + w[18]))`,
 *    with `maskedSinc = (G >= Hard) ? max(sinc, 1.0) : sinc`.
 *  - Stability clamp `[S_MIN=0.001, S_MAX=36500]`.
 *  - Interval: `I(S, t) = clamp(round(S * interval_modifier), 1, max_ivl)`
 *    with `interval_modifier = (0.9^(1/decay) - 1) / FACTOR`.
 *  - (Re)learning steps: hardcoded `learning_steps=['1m','10m']`,
 *    `relearning_steps=['10m']` (matches `ts-fsrs` defaults).
 *
 * Pin rationale:
 *  - `ts-fsrs@5.4.1` `default_w` exports the same 21-element vector
 *    the `open-spaced-repetition/fsrs-kotlin` README ships as the FSRS-6
 *    default. Both sources agree byte-for-byte.
 *  - Math was re-derived from the v6 spec formulas; no code was vendored
 *    from `fsrs-kotlin` (which targets Android). The Kotlin port is used
 *    as a *shape* reference, not as a code source.
 *
 * Spec: `openspec/changes/saniexam/specs/fsrs-scheduler/spec.md`. PR2 owns
 * the engine; PR3+ persist `CardState.schedulerVersion` and route Review
 * writes through [commit].
 */
class FsrsEngine {
    // The engine is pure-Kotlin (no `android.*` / `androidx.*` /
    // `dagger.*` / `javax.inject.*` imports) per the
    // `fsrs-scheduler` spec "No I/O, No Android Dependencies". Hilt
    // provides a single instance via `AppModule.provideFsrsEngine()`;
    // the engine has no Hilt annotations so the purity test
    // (`FsrsSchedulerPurityTest`) can scan the scheduler package
    // for forbidden imports without false positives.

    // -- (Re)learning step sequences (ts-fsrs defaults) -------------------

    /** Total number of learning steps; matches `learning_steps.length` in ts-fsrs. */
    private val learningStepsCount: Int = 2
    /** `learning_steps[0]` in minutes — Again / first-step base. */
    private val learningStep1Minutes: Int = 1
    /** `learning_steps[1]` in minutes — used by Good to advance one step. */
    private val learningStep2Minutes: Int = 10
    /** `relearning_steps[0]` in minutes — Again in Review (Relearning). */
    private val relearningStepMinutes: Int = 10

    // -- Public API ---------------------------------------------------------

    /**
     * Compute the next state for each of the four ratings without mutating
     * [state]. Spec "Preview is read-only and matches commit" — preview and
     * commit share this code path.
     */
    fun preview(state: FsrsState, now: Instant): FsrsPreview =
        FsrsPreview(computeAllRatings(state, now))

    /** Commit one [rating] at [now]. Pure: [state] is not mutated. */
    fun commit(state: FsrsState, rating: Rating, now: Instant): FsrsState =
        computeAllRatings(state, now).getValue(rating)

    // -- FSM dispatch --------------------------------------------------------

    private fun computeAllRatings(state: FsrsState, now: Instant): Map<Rating, FsrsState> {
        require(state.schedulerVersion == SchedulerVersion.CURRENT) {
            "FsrsState.schedulerVersion=${state.schedulerVersion} but engine is at " +
                "version=${SchedulerVersion.CURRENT}. Migrate the row before committing."
        }
        return when (state.phase) {
            CardPhase.New -> newState(state, now)
            CardPhase.Learning, CardPhase.Relearning -> learningState(state, now)
            CardPhase.Review -> reviewState(state, now)
        }
    }

    // --- New card -----------------------------------------------------------
    //
    // v6 schedule for a New card mirrors ts-fsrs `BasicLearningStepsStrategy`:
    //   cur_step = state.learningSteps (always 0 for cold New)
    //   Again -> 1m, Learning, next_step=0
    //   Hard  -> 6m (rounded (1+10)/2), Learning, next_step=0
    //   Good  -> 10m, Learning, next_step=1
    //   Easy  -> Review, scheduled `nextInterval(initStability(Easy), 0)` days

    private fun newState(state: FsrsState, now: Instant): Map<Rating, FsrsState> =
        Rating.entries.associateWith { g ->
            // New card: d=0, s=0. [newMemory] short-circuits to init_stability
            // and init_difficulty; the forgetting curve is not used. We pass
            // r=1.0 to satisfy the (unused) parameter contract.
            // `isReviewCall = false` so lapses never increment for New (matches
            // ts-fsrs `BasicScheduler.newState`).
            val next = newMemory(state, g, r = 1.0, elapsed = 0, now = now, isReviewCall = false)
            applyLearningSteps(state, next, g, toState = CardPhase.Learning, now = now)
        }

    // --- Learning / Relearning --------------------------------------------

    private fun learningState(state: FsrsState, now: Instant): Map<Rating, FsrsState> =
        Rating.entries.associateWith { g ->
            val elapsed = calendarDaysSince(state.lastReviewedAt, now)
            val r = forgettingCurve(elapsed, state.stability)
            // `isReviewCall = false` so lapses never increment for (Re)Learning
            // (matches ts-fsrs `BasicScheduler.learningState`).
            val next = newMemory(state, g, r, elapsed, now, isReviewCall = false)
            applyLearningSteps(state, next, g, toState = state.phase, now = now)
        }

    // --- Review (mature card) ---------------------------------------------
    //
    // For a Review card, ts-fsrs computes Hard/Good/Easy with the recall
    // stability formula (using `R = forgetting_curve(t, S)`), then orders
    // their intervals so Hard < Good < Easy. Again uses the forget
    // stability formula with a `next_s_min = s / exp(w[17]*w[18])` ceiling,
    // then applies the Relearning step (10 minutes) and increments lapses.
    //
    // The `BasicScheduler.reviewState` also orders the four intervals so
    // Again <= Hard < Good < Easy; we apply the same ordering.

    private fun reviewState(state: FsrsState, now: Instant): Map<Rating, FsrsState> {
        val elapsed = calendarDaysSince(state.lastReviewedAt, now)
        val r = forgettingCurve(elapsed, state.stability)

        // Per-grade stability + difficulty (mimics `next_ds` per grade).
        // `isReviewCall = true` so Again increments `lapses` (matches
        // ts-fsrs `BasicScheduler.reviewState`).
        val again = newMemory(state, Rating.Again, r, elapsed, now, isReviewCall = true)
            .copy(phase = CardPhase.Review) // overwritten by [applyRelearningStep]
        val hard = newMemory(state, Rating.Hard, r, elapsed, now, isReviewCall = true)
            .copy(phase = CardPhase.Review)
        val good = newMemory(state, Rating.Good, r, elapsed, now, isReviewCall = true)
            .copy(phase = CardPhase.Review)
        val easy = newMemory(state, Rating.Easy, r, elapsed, now, isReviewCall = true)
            .copy(phase = CardPhase.Review)

        // Build full FsrsState candidates with their due/scheduledDays.
        val againFull = applyRelearningStep(again, now)
        val (hardIvl, goodIvl, easyIvl) = orderIntervals(hard, good, easy, elapsed, now)
        val hardFull = hard.copy(scheduledDays = hardIvl, dueAt = scheduleDays(now, hardIvl))
        val goodFull = good.copy(scheduledDays = goodIvl, dueAt = scheduleDays(now, goodIvl))
        val easyFull = easy.copy(scheduledDays = easyIvl, dueAt = scheduleDays(now, easyIvl))

        return mapOf(
            Rating.Again to againFull,
            Rating.Hard to hardFull,
            Rating.Good to goodFull,
            Rating.Easy to easyFull,
        )
    }

    /**
     * Compute the new stability+difficulty for a grade using the v6
     * `next_state` rules: short-term path when `t==0 && enable_short_term`,
     * forget path for Again, recall path otherwise.
     *
     * @param isReviewCall true when called from [reviewState] (lapses
     *   increment for Again); false when called from [newState] or
     *   [learningState] (lapses NEVER increment, matching ts-fsrs
     *   `BasicScheduler.learningState` and `newState`).
     */
    private fun newMemory(
        state: FsrsState,
        g: Rating,
        r: Double,
        elapsed: Int,
        now: Instant,
        isReviewCall: Boolean = false,
    ): FsrsState {
        val newStability: Double
        val newPhase: CardPhase
        val incrementLapses: Boolean
        val newLearningSteps: Int
        if (state.difficulty == 0.0 && state.stability == 0.0) {
            // New card path: ts-fsrs `next_state` returns init_stability/init_difficulty
            // when the memory state is empty. Lapses and phase are set by the
            // caller ([applyLearningSteps]); do NOT pre-empt them here.
            newStability = initStability(g)
            newPhase = CardPhase.New
            newLearningSteps = 0
            incrementLapses = false
        } else if (elapsed == 0 && FsrsParameters.ENABLE_SHORT_TERM) {
            // Short-term path: applies to ALL grades when t==0.
            newStability = nextShortTermStability(state.stability, g)
            // Phase is set by the caller ([applyLearningSteps] for
            // (Re)Learning path, [applyRelearningStep] for Review path).
            // We pass through `New` as a neutral default; the caller
            // overwrites.
            newPhase = CardPhase.New
            newLearningSteps = 0
            // Lapses increment only when the call site is `reviewState`
            // (matching ts-fsrs `BasicScheduler.reviewState`).
            incrementLapses = isReviewCall && g == Rating.Again
        } else if (g == Rating.Again) {
            // Forget path: next_forget_stability, capped by s/exp(w[17]*w[18]).
            val sAfterFail = nextForgetStability(state.difficulty, state.stability, r)
            val sMin = state.stability / exp(FsrsParameters.W[17] * FsrsParameters.W[18])
            newStability = min(max(round8(sMin), FsrsParameters.S_MIN), sAfterFail)
            newPhase = CardPhase.New // caller overwrites
            newLearningSteps = 0
            // Lapses increment only in the Review path (ts-fsrs semantics).
            incrementLapses = isReviewCall
        } else {
            // Recall path: Hard/Good/Easy.
            newStability = nextRecallStability(state.difficulty, state.stability, r, g)
            newPhase = CardPhase.New // caller overwrites
            newLearningSteps = 0
            incrementLapses = false
        }
        val newDifficulty = if (state.difficulty == 0.0 && state.stability == 0.0) {
            initDifficulty(g)
        } else {
            nextDifficulty(state.difficulty, g)
        }
        return FsrsState(
            stability = newStability,
            difficulty = newDifficulty,
            dueAt = now,
            lastReviewedAt = now,
            reps = state.reps + 1,
            lapses = if (incrementLapses) state.lapses + 1 else state.lapses,
            phase = newPhase,
            scheduledDays = 0,
            elapsedDays = elapsed,
            learningSteps = newLearningSteps,
            schedulerVersion = SchedulerVersion.CURRENT,
        )
    }

    // -- (Re)learning step application ------------------------------------
    //
    // Mirrors ts-fsrs `BasicLearningStepsStrategy` + `applyLearningSteps`
    // (see `ts-fsrs` `index.cjs`). Decides the new phase, due date, and
    // next-step index from the current state's (re)learning position.

    private fun applyLearningSteps(
        state: FsrsState,
        next: FsrsState,
        g: Rating,
        toState: CardPhase,
        now: Instant,
    ): FsrsState {
        val (scheduledMinutes, nextSteps) = learningStepInfo(state, g)
        return when {
            scheduledMinutes in 1..(60 * 24 - 1) -> {
                // Stay in (re)learning, schedule by minutes.
                next.copy(
                    phase = toState,
                    learningSteps = nextSteps,
                    scheduledDays = 0,
                    dueAt = scheduleMinutes(now, scheduledMinutes),
                    lastReviewedAt = now,
                )
            }
            scheduledMinutes >= 60 * 24 -> {
                // Step is at least one day — keep current state, schedule by minutes too.
                next.copy(
                    phase = toState,
                    learningSteps = nextSteps,
                    scheduledDays = scheduledMinutes / (60 * 24),
                    dueAt = scheduleMinutes(now, scheduledMinutes),
                    lastReviewedAt = now,
                )
            }
            else -> {
                // scheduledMinutes == 0: graduate to Review, use next_interval.
                val interval = nextInterval(next.stability, elapsedDays = 0)
                next.copy(
                    phase = CardPhase.Review,
                    learningSteps = 0,
                    scheduledDays = interval,
                    dueAt = scheduleDays(now, interval),
                    lastReviewedAt = now,
                )
            }
        }
    }

    private fun learningStepInfo(state: FsrsState, g: Rating): Pair<Int, Int> {
        val curStep = state.learningSteps.coerceAtLeast(0)
        if (state.phase == CardPhase.Review) {
            // Review state: only Again is dispatched here.
            return relearningStepMinutes to 0
        }
        if (state.phase == CardPhase.Relearning) {
            // Relearning_steps = ['10m'] (length 1). Matches ts-fsrs
            // `BasicLearningStepsStrategy`:
            //   Again -> 10m, next_step=0
            //   Hard  -> round(10 * 1.5) = 15m, next_step=0
            //   Good/Easy -> no step at index 1 -> undefined ->
            //               applyLearningSteps graduates to Review.
            return when (g) {
                Rating.Again -> relearningStepMinutes to 0
                Rating.Hard -> Math.round(relearningStepMinutes * 1.5).toInt() to 0
                else -> 0 to 0
            }
        }
        // Learning_steps = ['1m', '10m']
        return when {
            g == Rating.Again -> learningStep1Minutes to 0
            g == Rating.Hard -> {
                val hard = ((learningStep1Minutes + learningStep2Minutes) / 2.0)
                Math.round(hard).toInt() to curStep
            }
            g == Rating.Good && curStep + 1 < learningStepsCount -> {
                learningStep2Minutes to (curStep + 1)
            }
            else -> 0 to 0  // Good past the last step, or Easy — graduate.
        }
    }

    private fun applyRelearningStep(again: FsrsState, now: Instant): FsrsState =
        again.copy(
            phase = CardPhase.Relearning,
            learningSteps = 0,
            scheduledDays = 0,
            dueAt = scheduleMinutes(now, relearningStepMinutes),
            lastReviewedAt = now,
        )

    // -- Review interval ordering ------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    private fun orderIntervals(
        hard: FsrsState,
        good: FsrsState,
        easy: FsrsState,
        elapsed: Int,
        now: Instant,
    ): Triple<Int, Int, Int> {
        var hardIvl = nextInterval(hard.stability, elapsed)
        val goodIvlRaw = nextInterval(good.stability, elapsed)
        val easyIvlRaw = nextInterval(easy.stability, elapsed)
        hardIvl = min(hardIvl, goodIvlRaw)
        val goodIvl = max(goodIvlRaw, hardIvl + 1)
        val easyIvl = max(easyIvlRaw, goodIvl + 1)
        return Triple(hardIvl, goodIvl, easyIvl)
    }

    // -- `next_ds` wrapper -------------------------------------------------
    //
    // ts-fsrs `next_ds` is just a thin wrapper over `algorithm.next_state`
    // that copies stability/difficulty into a fresh card object. In the
    // Kotlin port we fold that into [newMemory], so this section is empty.

    // -- FSRS v6 math primitives -------------------------------------------

    private fun intervalModifier(): Double = round8(
        (FsrsParameters.REQUEST_RETENTION.pow(1.0 / FsrsParameters.DECAY) - 1.0) /
            FsrsParameters.FACTOR,
    )

    private fun initStability(g: Rating): Double =
        clampInitialStability(FsrsParameters.W[g.fsrsGrade() - 1])

    private fun initDifficulty(g: Rating): Double =
        constrainDifficulty(FsrsParameters.W[4] - exp((g.fsrsGrade() - 1) * FsrsParameters.W[5]) + 1.0)

    /**
     * Unclamped `init_difficulty` — used as the `init` argument to
     * `mean_reversion` in `next_difficulty`. Matches ts-fsrs
     * `init_difficulty(g)` which does NOT clamp; the clamp is applied
     * by the caller (`next_difficulty`/`mean_reversion`).
     */
    private fun initDifficultyRaw(g: Rating): Double =
        round8(FsrsParameters.W[4] - exp((g.fsrsGrade() - 1) * FsrsParameters.W[5]) + 1.0)

    /**
     * FSRS-6 forgetting curve: `R(t,S) = (1 + FACTOR * t / S)^DECAY`.
     * Matches the current `ts-fsrs` `forgetting_curve` implementation
     * byte-for-byte. (The `/9` divisor that appears in the FSRS-6 JSDoc
     * comment in `algorithm.ts` is **not** what the code computes; the
     * code uses the v5 shape. We follow the code.)
     */
    private fun forgettingCurve(elapsedDays: Int, stability: Double): Double {
        require(stability > 0.0) { "stability must be > 0 (got $stability)" }
        return round8((1.0 + FsrsParameters.FACTOR * elapsedDays / stability).pow(FsrsParameters.DECAY))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun nextInterval(stability: Double, elapsedDays: Int): Int {
        val raw = (stability * intervalModifier()).roundToLong()
        return raw.coerceIn(1L, FsrsParameters.MAXIMUM_INTERVAL.toLong()).toInt()
    }

    private fun linearDamping(deltaD: Double, oldD: Double): Double =
        round8(deltaD * (10.0 - oldD) / 9.0)

    private fun meanReversion(init: Double, current: Double): Double =
        round8(FsrsParameters.W[7] * init + (1.0 - FsrsParameters.W[7]) * current)

    private fun constrainDifficulty(d: Double): Double =
        min(max(round8(d), 1.0), 10.0)

    private fun nextDifficulty(d: Double, g: Rating): Double {
        val deltaD = -FsrsParameters.W[6] * (g.fsrsGrade() - 3)
        // ts-fsrs: `mean_reversion(this.init_difficulty(Rating.Easy), next_d)` —
        // the `init_difficulty` here is the RAW value (no clamp), matching
        // `init_difficulty(g)` in `algorithm.ts` (which returns `roundTo(d, 8)`
        // without clamping). The clamp is applied by the surrounding
        // `clamp(..., 1, 10)`.
        val reverted = meanReversion(initDifficultyRaw(Rating.Easy), d + linearDamping(deltaD, d))
        return constrainDifficulty(reverted)
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, g: Rating): Double {
        val hardPenalty = if (g == Rating.Hard) FsrsParameters.W[15] else 1.0
        val easyBonus = if (g == Rating.Easy) FsrsParameters.W[16] else 1.0
        val factor = exp(FsrsParameters.W[8]) *
            (11.0 - d) *
            s.pow(-FsrsParameters.W[9]) *
            (exp((1.0 - r) * FsrsParameters.W[10]) - 1.0) *
            hardPenalty *
            easyBonus
        return clampStability(round8(s * (1.0 + factor)))
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val result = FsrsParameters.W[11] *
            d.pow(-FsrsParameters.W[12]) *
            ((s + 1.0).pow(FsrsParameters.W[13]) - 1.0) *
            exp((1.0 - r) * FsrsParameters.W[14])
        return clampStability(round8(result))
    }

    /**
     * FSRS-6 short-term stability:
     *  `sinc = pow(S, -w[19]) * exp(w[17] * (G-3 + w[18]))`,
     *  `maskedSinc = (G >= Hard) ? max(sinc, 1.0) : sinc`,
     *  `S' = clamp(S * maskedSinc, S_MIN, S_MAX)`.
     */
    private fun nextShortTermStability(s: Double, g: Rating): Double {
        val sinc = s.pow(-FsrsParameters.W[19]) *
            exp(FsrsParameters.W[17] * (g.fsrsGrade() - 3 + FsrsParameters.W[18]))
        val masked = if (g.fsrsGrade() >= Rating.Hard.fsrsGrade()) max(sinc, 1.0) else sinc
        return clampStability(round8(s * masked))
    }

    private fun clampStability(s: Double): Double =
        min(max(s, FsrsParameters.S_MIN), FsrsParameters.S_MAX)

    private fun clampInitialStability(s: Double): Double =
        min(max(s, FsrsParameters.S_MIN), FsrsParameters.INIT_S_MAX)

    // -- Date scheduling -----------------------------------------------------

    /** Add [days] × 86_400_000 ms to [now]. Matches ts-fsrs `scheduler(t, true)`. */
    private fun scheduleDays(now: Instant, days: Int): Instant =
        if (days <= 0) now else now.plusMillis(days.toLong() * 86_400_000L)

    private fun scheduleMinutes(now: Instant, minutes: Int): Instant =
        if (minutes == 0) now else now.plusMillis(minutes.toLong() * 60_000L)

    /**
     * Recompute elapsed_days at commit time, matching ts-fsrs
     * `dateDiffInDays(last, cur)`: floor of (UTC midnight of `now` −
     * UTC midnight of `last`) / 86_400_000. Returns 0 if [last] is null
     * (never-reviewed) or in the future (clock skew).
     */
    private fun calendarDaysSince(last: Instant?, now: Instant): Int {
        if (last == null) return 0
        val lastMs = last.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
        val nowMs = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
        return ((nowMs - lastMs) / 86_400_000L).coerceAtLeast(0L).toInt()
    }

    /** Round to 8 decimal places (ts-fsrs `toFixed(8)` parity). */
    private fun round8(x: Double): Double = (x * 1e8).roundToLong() / 1e8
}
