package es.saniexam.app.presentation.exam

import android.app.Application
import es.saniexam.app.domain.model.Option
import es.saniexam.app.domain.model.Question
import es.saniexam.app.domain.model.SubjectPack
import es.saniexam.app.domain.repository.CardStateRepository
import es.saniexam.app.domain.repository.DatasetRepository
import es.saniexam.app.domain.repository.OptionRepository
import es.saniexam.app.domain.repository.QuestionRepository
import es.saniexam.app.domain.repository.ReviewLogRepository
import es.saniexam.app.domain.usecase.RunExamSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Spec `exam-simulation` ViewModel surface:
 *  - "Start an exam" → ExamUiState.Active.
 *  - "Timer expires" → auto-submit on remaining <= 0 → ExamUiState.Results.
 *  - "User submits early" → ExamUiState.Results.
 *  - "Binary Correct/Incorrect Scoring" → results.total/correct/incorrect.
 *  - "No FSRS Perturbation" → VM has no [CardStateRepository] /
 *    [ReviewLogRepository] field. The structural guard.
 *
 * The countdown is driven by a public [ExamViewModel.tick] method;
 * the test calls it with a controlled `now` to exercise the
 * auto-submit path without running a real `delay` loop. The
 * [ExamRoute] coroutine is the production driver; the test
 * replaces it with a synchronous method call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExamViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val now: Instant = Instant.parse("2026-06-16T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    // PR7 string-resource refactor: the ExamViewModel is now an
    // `AndroidViewModel` so it can read localised error strings via
    // `application.getString(...)`. The Robolectric runner provides a
    // real `Application` for the test class so the error-message
    // branches exercise the actual resource table.
    private val application: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start transitions to Active with a session of the deterministic set`() = runTest {
        val useCase = stubUseCase(questions = (1..5).map { singleCorrectQuestion("$it") })
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Active, got $state", state is ExamUiState.Active)
        val active = state as ExamUiState.Active
        assertEquals(5, active.session.totalQuestions)
        assertEquals(50L * 60L, active.remainingSeconds)
        assertEquals(0, active.currentIndex)
    }

    @Test
    fun `tick with the same now does not change state`() = runTest {
        val useCase = stubUseCase(questions = (1..5).map { singleCorrectQuestion("$it") })
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()

        val before = vm.uiState.value
        vm.tick(now)
        advanceUntilIdle()
        val after = vm.uiState.value
        assertEquals(before, after)
    }

    @Test
    fun `tick at duration boundary auto-submits and emits Results`() = runTest {
        val useCase = stubUseCase(
            questions = (1..5).map { singleCorrectQuestion("$it") },
            duration = Duration.ofMinutes(50),
        )
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()

        // Advance the clock past the duration.
        vm.tick(now.plusSeconds(50L * 60L))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Results after auto-submit, got $state", state is ExamUiState.Results)
        val results = (state as ExamUiState.Results).results
        assertEquals(5, results.total)
        // All questions were left blank → 0 correct, 5 incorrect, 5 blank.
        // Spec `exam-simulation` "Timer expires → unanswered = incorrect":
        // blank questions count toward `incorrect` in the totals. The
        // per-row `isBlank` flag is preserved so the review list can
        // colour blank vs incorrect differently.
        assertEquals(0, results.correct)
        assertEquals(5, results.incorrect)
        assertEquals(5, results.blank)
    }

    @Test
    fun `tick before duration does not auto-submit`() = runTest {
        val useCase = stubUseCase(
            questions = (1..5).map { singleCorrectQuestion("$it") },
            duration = Duration.ofMinutes(50),
        )
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()

        // 10 minutes in: still active.
        vm.tick(now.plusSeconds(10L * 60L))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue("expected Active after 10 minutes, got $state", state is ExamUiState.Active)
        val active = state as ExamUiState.Active
        assertEquals(40L * 60L, active.remainingSeconds)
    }

    @Test
    fun `selectSingle replaces the current selection`() = runTest {
        val useCase = stubUseCase(questions = (1..3).map { singleCorrectQuestion("$it") })
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()

        vm.selectSingle("1", "1-o1")
        advanceUntilIdle()
        vm.selectSingle("1", "1-o2")
        advanceUntilIdle()

        val active = vm.uiState.value as ExamUiState.Active
        assertEquals(setOf("1-o2"), active.session.answers["1"])
    }

    @Test
    fun `toggleOption adds and removes the option from the selection`() = runTest {
        val useCase = stubUseCase(questions = (1..3).map { singleCorrectQuestion("$it") })
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()

        vm.toggleOption("1", "1-o1")
        advanceUntilIdle()
        assertEquals(setOf("1-o1"), (vm.uiState.value as ExamUiState.Active).session.answers["1"])

        vm.toggleOption("1", "1-o2")
        advanceUntilIdle()
        assertEquals(setOf("1-o1", "1-o2"), (vm.uiState.value as ExamUiState.Active).session.answers["1"])

        vm.toggleOption("1", "1-o1")
        advanceUntilIdle()
        assertEquals(setOf("1-o2"), (vm.uiState.value as ExamUiState.Active).session.answers["1"])
    }

    @Test
    fun `submitEarly produces Results and SessionEnd event is emitted on acknowledge`() = runTest {
        val useCase = stubUseCase(questions = (1..3).map { singleCorrectQuestion("$it") })
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()

        vm.selectSingle("1", "1-o1")
        vm.selectSingle("2", "2-o1")
        // Leave 3 unanswered.
        advanceUntilIdle()
        vm.submitEarly()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Results, got $state", state is ExamUiState.Results)
        val results = (state as ExamUiState.Results).results
        assertEquals(3, results.total)
        assertEquals(2, results.correct)
        // Spec `exam-simulation` "Timer expires → unanswered = incorrect":
        // the unanswered question counts toward `incorrect` in the totals
        // (the per-row `isBlank` flag is still 1 for the review list).
        assertEquals(1, results.incorrect)
        assertEquals(1, results.blank)

        // `MutableSharedFlow` is configured with `replay = 0` per the
        // android-viewmodel skill (no re-trigger on rotation), so a
        // late subscriber does not see the buffered event. We launch
        // a collector in the test scope BEFORE emitting, then assert
        // the received value.
        val eventReceived = kotlinx.coroutines.CompletableDeferred<ExamUiEvent>()
        val collectorJob = launch {
            vm.uiEvents.collect { event ->
                eventReceived.complete(event)
                cancel()
            }
        }
        advanceUntilIdle() // let the collector start
        vm.acknowledgeResults()
        advanceUntilIdle()
        val event = eventReceived.await()
        assertEquals(ExamUiEvent.SessionEnd, event)
        collectorJob.cancel()
    }

    @Test
    fun `goTo clamps the index to the valid range`() = runTest {
        val useCase = stubUseCase(questions = (1..3).map { singleCorrectQuestion("$it") })
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()

        vm.goTo(99)
        advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as ExamUiState.Active).currentIndex)

        vm.goTo(-5)
        advanceUntilIdle()
        assertEquals(0, (vm.uiState.value as ExamUiState.Active).currentIndex)

        vm.goTo(1)
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as ExamUiState.Active).currentIndex)
    }

    @Test
    fun `NoActivePackException produces Error state`() = runTest {
        val useCase = stubUseCase(questions = emptyList(), throwNoActivePack = true)
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue("expected Error, got $state", state is ExamUiState.Error)
        assertNotNull((state as ExamUiState.Error).message)
    }

    @Test
    fun `EmptyPackException produces Error state`() = runTest {
        val useCase = stubUseCase(questions = emptyList(), throwEmptyPack = true)
        val vm = ExamViewModel(application, useCase, testDispatcher, clock)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue("expected Error, got $state", state is ExamUiState.Error)
        assertNotNull((state as ExamUiState.Error).message)
    }

    /**
     * **No FSRS Perturbation — structural guard.** The ViewModel's
     * constructor signature must NOT include [CardStateRepository] or
     * [ReviewLogRepository]. This is the static guarantee that the
     * exam code path can never mutate FSRS state. The test asserts
     * the class has zero fields of either type. (The companion test
     * `RunExamSessionUseCaseTest` "no-perturbation guard" asserts the
     * same for the use case.)
     */
    @Test
    fun `view model has no CardStateRepository or ReviewLogRepository field (no-perturbation guard)`() {
        val cardType = CardStateRepository::class.java
        val logType = ReviewLogRepository::class.java
        val fields = ExamViewModel::class.java.declaredFields
        for (field in fields) {
            assertFalse(
                "no-perturbation guard: VM must not hold a ${cardType.simpleName} field, " +
                    "but found ${field.name}: ${field.type.name}",
                cardType.isAssignableFrom(field.type),
            )
            assertFalse(
                "no-perturbation guard: VM must not hold a ${logType.simpleName} field, " +
                    "but found ${field.name}: ${field.type.name}",
                logType.isAssignableFrom(field.type),
            )
        }
    }

    // --- helpers + stubs ---

    private fun stubUseCase(
        questions: List<Question>,
        @Suppress("UNUSED_PARAMETER") duration: Duration = Duration.ofMinutes(50),
        throwNoActivePack: Boolean = false,
        throwEmptyPack: Boolean = false,
    ): RunExamSessionUseCase {
        // For EmptyPackException, the use case reads a non-empty active
        // pack and then finds no questions for it. We seed an empty
        // question list to trigger the empty-pack path.
        if (throwEmptyPack) {
            return RunExamSessionUseCase(
                questionRepository = FakeQuestionRepository(emptyList()),
                optionRepository = FakeOptionRepository(emptyList()),
                datasetRepository = FakeDatasetRepository(activePacks = listOf(pack("p1"))),
                userSettingsRepository = FakeUserSettingsRepository(),
                io = testDispatcher,
                clock = clock,
            )
        }
        return RunExamSessionUseCase(
            questionRepository = FakeQuestionRepository(questions),
            optionRepository = FakeOptionRepository(questions),
            datasetRepository = FakeDatasetRepository(
                activePacks = if (throwNoActivePack) emptyList() else listOf(pack("p1")),
            ),
            userSettingsRepository = FakeUserSettingsRepository(),
            io = testDispatcher,
            clock = clock,
        )
    }

    private fun pack(id: String): SubjectPack = SubjectPack(
        id = id,
        version = 1,
        sourceAttribution = "test",
        publishedAt = "2026-01-01",
        license = "test",
        licenseNotes = "test",
        category = es.saniexam.app.domain.model.UserSettings.TCAE,
    )

    private fun singleCorrectQuestion(id: String): Question = Question(
        id = id,
        packId = "p1",
        packVersion = 1,
        topicId = "t1",
        prompt = "Prompt for $id",
        explanation = null,
        officialYear = null,
        officialSourceRef = null,
    )

    private class FakeQuestionRepository(private val questions: List<Question>) : QuestionRepository {
        override fun observeAll(packId: String): Flow<List<Question>> =
            MutableStateFlow(questions.filter { it.packId == packId })
        override suspend fun get(id: String): Question? = questions.firstOrNull { it.id == id }
        override suspend fun count(packId: String): Int = questions.count { it.packId == packId }
        override fun observeAllByCategory(category: String): Flow<List<Question>> =
            MutableStateFlow(questions)
        override suspend fun countByCategory(category: String): Int = questions.size
    }

    private class FakeOptionRepository(private val questions: List<Question>) : OptionRepository {
        override suspend fun forQuestion(questionId: String): List<Option> {
            val q = questions.firstOrNull { it.id == questionId } ?: return emptyList()
            return listOf(
                Option("$questionId-o1", q.id, 0, "A", isCorrect = true),
                Option("$questionId-o2", q.id, 1, "B", isCorrect = false),
                Option("$questionId-o3", q.id, 2, "C", isCorrect = false),
                Option("$questionId-o4", q.id, 3, "D", isCorrect = false),
            )
        }
    }

    private class FakeDatasetRepository(
        private val activePacks: List<SubjectPack>,
    ) : DatasetRepository {
        override fun observeActivePacks(): Flow<List<SubjectPack>> = MutableStateFlow(activePacks)
        override fun observeAppliedVersions() =
            MutableStateFlow(emptyList<es.saniexam.app.domain.model.DatasetVersion>())
        override suspend fun isApplied(packId: String, packVersion: Int): Boolean = true
        override suspend fun recordVersion(version: es.saniexam.app.domain.model.DatasetVersion) = Unit
        override fun observeActivePacksByCategory(category: String): Flow<List<SubjectPack>> =
            MutableStateFlow(activePacks.filter { it.category == category })
        override suspend fun countActivePacksByCategory(category: String): Int =
            activePacks.count { it.category == category }
    }

    private class FakeUserSettingsRepository(
        initial: es.saniexam.app.domain.model.UserSettings =
            es.saniexam.app.domain.model.UserSettings.Default,
    ) : es.saniexam.app.domain.repository.UserSettingsRepository {
        private var state: es.saniexam.app.domain.model.UserSettings = initial
        override suspend fun get(): es.saniexam.app.domain.model.UserSettings = state
        override suspend fun update(settings: es.saniexam.app.domain.model.UserSettings) { state = settings }
    }
}

