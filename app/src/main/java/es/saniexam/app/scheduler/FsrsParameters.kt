package es.saniexam.app.scheduler

/**
 * FSRS v6 parameters. `W` is the pinned 21-element `w` vector from the
 * official FSRS v6 default (FSRS-6 / Free Spaced Repetition Scheduler v6),
 * the same vector the `open-spaced-repetition/fsrs-kotlin` README ships as
 * the FSRS-6 default and that the current `ts-fsrs@5.4.1` `default_w`
 * exports:
 *
 * ```
 * 0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
 * 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
 * 1.8729, 0.5425, 0.0912, 0.0658, 0.1542
 * ```
 *
 * Index semantics (matching `ts-fsrs` v5.x `default_w`):
 *  - w[0..3]: initial stability per rating (Again/Hard/Good/Easy)
 *  - w[4]   : initial difficulty (Good) baseline
 *  - w[5]   : initial difficulty multiplier
 *  - w[6]   : next-difficulty multiplier
 *  - w[7]   : mean-reversion weight
 *  - w[8]   : recall stability exponent base
 *  - w[9]   : recall stability negative power
 *  - w[10]  : recall stability (1-R) exponent
 *  - w[11]  : forget stability multiplier
 *  - w[12]  : forget stability negative power
 *  - w[13]  : forget stability power
 *  - w[14]  : forget stability (1-R) exponent
 *  - w[15]  : Hard penalty on recall stability
 *  - w[16]  : Easy bonus on recall stability
 *  - w[17]  : short-term stability exponent (rating term)
 *  - w[18]  : short-term stability exponent offset
 *  - w[19]  : short-term last-stability negative power
 *  - w[20]  : decay (positive value; engine uses `decay = -w[20]`)
 *
 * `const` fields get inlined and let the test suite assert the pin
 * against the byte-for-byte reference. Do not edit without bumping
 * [SchedulerVersion.CURRENT] and regenerating the golden file
 * (see `tools/generate-golden.ts`).
 *
 * Reference:
 *  - ts-fsrs `default_w` (v5.4.1): 21-element FSRS-6 default
 *  - open-spaced-repetition/fsrs-kotlin README: same 21-element default
 */
object FsrsParameters {
    const val REQUEST_RETENTION: Double = 0.9
    const val MAXIMUM_INTERVAL: Int = 36500
    const val ENABLE_SHORT_TERM: Boolean = true

    /** FSRS-6 stability clamp lower bound (matches `ts-fsrs` `S_MIN`). */
    const val S_MIN: Double = 0.001
    /** FSRS-6 stability clamp upper bound (matches `ts-fsrs` `S_MAX`). */
    const val S_MAX: Double = 36500.0
    /** FSRS-6 initial-stability clamp upper bound (matches `INIT_S_MAX`). */
    const val INIT_S_MAX: Double = 100.0

    /**
     * 21-element FSRS-6 `w` vector (pinned, do not edit without bumping
     * [SchedulerVersion.CURRENT] and regenerating the golden file).
     */
    val W: DoubleArray = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956,
        6.4133, 0.8334, 3.0194, 0.001,
        1.8722, 0.1666, 0.796, 1.4835,
        0.0614, 0.2629, 1.6483, 0.6014,
        1.8729, 0.5425, 0.0912, 0.0658,
        0.1542,
    )

    /** `decay = -w[20]`. ts-fsrs FSRS-6 default `-0.1542`. */
    const val DECAY: Double = -0.1542
    /**
     * `factor = 0.9^(1/decay) - 1`. Pre-computed at init for stability; for
     * decay `-0.1542`, `factor ≈ 0.9803464944134799` (matches
     * `ts-fsrs` `computeDecayFactor`).
     */
    val FACTOR: Double = kotlin.math.exp(kotlin.math.ln(0.9) / DECAY) - 1.0
}
