package com.healthplatform.sync.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.healthplatform.sync.service.ExerciseSignalResponse
import com.healthplatform.sync.service.GeneratedRoutineExerciseResponse
import com.healthplatform.sync.service.GeneratedRoutineProgressionResponse
import com.healthplatform.sync.service.GeneratedRoutineReadinessResponse
import com.healthplatform.sync.service.GeneratedRoutineResponse
import com.healthplatform.sync.service.LandmarkStatusResponse
import com.healthplatform.sync.service.ProgressionSummaryResponse
import com.healthplatform.sync.service.RoutineDecisionResponse
import com.healthplatform.sync.service.ServerApiClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GeneratedRoutineViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockClient: ServerApiClient
    private lateinit var viewModel: GeneratedRoutineViewModel

    private val sampleExercise = GeneratedRoutineExerciseResponse(
        id = "ex-001",
        ordering = 1,
        exerciseTemplateId = "EX001",
        exerciseName = "Bench Press",
        targetSets = 3,
        targetReps = 8,
        targetWeightKg = 82.5,
        targetRpe = 8.0,
        resolvedPrimary = "chest",
        resolvedSecondaries = listOf("front_delts", "triceps"),
        reasoning = "Last two sets hit target — increase weight.",
        flags = listOf("two_for_two_increase")
    )

    private val sampleRoutine = GeneratedRoutineResponse(
        id = "gen-001",
        title = "Push Day",
        status = "presented",
        reasoningSummary = "Chest below MAV — emphasizing pressing.",
        readiness = GeneratedRoutineReadinessResponse(74.0, "Take it easy"),
        progression = GeneratedRoutineProgressionResponse(62, true),
        warnings = listOf("triceps approaching MRV"),
        exercises = listOf(sampleExercise)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockClient = mockk()
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = GeneratedRoutineViewModel(app, clientProvider = { mockClient })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `generateRoutine success populates routine state`() = runTest {
        coEvery { mockClient.generateRoutine(any()) } returns Result.success(sampleRoutine)

        viewModel.state.test {
            awaitItem() // initial

            viewModel.generateRoutine("push", listOf("chest", "triceps"))
            testDispatcher.scheduler.advanceUntilIdle()

            val generating = awaitItem()
            assertTrue(generating.isGenerating)

            val loaded = awaitItem()
            assertFalse(loaded.isGenerating)
            assertNotNull(loaded.routine)
            assertEquals("Push Day", loaded.routine!!.title)
            assertEquals(1, loaded.routine!!.exercises.size)
            assertEquals("Bench Press", loaded.routine!!.exercises[0].exerciseName)
        }
    }

    @Test
    fun `generateRoutine failure sets error`() = runTest {
        coEvery { mockClient.generateRoutine(any()) } returns Result.failure(
            Exception("HTTP 404")
        )

        viewModel.state.test {
            awaitItem()

            viewModel.generateRoutine("push", listOf("chest"))
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // isGenerating
            val errorState = awaitItem()

            assertFalse(errorState.isGenerating)
            assertNotNull(errorState.error)
            assertNull(errorState.routine)
        }
    }

    @Test
    fun `loadRoutine success populates routine`() = runTest {
        coEvery { mockClient.getGeneratedRoutine("gen-001") } returns Result.success(sampleRoutine)

        viewModel.state.test {
            awaitItem()

            viewModel.loadRoutine("gen-001")
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // isGenerating
            val loaded = awaitItem()
            assertNotNull(loaded.routine)
            assertEquals("gen-001", loaded.routine!!.id)
        }
    }

    @Test
    fun `acceptRoutine updates decision state`() = runTest {
        coEvery { mockClient.generateRoutine(any()) } returns Result.success(sampleRoutine)
        coEvery { mockClient.decideGeneratedRoutine("gen-001", "accepted") } returns
            Result.success(RoutineDecisionResponse("gen-001", "accepted", "2026-03-22T10:00:00Z"))

        viewModel.generateRoutine("push", listOf("chest"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            val current = awaitItem()
            assertNotNull(current.routine)

            viewModel.acceptRoutine()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // isDeciding
            val decided = awaitItem()
            assertEquals("accepted", decided.decisionMade)
            assertEquals("accepted", decided.routine!!.status)
            assertFalse(decided.isDeciding)
        }
    }

    @Test
    fun `rejectRoutine updates decision state`() = runTest {
        coEvery { mockClient.generateRoutine(any()) } returns Result.success(sampleRoutine)
        coEvery { mockClient.decideGeneratedRoutine("gen-001", "rejected") } returns
            Result.success(RoutineDecisionResponse("gen-001", "rejected", "2026-03-22T10:00:00Z"))

        viewModel.generateRoutine("push", listOf("chest"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            awaitItem()

            viewModel.rejectRoutine()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // isDeciding
            val decided = awaitItem()
            assertEquals("rejected", decided.decisionMade)
        }
    }

    @Test
    fun `generateRoutine is debounced`() = runTest {
        coEvery { mockClient.generateRoutine(any()) } returns Result.success(sampleRoutine)

        viewModel.generateRoutine("push", listOf("chest"))
        viewModel.generateRoutine("push", listOf("chest")) // second call while generating
        testDispatcher.scheduler.advanceUntilIdle()

        // Only one call should have been made
        io.mockk.coVerify(exactly = 1) { mockClient.generateRoutine(any()) }
    }

    private val sampleProgressionSummary = ProgressionSummaryResponse(
        snapshotDate = "2026-03-22",
        periodDays = 7,
        trainingLoadScore = 62,
        historyFresh = true,
        volumeByMuscle = mapOf("chest" to 10),
        landmarkStatus = mapOf(
            "chest" to LandmarkStatusResponse(
                sets = 10, mevLow = 4, mavLow = 6, mavHigh = 16, mrvHigh = 24,
                status = "in_range", approachingMrv = false
            )
        ),
        exerciseSignals = listOf(
            ExerciseSignalResponse("EX001", "Bench Press", 80.0, 2, "increase_weight")
        )
    )

    @Test
    fun `generateOnEntry includes training load when progression data exists`() = runTest {
        coEvery { mockClient.getProgressionSummary(any()) } returns Result.success(sampleProgressionSummary)
        coEvery { mockClient.generateRoutine(any()) } returns Result.success(sampleRoutine)

        viewModel.state.test {
            awaitItem() // initial

            viewModel.generateOnEntry()
            testDispatcher.scheduler.advanceUntilIdle()

            val generating = awaitItem()
            assertTrue(generating.isGenerating)

            val loaded = awaitItem()
            assertFalse(loaded.isGenerating)
            assertNotNull(loaded.routine)
        }

        // Verify training load was included in the readiness payload
        io.mockk.coVerify {
            mockClient.generateRoutine(match { request ->
                request.readiness != null &&
                request.readiness!!.inputs != null &&
                request.readiness!!.inputs!!.any { it.id == "TRAINING_LOAD" && it.score == 62 }
            })
        }
    }

    @Test
    fun `generateOnEntry excludes training load when progression unavailable`() = runTest {
        coEvery { mockClient.getProgressionSummary(any()) } returns Result.failure(Exception("no data"))
        coEvery { mockClient.generateRoutine(any()) } returns Result.success(sampleRoutine)

        viewModel.generateOnEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify training load input was excluded (score null, status EXCLUDED)
        io.mockk.coVerify {
            mockClient.generateRoutine(match { request ->
                request.readiness != null &&
                request.readiness!!.inputs != null &&
                request.readiness!!.inputs!!.any { it.id == "TRAINING_LOAD" && it.score == null }
            })
        }
    }

    @Test
    fun `generateOnEntry no-ops if routine already loaded`() = runTest {
        coEvery { mockClient.getProgressionSummary(any()) } returns Result.failure(Exception("no data"))
        coEvery { mockClient.generateRoutine(any()) } returns Result.success(sampleRoutine)

        viewModel.generateRoutine("push", listOf("chest"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Now call generateOnEntry — should not trigger a second call
        viewModel.generateOnEntry()
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { mockClient.generateRoutine(any()) }
    }
}
