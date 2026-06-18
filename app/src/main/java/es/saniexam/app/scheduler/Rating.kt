package es.saniexam.app.scheduler

/**
 * Four-button review rating. Ordinal ordering encodes the FSRS contract
 * `Again < Hard < Good < Easy`: lower ordinal = shorter next interval.
 * The enum order is the contract; do not reorder.
 *
 * Matches the FSRS v6 spec "Four-Button Rating Contract" and `ts-fsrs@5.4.1`
 * `Rating.Again=1..Rating.Easy=4` (Manual is intentionally absent in
 * SaniExam — no manual scheduling).
 */
enum class Rating {
    Again,
    Hard,
    Good,
    Easy,
    ;

    /** Ordinal position in the spec's `<` chain; 1-based for FSRS math parity. */
    fun fsrsGrade(): Int = ordinal + 1
}
