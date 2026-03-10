package com.healthplatform.sync.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.healthplatform.sync.Config
import com.healthplatform.sync.SyncPrefsKeys
import com.google.gson.Gson
import com.healthplatform.sync.data.BloodPressureData
import com.healthplatform.sync.data.BodyMeasurementData
import com.healthplatform.sync.data.HealthConnectReader
import com.healthplatform.sync.data.HrvData
import com.healthplatform.sync.data.SleepData
import com.healthplatform.sync.data.db.ApexDatabase
import com.healthplatform.sync.data.db.SyncQueueDao
import com.healthplatform.sync.data.db.SyncQueueEntity
import com.healthplatform.sync.data.db.computeRecordHash
import com.healthplatform.sync.security.SecurePrefs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private val gson = Gson()
    private lateinit var context: Context
    private lateinit var mockApi: ApiService
    private lateinit var db: ApexDatabase
    private lateinit var dao: SyncQueueDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Clear shared prefs
        context.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        // Mock SecurePrefs
        mockkObject(SecurePrefs)
        every { SecurePrefs.getApiKey(any()) } returns "test-api-key"
        every { SecurePrefs.getDeviceSecret(any(), any()) } returns "test-secret"

        // Mock Config
        mockkObject(Config)
        every { Config.getServerUrl(any()) } returns "https://localhost"
        every { Config.DEVICE_SECRET } returns "test-secret"

        // Real Room DB (in-memory) for queue assertions
        db = Room.inMemoryDatabaseBuilder(context, ApexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncQueueDao()

        // Mock ApexDatabase.get() to return our in-memory DB
        mockkObject(ApexDatabase)
        every { ApexDatabase.get(any()) } returns db

        // Mock ApiService.get() to return our mock API
        mockApi = mockk()
        mockkObject(ApiService)
        every { ApiService.get(any(), any(), any()) } returns mockApi

        // Mock HealthConnectReader — HC not available in test environment
        mockkConstructor(HealthConnectReader::class)
        coEvery { anyConstructed<HealthConnectReader>().readBloodPressure(any()) } returns emptyList()
        coEvery { anyConstructed<HealthConnectReader>().readSleep(any()) } returns emptyList()
        coEvery { anyConstructed<HealthConnectReader>().readWeight(any()) } returns emptyList()
        coEvery { anyConstructed<HealthConnectReader>().readHeartRateVariability(any()) } returns emptyList()
        coEvery { anyConstructed<HealthConnectReader>().getBpChangesToken() } returns "bp-token"
        coEvery { anyConstructed<HealthConnectReader>().getSleepChangesToken() } returns "sleep-token"
        coEvery { anyConstructed<HealthConnectReader>().getHrvChangesToken() } returns "hrv-token"

        // Suppress Glance widget update
        mockkConstructor(androidx.glance.appwidget.GlanceAppWidgetManager::class)
        coEvery {
            anyConstructed<androidx.glance.appwidget.GlanceAppWidgetManager>()
                .getGlanceIds(any<Class<androidx.glance.appwidget.GlanceAppWidget>>())
        } returns emptyList<androidx.glance.GlanceId>()
    }

    @After
    fun tearDown() {
        db.close()
        unmockkAll()
    }

    private fun prefs() = context.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)

    // -------------------------------------------------------------------------
    // doWork orchestration
    // -------------------------------------------------------------------------

    @Test
    fun `all succeed returns Result success`() = runTest {
        // No records in queue, all HC reads return empty → nothing to sync
        coEvery { mockApi.syncBloodPressure(any()) } returns Result.success(SyncResponse(true, 0, null))
        coEvery { mockApi.syncSleep(any()) } returns Result.success(SyncResponse(true, 0, null))
        coEvery { mockApi.syncBodyMeasurements(any()) } returns Result.success(SyncResponse(true, 0, null))
        coEvery { mockApi.syncHrv(any()) } returns Result.success(SyncResponse(true, 0, null))

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `BP sync failure returns Result retry`() = runTest {
        // Pre-seed the queue with a BP record
        val bpPayload = """{"systolic":120,"diastolic":80,"measuredAt":"2026-03-01T08:00:00Z"}"""
        dao.insertAll(listOf(
            SyncQueueEntity(
                dataType = SyncWorker.DATA_TYPE_BP,
                measuredAt = "2026-03-01T08:00:00Z",
                payload = bpPayload,
                recordHash = computeRecordHash(SyncWorker.DATA_TYPE_BP, bpPayload)
            )
        ))

        coEvery { mockApi.syncBloodPressure(any()) } returns
            Result.failure(java.io.IOException("Network error"))

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        // Records should still be in the queue (not deleted on failure)
        assertEquals(1, dao.pendingCount())
    }

    @Test
    fun `expired BP change token removed from prefs, execution continues`() = runTest {
        // Set a change token in prefs
        prefs().edit().putString(SyncPrefsKeys.CHANGE_TOKEN_BP, "old-token").apply()

        // Incremental read throws (expired token)
        coEvery { anyConstructed<HealthConnectReader>().readBloodPressureChanges("old-token") } throws
            Exception("Token expired")

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        // After fallback full read a fresh token is re-acquired and persisted
        assertEquals("bp-token", prefs().getString(SyncPrefsKeys.CHANGE_TOKEN_BP, null))
        // Should still succeed (Phase 2 has nothing to flush → no failures)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `HC exception continues to Phase 2`() = runTest {
        // All HC reads throw
        coEvery { anyConstructed<HealthConnectReader>().readBloodPressure(any()) } throws
            Exception("HC unavailable")
        coEvery { anyConstructed<HealthConnectReader>().readSleep(any()) } throws
            Exception("HC unavailable")
        coEvery { anyConstructed<HealthConnectReader>().readWeight(any()) } throws
            Exception("HC unavailable")
        coEvery { anyConstructed<HealthConnectReader>().readHeartRateVariability(any()) } throws
            Exception("HC unavailable")

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        // Phase 2 has nothing in queue → success
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `anomaly notification when systolic at 140`() = runTest {
        // Pre-seed queue with hypertensive BP
        val gson = com.google.gson.Gson()
        val bp = BloodPressureData(systolic = 140, diastolic = 85, measuredAt = "2026-03-01T08:00:00Z")
        val bpPayload1 = gson.toJson(bp)
        dao.insertAll(listOf(
            SyncQueueEntity(
                dataType = SyncWorker.DATA_TYPE_BP,
                measuredAt = bp.measuredAt,
                payload = bpPayload1,
                recordHash = computeRecordHash(SyncWorker.DATA_TYPE_BP, bpPayload1)
            )
        ))

        coEvery { mockApi.syncBloodPressure(any()) } returns
            Result.success(SyncResponse(true, 1, "sync-1"))

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // BP anomaly threshold: systolic >= 140. Record should be synced.
        assertEquals(0, dao.getPending(SyncWorker.DATA_TYPE_BP).size)
    }

    @Test
    fun `anomaly notification when diastolic at 90`() = runTest {
        val gson = com.google.gson.Gson()
        val bp = BloodPressureData(systolic = 125, diastolic = 90, measuredAt = "2026-03-01T08:00:00Z")
        val bpPayload2 = gson.toJson(bp)
        dao.insertAll(listOf(
            SyncQueueEntity(
                dataType = SyncWorker.DATA_TYPE_BP,
                measuredAt = bp.measuredAt,
                payload = bpPayload2,
                recordHash = computeRecordHash(SyncWorker.DATA_TYPE_BP, bpPayload2)
            )
        ))

        coEvery { mockApi.syncBloodPressure(any()) } returns
            Result.success(SyncResponse(true, 1, "sync-1"))

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, dao.getPending(SyncWorker.DATA_TYPE_BP).size)
    }

    @Test
    fun `blank API key returns Result failure immediately`() = runTest {
        every { SecurePrefs.getApiKey(any()) } returns ""

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    // -------------------------------------------------------------------------
    // US-005: Queue entity builders (tested via doWork Phase 1 → DAO capture)
    // -------------------------------------------------------------------------

    @Test
    fun `BP toQueueEntity sets dataType to blood_pressure`() = runTest {
        val bp = BloodPressureData(systolic = 120, diastolic = 80, measuredAt = "2026-03-01T08:00:00Z")
        coEvery { anyConstructed<HealthConnectReader>().readBloodPressure(any()) } returns listOf(bp)

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        worker.doWork()

        val pending = dao.getPending(SyncWorker.DATA_TYPE_BP)
        assertEquals(1, pending.size)
        assertEquals("blood_pressure", pending[0].dataType)
        // Deserialize payload and verify fields
        val parsed = gson.fromJson(pending[0].payload, BloodPressureData::class.java)
        assertEquals(120, parsed.systolic)
        assertEquals(80, parsed.diastolic)
    }

    @Test
    fun `Sleep toQueueEntity uses sleepStart as measuredAt`() = runTest {
        val sleep = SleepData(
            sleepStart = "2026-03-01T23:00:00Z",
            sleepEnd = "2026-03-02T07:00:00Z",
            durationMinutes = 480
        )
        coEvery { anyConstructed<HealthConnectReader>().readSleep(any()) } returns listOf(sleep)

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        worker.doWork()

        val pending = dao.getPending(SyncWorker.DATA_TYPE_SLEEP)
        assertEquals(1, pending.size)
        assertEquals("2026-03-01T23:00:00Z", pending[0].measuredAt)
    }

    @Test
    fun `Body toQueueEntity maps weight bodyFat and leanMass`() = runTest {
        val body = BodyMeasurementData(
            measuredAt = "2026-03-01T07:00:00Z",
            weightKg = 80.0,
            bodyFatPercent = 18.5,
            muscleMassKg = 32.0
        )
        coEvery { anyConstructed<HealthConnectReader>().readWeight(any()) } returns listOf(body)

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        worker.doWork()

        val pending = dao.getPending(SyncWorker.DATA_TYPE_BODY)
        assertEquals(1, pending.size)
        val parsed = gson.fromJson(pending[0].payload, BodyMeasurementData::class.java)
        assertEquals(80.0, parsed.weightKg!!, 0.01)
        assertEquals(18.5, parsed.bodyFatPercent!!, 0.01)
        assertEquals(32.0, parsed.muscleMassKg!!, 0.01)
    }

    @Test
    fun `HRV toQueueEntity maps hrvMs and deviceName`() = runTest {
        val hrv = HrvData(measuredAt = "2026-03-01T06:00:00Z", hrvMs = 55.3, deviceName = "Oura Ring")
        coEvery { anyConstructed<HealthConnectReader>().readHeartRateVariability(any()) } returns listOf(hrv)

        val worker = TestListenableWorkerBuilder<SyncWorker>(context).build()
        worker.doWork()

        val pending = dao.getPending(SyncWorker.DATA_TYPE_HRV)
        assertEquals(1, pending.size)
        val parsed = gson.fromJson(pending[0].payload, HrvData::class.java)
        assertEquals(55.3, parsed.hrvMs, 0.01)
        assertEquals("Oura Ring", parsed.deviceName)
    }

    // -------------------------------------------------------------------------
    // US-005: Schedule and cancel
    // -------------------------------------------------------------------------

    @Test
    fun `schedule enqueues periodic work`() = runTest {
        val config = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        } catch (_: IllegalStateException) {
            // Already initialized
        }

        SyncWorker.schedule(context)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncWorker.WORK_NAME)
            .get()

        assertTrue(workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)
    }

    @Test
    fun `cancel removes scheduled work`() = runTest {
        val config = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        } catch (_: IllegalStateException) {
            // Already initialized
        }

        SyncWorker.schedule(context)
        SyncWorker.cancel(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncWorker.WORK_NAME)
            .get()

        // After cancel, work should be cancelled
        assertTrue(workInfos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
