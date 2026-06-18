package es.saniexam.app.domain.usecase

import es.saniexam.app.data.dao.CardStateDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.dao.SubjectPackDao
import es.saniexam.app.data.entity.toEntity
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.CardStateWithQuestion
import es.saniexam.app.domain.repository.CardStateRepository
import es.saniexam.app.domain.repository.QuestionRepository
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
 * with no existing row get one). The single-pack MVP resolves the
 * active pack as the only `SubjectPackEntity` row.
 */
class GetDueQueueUseCase @Inject constructor(
    private val cardStateRepository: CardStateRepository,
    private val cardStateDao: CardStateDao,
    private val questionDao: QuestionDao,
    private val questionRepository: QuestionRepository,
    private val subjectPackDao: SubjectPackDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(now: Instant, limit: Int): List<CardStateWithQuestion> =
        withContext(io) {
            seedMissingCardStates(now)
            cardStateRepository.listDue(now, limit)
        }

    private suspend fun seedMissingCardStates(now: Instant) {
        val pack = subjectPackDao.observeAll().first().firstOrNull() ?: return
        val questions = questionRepository.observeAll(pack.id).first()
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
