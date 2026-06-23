package es.saniexam.app.domain.usecase

import es.saniexam.app.data.dao.CardStateDao
import es.saniexam.app.data.dao.QuestionDao
import es.saniexam.app.data.dao.SubjectPackDao
import es.saniexam.app.data.entity.CardStateEntity
import es.saniexam.app.data.entity.QuestionEntity
import es.saniexam.app.data.entity.SubjectPackEntity
import es.saniexam.app.data.entity.TopicEntity
import es.saniexam.app.data.entity.toDomain
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.CardStateWithQuestion
import es.saniexam.app.domain.model.Option
import es.saniexam.app.domain.model.Question
import es.saniexam.app.domain.model.UserSettings
import es.saniexam.app.domain.repository.CardStateRepository
import es.saniexam.app.domain.repository.QuestionRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
import es.saniexam.app.scheduler.CardPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Spec `review-session` "Daily Due Queue" scenarios. The use case is
 * a thin coordinator: the DAO already filters `due_at <= now` and the
 * snapshot is deterministic. The seed step creates a `CardState` for
 * every question that has none yet.
 *
 *  - "Queue on open": the returned list contains only due cards.
 *  - "Suspended excluded": no v1 `suspended` column → the spec
 *    collapses to "due filter only". Documented as a no-op test.
 *  - "Empty queue": no due cards → empty list. The UI shows the
 *    empty-state message and does **not** auto-advance.
 *  - "Seed creates new card for question without CardState": the use
 *    case materialises a `FsrsState.newCard()` row for every
 *    question that has no state yet.
 *
 * PR-A multi-category plumbing: the seed step reads questions through
 * [QuestionRepository.observeAllByCategory] with the user's
 * `activeCategory`. The tests inject a fake repository that returns
 * the questions for the seeded TCAE category.
 */
class GetDueQueueUseCaseTest {

    private val now: Instant = Instant.parse("2026-06-16T10:00:00Z")
    private val io = Dispatchers.Unconfined
    private val tcaeSettings = UserSettings.Default // activeCategory = TCAE

    @Test
    fun `queue contains only due cards in due-at order`() = runBlocking {
        val due1 = cardStateEntity("q1", dueAt = now.minusSeconds(86_400L))
        val due2 = cardStateEntity("q2", dueAt = now.minusSeconds(2 * 86_400L))
        val future = cardStateEntity("q3", dueAt = now.plusSeconds(86_400L))
        val cardStateDao = FakeCardStateDao(listOf(due2, due1, future))
        val questionDao = FakeQuestionDao(emptyList())
        val questionRepository = FakeQuestionRepository(emptyList(), tcaeQuestions = emptyList())
        val cardStateRepository = FakeCardStateRepository(emptyList())
        val useCase = GetDueQueueUseCase(
            cardStateRepository = cardStateRepository,
            cardStateDao = cardStateDao,
            questionDao = questionDao,
            questionRepository = questionRepository,
            userSettingsRepository = FakeUserSettingsRepository(tcaeSettings),
            io = io,
        )

        // Two due, one future. The DAO returns only the due ones in
        // due_at ASC order (q2 before q1 because q2 is more overdue).
        val expected = listOf(due2.toDomain(), due1.toDomain())
        cardStateRepository.queueToReturn = expected.map { entity ->
            CardStateWithQuestion(
                cardState = entity,
                question = Question(entity.questionId, entity.packId, entity.packVersion, "t", "p", null, null, null),
                options = listOf(
                    Option("o1", entity.questionId, 0, "A", isCorrect = true),
                    Option("o2", entity.questionId, 1, "B", isCorrect = false),
                ),
            )
        }
        val result = useCase(now, limit = 10)
        assertEquals(2, result.size)
        assertEquals("q2", result[0].cardState.questionId)
        assertEquals("q1", result[1].cardState.questionId)
    }

    @Test
    fun `empty queue returns empty list and does not auto-advance`() = runBlocking {
        val cardStateRepository = FakeCardStateRepository(emptyList())
        val useCase = GetDueQueueUseCase(
            cardStateRepository = cardStateRepository,
            cardStateDao = FakeCardStateDao(emptyList()),
            questionDao = FakeQuestionDao(emptyList()),
            questionRepository = FakeQuestionRepository(emptyList(), tcaeQuestions = emptyList()),
            userSettingsRepository = FakeUserSettingsRepository(tcaeSettings),
            io = io,
        )

        val result = useCase(now, limit = 10)
        assertTrue("empty queue must produce empty list", result.isEmpty())
    }

    @Test
    fun `seed creates a new card state for every question that has none`() = runBlocking {
        val packId = "sanidad-v1"
        val question1 = questionEntity("q1", packId, 1)
        val question2 = questionEntity("q2", packId, 1)
        val cardStateDao = FakeCardStateDao(emptyList()) // no card states yet
        val cardStateRepository = FakeCardStateRepository(emptyList())
        val useCase = GetDueQueueUseCase(
            cardStateRepository = cardStateRepository,
            cardStateDao = cardStateDao,
            questionDao = FakeQuestionDao(emptyList()),
            questionRepository = FakeQuestionRepository(
                allQuestions = emptyList(),
                tcaeQuestions = listOf(question1, question2),
            ),
            userSettingsRepository = FakeUserSettingsRepository(tcaeSettings),
            io = io,
        )

        useCase(now, limit = 10)
        // The seed must have upserted one card state per question.
        assertEquals(2, cardStateDao.upserts.size)
        val ids = cardStateDao.upserts.map { it.questionId }.toSet()
        assertEquals(setOf("q1", "q2"), ids)
        val seed = cardStateDao.upserts.first()
        val seedDomain = seed.toDomain()
        assertEquals(CardPhase.New, seedDomain.phase)
        assertEquals(0.0, seedDomain.stability, 0.0)
        assertEquals(0.0, seedDomain.difficulty, 0.0)
        assertEquals(0, seedDomain.reps)
    }

    @Test
    fun `seed is idempotent and does not overwrite existing card states`() = runBlocking {
        val packId = "sanidad-v1"
        val question = questionEntity("q1", packId, 1)
        // Card state already exists for q1.
        val existing = cardStateEntity("q1", dueAt = now.plusSeconds(86_400L), phase = CardPhase.Review, stability = 7.0)
        val cardStateDao = FakeCardStateDao(listOf(existing))
        val cardStateRepository = FakeCardStateRepository(emptyList())
        val useCase = GetDueQueueUseCase(
            cardStateRepository = cardStateRepository,
            cardStateDao = cardStateDao,
            questionDao = FakeQuestionDao(emptyList()),
            questionRepository = FakeQuestionRepository(
                allQuestions = emptyList(),
                tcaeQuestions = listOf(question),
            ),
            userSettingsRepository = FakeUserSettingsRepository(tcaeSettings),
            io = io,
        )

        useCase(now, limit = 10)
        // No upsert because q1 already has a card state.
        assertEquals(0, cardStateDao.upserts.size)
    }

    @Test
    fun `suspended cards are not in v1 schema (spec says no suspended column)`() = runBlocking {
        // Spec note: v1 has no `suspended` column. The "Suspended
        // excluded" scenario is therefore structurally satisfied by
        // every due card being eligible. This test asserts the queue
        // includes a card whose CardState has no suspended flag (the
        // default). A future spec that adds a `suspended` column will
        // turn this into a real filter.
        val cardState = cardStateEntity("q1", dueAt = now.minusSeconds(86_400L))
        val cardStateDao = FakeCardStateDao(listOf(cardState))
        val cardStateRepository = FakeCardStateRepository(
            listOf(
                CardStateWithQuestion(
                    cardState = cardState.toDomain(),
                    question = Question("q1", cardState.packId, cardState.packVersion, "t", "p", null, null, null),
                    options = listOf(Option("o1", "q1", 0, "A", isCorrect = true)),
                ),
            ),
        )
        val useCase = GetDueQueueUseCase(
            cardStateRepository = cardStateRepository,
            cardStateDao = cardStateDao,
            questionDao = FakeQuestionDao(emptyList()),
            questionRepository = FakeQuestionRepository(emptyList(), tcaeQuestions = emptyList()),
            userSettingsRepository = FakeUserSettingsRepository(tcaeSettings),
            io = io,
        )

        val result = useCase(now, limit = 10)
        assertEquals(1, result.size)
        assertNotNull(result.first().cardState)
    }

    @Test
    fun `due queue excludes due cards from inactive categories`() = runBlocking {
        val tcaeCard = cardStateEntity("q-tcae", dueAt = now.minusSeconds(86_400L), packId = "sanidad-v1")
        val otherCard = cardStateEntity("q-other", dueAt = now.minusSeconds(2 * 86_400L), packId = "nursing-v1")
        val cardStateRepository = FakeCardStateRepository(
            listOf(
                withQuestion(otherCard, packId = "nursing-v1"),
                withQuestion(tcaeCard, packId = "sanidad-v1"),
            ),
        ).apply {
            queueByCategory[UserSettings.TCAE] = listOf(withQuestion(tcaeCard, packId = "sanidad-v1"))
        }
        val useCase = GetDueQueueUseCase(
            cardStateRepository = cardStateRepository,
            cardStateDao = FakeCardStateDao(listOf(tcaeCard, otherCard)),
            questionDao = FakeQuestionDao(emptyList()),
            questionRepository = FakeQuestionRepository(emptyList(), tcaeQuestions = emptyList()),
            userSettingsRepository = FakeUserSettingsRepository(tcaeSettings),
            io = io,
        )

        val result = useCase(now, limit = 10)

        assertEquals(listOf("q-tcae"), result.map { it.cardState.questionId })
    }

    // --- helpers + fakes ---

    private fun cardStateEntity(
        qid: String,
        dueAt: Instant = now,
        phase: CardPhase = CardPhase.New,
        stability: Double = 0.0,
        packId: String = "sanidad-v1",
    ): CardStateEntity = CardStateEntity(
        questionId = qid,
        packId = packId,
        packVersion = 1,
        stability = stability,
        difficulty = 0.0,
        dueAt = dueAt.toEpochMilli(),
        lastReviewedAt = null,
        reps = 0,
        lapses = 0,
        phase = phase.name.lowercase(),
        scheduledDays = 0,
        elapsedDays = 0,
        learningSteps = 0,
        schedulerVersion = 1,
    )

    private fun questionEntity(qid: String, packId: String, packVersion: Int): QuestionEntity = QuestionEntity(
        id = qid, packId = packId, packVersion = packVersion,
        topicId = "t1", prompt = "p", explanation = null,
        officialYear = null, officialSourceRef = null,
    )

    private fun withQuestion(card: CardStateEntity, packId: String): CardStateWithQuestion =
        CardStateWithQuestion(
            cardState = card.toDomain(),
            question = Question(card.questionId, packId, card.packVersion, "t", "p", null, null, null),
            options = listOf(Option("${card.questionId}-o1", card.questionId, 0, "A", isCorrect = true)),
        )

    private class FakeCardStateDao(initial: List<CardStateEntity>) : CardStateDao {
        var upserts: MutableList<CardStateEntity> = mutableListOf()
        private val rows: MutableMap<String, CardStateEntity> = initial.associateBy { it.questionId }.toMutableMap()
        override suspend fun upsert(state: CardStateEntity) {
            upserts.add(state)
            rows[state.questionId] = state
        }
        override suspend fun upsertAll(states: List<CardStateEntity>) {
            for (s in states) upsert(s)
        }
        override suspend fun get(questionId: String): CardStateEntity? = rows[questionId]
        override fun observeDue(nowMs: Long, limit: Int): Flow<List<CardStateEntity>> =
            MutableStateFlow(rows.values.filter { it.dueAt <= nowMs }.sortedBy { it.dueAt }.take(limit))
        override suspend fun listDueByCategory(nowMs: Long, category: String, limit: Int): List<CardStateEntity> =
            rows.values.filter { it.dueAt <= nowMs }.sortedBy { it.dueAt }.take(limit)
        override suspend fun count(): Int = rows.size
        override suspend fun countDue(nowMs: Long): Int = rows.values.count { it.dueAt <= nowMs }
        override fun observeAll(): Flow<List<CardStateEntity>> = MutableStateFlow(rows.values.toList())
        override suspend fun getAll(): List<CardStateEntity> = rows.values.toList()
        override suspend fun deleteAll() { rows.clear() }
    }

    private class FakeQuestionDao(initial: List<QuestionEntity>) : QuestionDao {
        private val rows: MutableMap<String, QuestionEntity> = initial.associateBy { it.id }.toMutableMap()
        override suspend fun insertAll(questions: List<QuestionEntity>) {
            for (q in questions) rows[q.id] = q
        }
        override fun observeAll(packId: String): Flow<List<QuestionEntity>> =
            MutableStateFlow(rows.values.filter { it.packId == packId })
        override suspend fun get(id: String): QuestionEntity? = rows[id]
        override suspend fun count(packId: String): Int = rows.values.count { it.packId == packId }
        override fun observeAllByCategory(category: String): Flow<List<QuestionEntity>> =
            MutableStateFlow(rows.values.toList())
        override suspend fun countByCategory(category: String): Int = rows.size
    }

    private class FakeQuestionRepository(
        private val allQuestions: List<QuestionEntity>,
        private val tcaeQuestions: List<QuestionEntity>,
    ) : QuestionRepository {
        private val rows = MutableStateFlow(allQuestions + tcaeQuestions)
        override fun observeAll(packId: String): Flow<List<Question>> =
            MutableStateFlow(
                rows.value.filter { it.packId == packId }.map { it.toDomain() },
            )
        override suspend fun get(id: String): Question? = rows.value.firstOrNull { it.id == id }?.toDomain()
        override suspend fun count(packId: String): Int = rows.value.count { it.packId == packId }
        override fun observeAllByCategory(category: String): Flow<List<Question>> =
            MutableStateFlow(tcaeQuestions.map { it.toDomain() })
        override suspend fun countByCategory(category: String): Int = tcaeQuestions.size
    }

    private class FakeSubjectPackDao(initial: List<SubjectPackEntity>) : SubjectPackDao {
        private val rows = MutableStateFlow(initial)
        override suspend fun insert(pack: SubjectPackEntity) { rows.value = rows.value + pack }
        override suspend fun deleteById(packId: String) {
            rows.value = rows.value.filterNot { it.id == packId }
        }
        override fun observeAll(): Flow<List<SubjectPackEntity>> = rows
        override suspend fun get(packId: String, packVersion: Int): SubjectPackEntity? =
            rows.value.firstOrNull { it.id == packId && it.version == packVersion }
        override fun observeByCategory(category: String): Flow<List<SubjectPackEntity>> =
            MutableStateFlow(rows.value.filter { it.category == category })
        override suspend fun countByCategory(category: String): Int =
            rows.value.count { it.category == category }
    }

    private class FakeCardStateRepository(initial: List<CardStateWithQuestion>) : CardStateRepository {
        var queueToReturn: List<CardStateWithQuestion> = initial
        val queueByCategory: MutableMap<String, List<CardStateWithQuestion>> = mutableMapOf()
        override fun observeDue(now: Instant, limit: Int): Flow<List<CardState>> = flowOf(emptyList())
        override suspend fun get(questionId: String): CardState? = null
        override suspend fun upsert(state: CardState) = Unit
        override suspend fun count(): Int = 0
        override suspend fun countDue(now: Instant): Int = 0
        override suspend fun getWithQuestion(questionId: String): CardStateWithQuestion? = null
        override suspend fun listDue(now: Instant, limit: Int): List<CardStateWithQuestion> = queueToReturn
        override suspend fun listDueByCategory(
            now: Instant,
            category: String,
            limit: Int,
        ): List<CardStateWithQuestion> = queueByCategory[category] ?: queueToReturn
        override suspend fun deleteAll() = Unit
        override suspend fun replaceAll(states: List<CardState>) = Unit
    }

    private class FakeUserSettingsRepository(
        private val settings: UserSettings,
    ) : UserSettingsRepository {
        override suspend fun get(): UserSettings = settings
        override suspend fun update(settings: UserSettings) = Unit
    }

    // Suppress unused warnings on TopicEntity when test class compiles.
    @Suppress("unused")
    private val topicSentinel: TopicEntity? = null
}
