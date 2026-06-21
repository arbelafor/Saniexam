package es.saniexam.app.domain.usecase

import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.model.ExamQuestion
import es.saniexam.app.domain.model.ExamResultRow
import es.saniexam.app.domain.model.ExamResults
import es.saniexam.app.domain.model.ExamSession
import es.saniexam.app.domain.model.Question
import es.saniexam.app.domain.repository.DatasetRepository
import es.saniexam.app.domain.repository.OptionRepository
import es.saniexam.app.domain.repository.QuestionRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Spec `exam-simulation`. Owns the read-and-score loop of a mock exam.
 *
 * **No FSRS Perturbation**: this use case's constructor signature
 * does **not** include [es.saniexam.app.domain.repository.CardStateRepository]
 * or [es.saniexam.app.domain.repository.ReviewLogRepository]. The
 * compiler therefore prevents the use case from calling
 * `cardStateRepository.upsert` or `reviewLogRepository.append`. The
 * spec contract is enforced structurally; the unit tests assert the
 * same contract dynamically (zero `CardState` / `ReviewLog` writes
 * after a complete exam).
 *
 * The use case is stateless across calls: [start] is the only `suspend`
 * operation (it reads the pack from disk); [score] and
 * [withSelection] are pure functions of the in-memory [ExamSession].
 *
 * PR-A: the active category resolves through
 * [UserSettings.activeCategory] (spec `professional-categories`
 * "Active Category in User Settings"). The MVP ships with `TCAE` as
 * the only registered category; future categories (Enfermería,
 * Medicina) reuse the same use case.
 */
class RunExamSessionUseCase @Inject constructor(
    private val questionRepository: QuestionRepository,
    private val optionRepository: OptionRepository,
    private val datasetRepository: DatasetRepository,
    private val userSettingsRepository: UserSettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) {

    /**
     * Default seed for the deterministic shuffle. The spec calls for a
     * "deterministic 50-question subset"; a fixed seed is enough for
     * the dev pack and for the unit tests. The ViewModel can override
     * this when the user re-attempts.
     */
    var seed: Long = DEFAULT_SEED
        set(value) {
            require(value != 0L) { "seed must be non-zero (Random(0) is deterministic in the wrong way)" }
            field = value
        }

    /**
     * Read the active pack from disk through the user's
     * `active_category`, take a deterministic subset of up to
     * [MAX_QUESTIONS] questions, and return an in-memory
     * [ExamSession]. Throws [NoActivePackException] when no pack
     * is applied; [EmptyPackException] when the active pack has no
     * questions.
     */
    suspend fun start(
        now: Instant = Instant.now(clock),
        questionLimit: Int = MAX_QUESTIONS,
        duration: Duration = DEFAULT_DURATION,
    ): ExamSession = withContext(io) {
        val activeCategory = userSettingsRepository.get().activeCategory
        val packs = datasetRepository.observeActivePacksByCategory(activeCategory).first()
        val pack = packs.firstOrNull() ?: throw NoActivePackException()
        val allQuestions = questionRepository.observeAllByCategory(activeCategory).first()
        if (allQuestions.isEmpty()) throw EmptyPackException(pack.id)
        val sampled = sample(allQuestions, questionLimit, seed)
        val questions = sampled.map { q ->
            ExamQuestion(question = q, options = optionRepository.forQuestion(q.id))
        }
        ExamSession(
            packId = pack.id,
            packVersion = pack.version,
            totalQuestions = questions.size,
            durationSeconds = duration.seconds,
            startedAt = now,
            questions = questions,
            answers = emptyMap(),
        )
    }

    /**
     * Score [session] at [now]. Pure function (no I/O). Each question
     * is scored `correct` only when the user's selected set equals
     * the question's full correct option set (spec
     * `exam-simulation` "Multi-correct schema" + "Single-correct
     * scoring"); otherwise the question is `incorrect` — including
     * the blank case where the user did not select any option (spec
     * "Timer expires" → "unanswered = incorrect").
     *
     * The [ExamResultRow.isBlank] flag is preserved on every per-question
     * row so the review list can colour blank vs incorrect differently;
     * the totals (returned on [ExamResults]) follow the strict spec
     * wording: blank questions count toward `incorrect` and never toward
     * `correct` (so the percentage is `correct / total`, and unanswered
     * depress it).
     */
    fun score(session: ExamSession, now: Instant): ExamResults {
        var correct = 0
        var incorrect = 0
        val rows = ArrayList<ExamResultRow>(session.questions.size)
        for (eq in session.questions) {
            val selected = session.answers[eq.question.id].orEmpty()
            val correctIds = eq.correctOptionIds
            val isBlank = selected.isEmpty()
            val isCorrect = !isBlank && selected == correctIds
            if (isCorrect) {
                correct++
            } else {
                // Spec "Timer expires → unanswered = incorrect" + the
                // strict reading of "Multi-correct schema" → wrong
                // selection = incorrect. Blank and wrong both count
                // toward the `incorrect` total. The per-row `isBlank`
                // flag is preserved so the review list can colour
                // blank vs incorrect differently.
                incorrect++
            }
            val correctTexts = eq.options.asSequence()
                .filter { it.id in correctIds }
                .sortedBy { it.ordinal }
                .map { it.text }
                .toList()
            val selectedTexts = eq.options.asSequence()
                .filter { it.id in selected }
                .sortedBy { it.ordinal }
                .map { it.text }
                .toList()
            rows += ExamResultRow(
                questionId = eq.question.id,
                prompt = eq.question.prompt,
                correctOptionIds = correctIds,
                correctOptionTexts = correctTexts,
                selectedOptionIds = selected,
                selectedOptionTexts = selectedTexts,
                isCorrect = isCorrect,
                isBlank = isBlank,
            )
        }
        val total = session.totalQuestions
        val percentage = if (total == 0) 0.0 else (correct.toDouble() * 100.0 / total)
        val elapsedSeconds = Duration.between(session.startedAt, now).seconds.coerceAtLeast(0L)
        return ExamResults(
            total = total,
            correct = correct,
            // Spec compliance (PR7 W1): blank questions are scored
            // `incorrect` in the totals. The per-row `isBlank` flag
            // still distinguishes them in the per-question review list.
            incorrect = incorrect,
            blank = rows.count { it.isBlank },
            percentage = percentage,
            elapsedSeconds = elapsedSeconds,
            perQuestion = rows,
        )
    }

    /**
     * Replace the answer for [questionId] with [selectedOptionIds].
     * Pure: returns a new [ExamSession] with the updated map. A blank
     * selection (`selectedOptionIds.isEmpty()`) is the "unanswered"
     * state; the scoring path treats it as incorrect.
     */
    fun withSelection(
        session: ExamSession,
        questionId: String,
        selectedOptionIds: Set<String>,
    ): ExamSession = session.copy(answers = session.answers + (questionId to selectedOptionIds))

    /**
     * Deterministic Fisher–Yates shuffle with a [seed]ed [java.util.Random].
     * The order is stable across runs for the same input list + seed.
     * If [limit] is `>= list.size`, returns the shuffled list. If
     * [limit] is smaller, returns the first [limit] entries of the
     * shuffled list (the spec calls for "a deterministic 50-question
     * subset" of the active pack).
     */
    private fun sample(questions: List<Question>, limit: Int, seed: Long): List<Question> {
        val mutable = questions.toMutableList()
        val random = java.util.Random(seed)
        for (i in mutable.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = mutable[i]
            mutable[i] = mutable[j]
            mutable[j] = tmp
        }
        val take = limit.coerceAtMost(mutable.size).coerceAtLeast(0)
        return mutable.subList(0, take).toList()
    }

    companion object {
        /** Default shuffle seed. Overridable via [seed]. */
        const val DEFAULT_SEED: Long = 0x5A41_1A4D_7E55_FF01L

        /** Hard cap on the number of questions in a single exam session. */
        const val MAX_QUESTIONS: Int = 50

        /** Default exam duration (50 minutes for a 50-question exam). */
        val DEFAULT_DURATION: Duration = Duration.ofMinutes(50)
    }
}

/** Thrown by [RunExamSessionUseCase.start] when no pack is applied. */
class NoActivePackException :
    RuntimeException("RunExamSessionUseCase: no active pack applied.")

/** Thrown by [RunExamSessionUseCase.start] when the active pack has no questions. */
class EmptyPackException(packId: String) :
    RuntimeException("RunExamSessionUseCase: pack '$packId' has no questions.")
