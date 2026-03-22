package com.healthplatform.sync.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.healthplatform.sync.SyncPrefsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private val testDispatcher = UnconfinedTestDispatcher()
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
    // Readiness engine integration (ADR-003)
    // HRV weight = 0 by default (A-01 unvalidated), so only sleep + BP contribute.
    // Scoring: sleep 360-540min → 0-100, BP 110-140 → 100-0.
    // Bands: >=80 "Good to go", 50-79 "Moderate", <50 "Recovery day".
    // -------------------------------------------------------------------------

    @Test
    fun `readiness good to go when sleep and BP are optimal`() = runTest {
        // Sleep 480min (8h, >=420) → 100, BP 115 (<120) → 100
        // Weighted: (100*0.30 + 100*0.20) / 0.50 = 100 → "Good to go"
        prefs().edit()
            .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 115)
            .putInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 480)
            .putString(SyncPrefsKeys.LAST_BP_TIME, java.time.Instant.now().toString())
            .putString(SyncPrefsKeys.LAST_SLEEP_TIME, java.time.Instant.now().toString())
            .apply()

        val vm = DashboardViewModel(app)
        drainAll()

        assertEquals("Good to go", vm.state.value.readinessLabel)
    }

    @Test
    fun `readiness take it easy with moderate metrics`() = runTest {
        // Sleep 400min (6h40m, 360-420) → 70, BP 125 (120-130) → 70
        // Weighted: (70*0.30 + 70*0.20) / 0.50 = 70 → "Take it easy"
        prefs().edit()
            .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 125)
            .putInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 400)
            .putString(SyncPrefsKeys.LAST_BP_TIME, java.time.Instant.now().toString())
            .putString(SyncPrefsKeys.LAST_SLEEP_TIME, java.time.Instant.now().toString())
            .apply()

        val vm = DashboardViewModel(app)
        drainAll()

        assertEquals("Take it easy", vm.state.value.readinessLabel)
    }

    @Test
    fun `readiness recovery day with poor metrics`() = runTest {
        // Sleep 300min (<360) → 25, BP 145 (>=140) → 10
        // Weighted: (25*0.30 + 10*0.20) / 0.50 = 19 → "Recovery day"
        prefs().edit()
            .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 145)
            .putInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 300)
            .putString(SyncPrefsKeys.LAST_BP_TIME, java.time.Instant.now().toString())
            .putString(SyncPrefsKeys.LAST_SLEEP_TIME, java.time.Instant.now().toString())
            .apply()

        val vm = DashboardViewModel(app)
        drainAll()

        assertEquals("Recovery day", vm.state.value.readinessLabel)
    }

    @Test
    fun `readiness null when no metrics available`() = runTest {
        val vm = DashboardViewModel(app)
        drainAll()

        assertNull(vm.state.value.readinessLabel)
    }

    @Test
    fun `HRV excluded by default — does not affect score`() = runTest {
        // Only set HRV, not sleep or BP — readiness should be null
        prefs().edit()
            .putFloat(SyncPrefsKeys.LAST_HRV_MS, 65f)
            .putString(SyncPrefsKeys.LAST_HRV_TIME, java.time.Instant.now().toString())
            .apply()

        val vm = DashboardViewModel(app)
        drainAll()

        // HRV weight is 0 → excluded → no contributing inputs → null label
        assertNull(vm.state.value.readinessLabel)
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
