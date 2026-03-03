package com.healthplatform.sync.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.healthplatform.sync.service.BpReadingResponse
import com.healthplatform.sync.service.BodyMeasurementResponse
import com.healthplatform.sync.service.HrvReadingResponse
import com.healthplatform.sync.service.ServerApiClient
import com.healthplatform.sync.service.SleepSessionResponse
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrendsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockClient: ServerApiClient
    private lateinit var viewModel: TrendsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockClient = mockk()
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = TrendsViewModel(app, clientProvider = { mockClient })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // loadBp
    // -------------------------------------------------------------------------

    @Test
    fun `loadBp success maps response to sorted BpReading list`() = runTest {
        val raw = listOf(
            BpReadingResponse(null, 125, 82, null, "2026-03-02T08:00:00Z", null, null),
            BpReadingResponse(null, 118, 76, null, "2026-03-01T08:00:00Z", null, null),
        )
        coEvery { mockClient.getBloodPressure(any()) } returns Result.success(raw)

        viewModel.state.test {
            val initial = awaitItem()
            assertEquals(0, initial.selectedTab)

            viewModel.loadBp(days = 7)
            testDispatcher.scheduler.advanceUntilIdle()

            // Skip the isLoading=true item
            skipItems(1)
            val loaded = awaitItem()

            assertEquals(2, loaded.bpReadings.size)
            // Sorted ascending by measured_at
            assertEquals(118, loaded.bpReadings[0].systolic)
            assertEquals(125, loaded.bpReadings[1].systolic)
            assertNull(loaded.error)
        }
    }

    @Test
    fun `loadBp failure sets friendly error message`() = runTest {
        coEvery { mockClient.getBloodPressure(any()) } returns
            Result.failure(java.net.SocketTimeoutException())

        viewModel.state.test {
            awaitItem() // initial

            viewModel.loadBp(days = 7)
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // isLoading=true
            val errorState = awaitItem()

            assertTrue(errorState.error!!.contains("unreachable"))
            assertTrue(errorState.bpReadings.isEmpty())
        }
    }

    // -------------------------------------------------------------------------
    // loadSleep
    // -------------------------------------------------------------------------

    @Test
    fun `loadSleep success maps response correctly`() = runTest {
        val raw = listOf(
            SleepSessionResponse(null, "2026-03-01T23:00:00Z", "2026-03-02T07:00:00Z",
                480, 90, 100, 200, 82, "health_connect"),
        )
        coEvery { mockClient.getSleep(any()) } returns Result.success(raw)

        viewModel.state.test {
            awaitItem()
            viewModel.loadSleep(7)
            testDispatcher.scheduler.advanceUntilIdle()
            skipItems(1)
            val loaded = awaitItem()

            assertEquals(1, loaded.sleepSessions.size)
            assertEquals(480, loaded.sleepSessions[0].durationMinutes)
            assertEquals(90, loaded.sleepSessions[0].deepSleepMinutes)
        }
    }

    // -------------------------------------------------------------------------
    // loadHrv
    // -------------------------------------------------------------------------

    @Test
    fun `loadHrv success maps response to sorted HrvReading list`() = runTest {
        val raw = listOf(
            HrvReadingResponse(null, "2026-03-02T06:00:00Z", 55.3, null, "sleep", null, null),
            HrvReadingResponse(null, "2026-03-01T06:00:00Z", 48.7, null, "sleep", null, null),
        )
        coEvery { mockClient.getHrv(any()) } returns Result.success(raw)

        viewModel.state.test {
            awaitItem()
            viewModel.loadHrv(7)
            testDispatcher.scheduler.advanceUntilIdle()
            skipItems(1)
            val loaded = awaitItem()

            assertEquals(2, loaded.hrvReadings.size)
            // Sorted ascending by measured_at
            assertEquals(48.7, loaded.hrvReadings[0].hrvMs, 0.01)
            assertEquals(55.3, loaded.hrvReadings[1].hrvMs, 0.01)
            assertNull(loaded.error)
        }
    }

    @Test
    fun `loadHrv failure sets error, does not clear existing HRV data`() = runTest {
        // Seed some existing data first
        val initial = listOf(
            HrvReadingResponse(null, "2026-03-01T06:00:00Z", 50.0, null, null, null, null)
        )
        coEvery { mockClient.getHrv(7) } returns Result.success(initial)
        viewModel.loadHrv(7)
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { mockClient.getHrv(30) } returns
            Result.failure(java.net.ConnectException("Refused"))

        viewModel.state.test {
            awaitItem() // current state with data

            viewModel.loadHrv(30)
            testDispatcher.scheduler.advanceUntilIdle()
            skipItems(1) // isLoading=true
            val errorState = awaitItem()

            assertTrue(errorState.error!!.contains("connect", ignoreCase = true))
        }
    }

    // -------------------------------------------------------------------------
    // selectTab
    // -------------------------------------------------------------------------

    @Test
    fun `selectTab 3 triggers loadHrv`() = runTest {
        coEvery { mockClient.getBloodPressure(any()) } returns Result.success(emptyList())
        coEvery { mockClient.getHrv(any()) } returns Result.success(emptyList())

        viewModel.state.test {
            awaitItem()
            viewModel.selectTab(3)
            testDispatcher.scheduler.advanceUntilIdle()

            // Drain loading/loaded items
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { mockClient.getHrv(any()) }
    }

    // -------------------------------------------------------------------------
    // selectRange
    // -------------------------------------------------------------------------

    @Test
    fun `selectRange re-loads current tab with new day count`() = runTest {
        coEvery { mockClient.getBloodPressure(30) } returns Result.success(emptyList())

        viewModel.state.test {
            awaitItem()
            viewModel.selectRange(30)
            testDispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { mockClient.getBloodPressure(30) }
    }
}
