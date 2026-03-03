package com.healthplatform.sync.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.healthplatform.sync.SyncPrefsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test for isolation
        app.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun prefs() = app.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)

    // -------------------------------------------------------------------------
    // loadFromPrefs — BP
    // -------------------------------------------------------------------------

    @Test
    fun `loadFromPrefs reads BP values correctly`() = runTest {
        prefs().edit()
            .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 122)
            .putInt(SyncPrefsKeys.LAST_BP_DIASTOLIC, 78)
            .putString(SyncPrefsKeys.LAST_BP_TIME, "2026-03-02T08:00:00Z")
            .apply()

        val vm = DashboardViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(122, state.lastBpSystolic)
        assertEquals(78, state.lastBpDiastolic)
        assertEquals("2026-03-02T08:00:00Z", state.lastBpTime)
    }

    @Test
    fun `loadFromPrefs returns null BP when prefs are empty`() = runTest {
        val vm = DashboardViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.lastBpSystolic)
        assertNull(vm.state.value.lastBpDiastolic)
    }

    // -------------------------------------------------------------------------
    // loadFromPrefs — Sleep
    // -------------------------------------------------------------------------

    @Test
    fun `loadFromPrefs reads sleep values correctly`() = runTest {
        prefs().edit()
            .putInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 450)
            .putInt(SyncPrefsKeys.LAST_SLEEP_DEEP_MIN, 90)
            .putInt(SyncPrefsKeys.LAST_SLEEP_REM_MIN, 105)
            .apply()

        val vm = DashboardViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(450, state.lastSleepDurationMin)
        assertEquals(90, state.lastSleepDeepMin)
        assertEquals(105, state.lastSleepRemMin)
    }

    // -------------------------------------------------------------------------
    // loadFromPrefs — HRV
    // -------------------------------------------------------------------------

    @Test
    fun `loadFromPrefs reads HRV value correctly`() = runTest {
        prefs().edit()
            .putFloat(SyncPrefsKeys.LAST_HRV_MS, 54.2f)
            .apply()

        val vm = DashboardViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(54.2, vm.state.value.lastHrvMs!!, 0.1)
    }

    // -------------------------------------------------------------------------
    // loadFromPrefs — lastSync
    // -------------------------------------------------------------------------

    @Test
    fun `loadFromPrefs reads lastSyncMs correctly`() = runTest {
        val now = System.currentTimeMillis()
        prefs().edit().putLong(SyncPrefsKeys.LAST_SYNC, now).apply()

        val vm = DashboardViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(now, vm.state.value.lastSyncMs)
    }

    // -------------------------------------------------------------------------
    // triggerSync debounce
    // -------------------------------------------------------------------------

    @Test
    fun `triggerSync is debounced — second call while syncing is no-op`() = runTest {
        val vm = DashboardViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.state.test {
            awaitItem() // initial loaded state

            // First call flips isSyncing=true
            vm.triggerSync()
            val syncingState = awaitItem()
            assertEquals(true, syncingState.isSyncing)

            // Second call while isSyncing=true should be ignored (no new emission)
            vm.triggerSync()
            // No additional state emission expected (debounced)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
