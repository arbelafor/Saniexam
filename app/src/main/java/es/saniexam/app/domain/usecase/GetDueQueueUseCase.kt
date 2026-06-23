package es.saniexam.app.domain.usecase

import es.saniexam.app.data.dao.CardStateDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.entity.toEntity
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.CardStateWithQuestion
import es.saniexam.app.domain.repository.CardStateRepository
import es.saniexam.app.domain.repository.QuestionRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
import es.saniexam.app.scheduler.FsrsState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * Returns the due queue for Review mode. Spec `review-session`
 * "Daily Due Queue":
 *  - `CardState.dueAt <= now() AND suspended = false` (no `suspended`
 *    column in v1 → effectively just `dueAt <= now`).
 *  - Most-overdue first (DAO already orders `due_at ASC`).
 *  - Capped to [limit] cards.
 *  - Empty list when no card is due → the UI shows
 *    "no hay repasos pendientes" and does **not** auto-advance.
 *
 * PR5 lazy-seed: a question that has no `CardState` row yet is
 * initialised with `FsrsState.newCard()` (the engine's "I have never
 * seen this card" sentinel) so the bundled pack's questions are
 * reviewable on first launch. The seed is idempotent (only questions
 * with no existing row get one).
 *
 * PR-A multi-category plumbing: the active pack is resolved through
 * [UserSettings.activeCategory] (spec `professional-categories`
 * "Active Category in User Settings" scenario "Reading uses the
 * active category"). The MVP ships with `TCAE` as the only
 * registered category; future categories (Enfermería, Medicina) are
 * a value change, not a structural change.
 */
class GetDueQueueUseCase @Inject constructor(
    private val cardStateRepository: CardStateRepository,
    private val cardStateDao: CardStateDao,
    private val questionDao: QuestionDao,
    private val questionRepository: QuestionRepository,
    private val userSettingsRepository: UserSettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(now: Instant, limit: Int): List<CardStateWithQuestion> =
        withContext(io) {
            val activeCategory = userSettingsRepository.get().activeCategory
            seedMissingCardStates(now, activeCategory)
            cardStateRepository.listDueByCategory(now, activeCategory, limit)
        }

    private suspend fun seedMissingCardStates(now: Instant, activeCategory: String) {
        // PR-A: the active category flows from user settings. The
        // default is `TCAE`; future categories (Enfermería, etc.)
        // are a value change, not a structural change.
        val questions = questionRepository.observeAllByCategory(activeCategory).first()
        for (q in questions) {
            if (cardStateDao.get(q.id) != null) continue
            val newState = FsrsState.newCard(now)
            cardStateDao.upsert(
                CardState(
                    questionId = q.id,
                    packId = q.packId,
                    packVersion = q.packVersion,
                    stability = newState.stability,
                    difficulty = newState.difficulty,
                    dueAt = newState.dueAt,
                    lastReviewedAt = newState.lastReviewedAt,
                    reps = newState.reps,
                    lapses = newState.lapses,
                    phase = newState.phase,
                    scheduledDays = newState.scheduledDays,
                    elapsedDays = newState.elapsedDays,
                    learningSteps = newState.learningSteps,
                    schedulerVersion = newState.schedulerVersion,
                ).toEntity(),
            )
        }
    }
}
