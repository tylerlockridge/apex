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
import org.junit.Assert.assertTrue
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

    /** Drain IO-dispatched work back to the test scheduler. */
    private fun drainAll() {
        Thread.sleep(500)
        testDispatcher.scheduler.advanceUntilIdle()
    }

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
        drainAll()

        val state = vm.state.value
        assertEquals(122, state.lastBpSystolic)
        assertEquals(78, state.lastBpDiastolic)
        assertEquals("2026-03-02T08:00:00Z", state.lastBpTime)
    }

    @Test
    fun `loadFromPrefs returns null BP when prefs are empty`() = runTest {
        val vm = DashboardViewModel(app)
        drainAll()

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
        drainAll()

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
        drainAll()

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
        drainAll()

        assertEquals(now, vm.state.value.lastSyncMs)
    }

    // -------------------------------------------------------------------------
    // computeReadiness
    // -------------------------------------------------------------------------

    @Test
    fun `readiness good to go when all metrics optimal`() = runTest {
        prefs().edit()
            .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 115)
            .putInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 480) // 8h
            .putFloat(SyncPrefsKeys.LAST_HRV_MS, 65f)
            .apply()

        val vm = DashboardViewModel(app)
        drainAll()

        assertEquals("Good to go", vm.state.value.readinessLabel)
        assertEquals("All metrics looking good", vm.state.value.readinessReason)
    }

    @Test
    fun `readiness take it easy with moderate metrics`() = runTest {
        // BP 125 => +1, sleep 390min (6.5h) => +1 + concern, HRV 45 => +1  => score=3 >= 2
        prefs().edit()
            .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 125)
            .putInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 390)
            .putFloat(SyncPrefsKeys.LAST_HRV_MS, 45f)
            .apply()

        val vm = DashboardViewModel(app)
        drainAll()

        assertEquals("Take it easy", vm.state.value.readinessLabel)
        assertTrue(vm.state.value.readinessReason!!.contains("Under 7h sleep"))
    }

    @Test
    fun `readiness recovery day with poor metrics`() = runTest {
        // BP 145 => -1, sleep 300min (5h) => -1, HRV 20 => -1 => score=-3
        prefs().edit()
            .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 145)
            .putInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 300)
            .putFloat(SyncPrefsKeys.LAST_HRV_MS, 20f)
            .apply()

        val vm = DashboardViewModel(app)
        drainAll()

        assertEquals("Recovery day", vm.state.value.readinessLabel)
        assertTrue(vm.state.value.readinessReason!!.contains("BP high"))
        assertTrue(vm.state.value.readinessReason!!.contains("Low sleep"))
        assertTrue(vm.state.value.readinessReason!!.contains("Low HRV"))
    }

    @Test
    fun `readiness null when all three metrics null`() = runTest {
        // No prefs set => all null
        val vm = DashboardViewModel(app)
        drainAll()

        assertNull(vm.state.value.readinessLabel)
        assertNull(vm.state.value.readinessReason)
    }

    // -------------------------------------------------------------------------
    // triggerSync debounce
    // -------------------------------------------------------------------------

    @Test
    fun `triggerSync is debounced — second call while syncing is no-op`() = runTest {
        val vm = DashboardViewModel(app)
        drainAll()

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
