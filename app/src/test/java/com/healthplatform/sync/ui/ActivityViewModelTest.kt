package com.healthplatform.sync.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.healthplatform.sync.service.HevySyncResult
import com.healthplatform.sync.service.ServerApiClient
import com.healthplatform.sync.service.WorkoutResponse
import com.healthplatform.sync.service.WorkoutStatsSummaryResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActivityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockClient: ServerApiClient
    private lateinit var viewModel: ActivityViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockClient = mockk()
        // init { loadAll() } calls both loadWorkouts and loadStats on construction.
        coEvery { mockClient.getWorkouts(any(), any()) } returns Result.success(emptyList())
        coEvery { mockClient.getWorkoutStatsSummary(any()) } returns Result.success(
            WorkoutStatsSummaryResponse(total_workouts = 0, avg_duration = null, total_volume_kg = null, avg_sets_per_workout = null)
        )
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = ActivityViewModel(app, clientProvider = { mockClient })
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // loadWorkouts
    // -------------------------------------------------------------------------

    @Test
    fun `loadWorkouts success maps response to WorkoutSession list`() = runTest {
        val raw = listOf(
            WorkoutResponse(
                id = "w-001",
                hevy_id = "hevy-001",
                started_at = "2026-03-01T10:00:00Z",
                ended_at = "2026-03-01T11:00:00Z",
                duration_minutes = 60,
                title = "Push Day",
                total_volume_kg = 2000.0,
                total_sets = 20,
                total_reps = 150
            )
        )
        coEvery { mockClient.getWorkouts(any(), any()) } returns Result.success(raw)

        viewModel.state.test {
            awaitItem() // initial settled state

            viewModel.loadWorkouts()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // isLoading = true
            val loaded = awaitItem()

            assertEquals(1, loaded.workouts.size)
            assertEquals("Push Day", loaded.workouts[0].title)
            assertEquals(60, loaded.workouts[0].durationMinutes)
            assertEquals(2000.0, loaded.workouts[0].totalVolumeKg)
            assertFalse(loaded.isLoading)
            assertNull(loaded.error)
        }
    }

    @Test
    fun `loadWorkouts failure sets friendly error message`() = runTest {
        coEvery { mockClient.getWorkouts(any(), any()) } returns
            Result.failure(java.net.UnknownHostException())

        viewModel.state.test {
            awaitItem()

            viewModel.loadWorkouts()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // isLoading = true
            val errorState = awaitItem()

            assertNotNull(errorState.error)
            assertTrue(errorState.error!!.contains("network", ignoreCase = true))
            assertFalse(errorState.isLoading)
        }
    }

    // -------------------------------------------------------------------------
    // triggerHevySync
    // -------------------------------------------------------------------------

    @Test
    fun `triggerHevySync success clears syncError and reloads workouts`() = runTest {
        val syncResult = HevySyncResult(success = true, synced = 3, skipped = 0, total_fetched = 3, sync_id = "sync-001")
        coEvery { mockClient.triggerHevySync() } returns Result.success(syncResult)
        coEvery { mockClient.getWorkouts(any(), any()) } returns Result.success(emptyList())

        viewModel.state.test {
            awaitItem()

            viewModel.triggerHevySync()
            testDispatcher.scheduler.advanceUntilIdle()

            cancelAndIgnoreRemainingEvents()
        }

        // After sync succeeds, loadWorkouts should have been called again (once at init, once after sync)
        coVerify(atLeast = 2) { mockClient.getWorkouts(any(), any()) }
        assertNull(viewModel.state.value.syncError)
        assertFalse(viewModel.state.value.isSyncing)
    }

    @Test
    fun `triggerHevySync failure sets syncError`() = runTest {
        coEvery { mockClient.triggerHevySync() } returns
            Result.failure(java.net.ConnectException("Refused"))

        viewModel.state.test {
            awaitItem()

            viewModel.triggerHevySync()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // isSyncing = true
            val errorState = awaitItem()

            assertNotNull(errorState.syncError)
            assertTrue(errorState.syncError!!.contains("connect", ignoreCase = true))
            assertFalse(errorState.isSyncing)
        }
    }

    @Test
    fun `triggerHevySync is debounced — second call no-ops while syncing`() = runTest {
        coEvery { mockClient.triggerHevySync() } returns
            Result.success(HevySyncResult(true, 0, 0, 0, null))

        viewModel.triggerHevySync()
        viewModel.triggerHevySync() // second call while isSyncing = true

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockClient.triggerHevySync() }
    }
}
