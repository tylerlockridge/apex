package com.healthplatform.sync.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.healthplatform.sync.Config
import com.healthplatform.sync.SyncPrefsKeys
import com.healthplatform.sync.data.BloodPressureData
import com.healthplatform.sync.data.BodyMeasurementData
import com.healthplatform.sync.data.HealthConnectReader
import com.healthplatform.sync.data.HrvData
import com.healthplatform.sync.data.SleepData
import com.healthplatform.sync.data.db.ApexDatabase
import com.healthplatform.sync.data.db.SyncQueueEntity
import com.healthplatform.sync.security.SecurePrefs
import com.healthplatform.sync.widget.HealthGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val apiKey = SecurePrefs.getApiKey(applicationContext)
            if (apiKey.isBlank()) {
                Log.w(TAG, "No API key configured — skipping sync")
                return@withContext Result.failure()
            }

            val prefs = applicationContext
                .getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)

            val reader = HealthConnectReader(applicationContext)
            val api = ApiService.get(Config.SERVER_URL, Config.DEVICE_SECRET, apiKey)
            val dao = ApexDatabase.get(applicationContext).syncQueueDao()

            val since = Instant.now().minus(30, ChronoUnit.DAYS)
            val filter = inputData.getString(KEY_DATA_TYPE_FILTER)

            // ----------------------------------------------------------------
            // Phase 1 — Read from Health Connect → write to Room queue
            //
            // Each record is keyed by (dataType, measuredAt). The DAO uses
            // OnConflictStrategy.IGNORE, so records already in the queue are
            // silently skipped and never duplicated.
            //
            // If Health Connect is unavailable (permissions revoked, HC error)
            // we log a warning and continue to Phase 2 — whatever is already
            // in the queue will still be retried.
            // ----------------------------------------------------------------

            if (filter == null || filter == DATA_TYPE_BP) {
                try {
                    val records = reader.readBloodPressure(since)
                    dao.insertAll(records.map { it.toQueueEntity() })
                    Log.d(TAG, "Queued ${records.size} BP records from Health Connect")
                } catch (e: Exception) {
                    Log.w(TAG, "HC BP read failed — will flush existing queue", e)
                }
            }

            if (filter == null || filter == DATA_TYPE_SLEEP) {
                try {
                    val records = reader.readSleep(since)
                    dao.insertAll(records.map { it.toQueueEntity() })
                    Log.d(TAG, "Queued ${records.size} sleep records from Health Connect")
                } catch (e: Exception) {
                    Log.w(TAG, "HC sleep read failed — will flush existing queue", e)
                }
            }

            if (filter == null || filter == DATA_TYPE_BODY) {
                try {
                    val records = reader.readWeight(since)
                    dao.insertAll(records.map { it.toQueueEntity() })
                    Log.d(TAG, "Queued ${records.size} body records from Health Connect")
                } catch (e: Exception) {
                    Log.w(TAG, "HC body read failed — will flush existing queue", e)
                }
            }

            if (filter == null || filter == DATA_TYPE_HRV) {
                try {
                    val records = reader.readHeartRateVariability(since)
                    dao.insertAll(records.map { it.toQueueEntity() })
                    Log.d(TAG, "Queued ${records.size} HRV records from Health Connect")
                } catch (e: Exception) {
                    Log.w(TAG, "HC HRV read failed — will flush existing queue", e)
                }
            }

            // ----------------------------------------------------------------
            // Phase 2 — Flush the Room queue to the server
            //
            // Records are only deleted from the queue after the server
            // acknowledges a successful upload. A network failure leaves them
            // in place; WorkManager's exponential backoff will retry.
            // ----------------------------------------------------------------

            var anyFailure = false

            // ---- Blood Pressure ----
            if (filter == null || filter == DATA_TYPE_BP) {
                val pending = dao.getPending(DATA_TYPE_BP)
                if (pending.isNotEmpty()) {
                    val records = pending.map { gson.fromJson(it.payload, BloodPressureData::class.java) }
                    api.syncBloodPressure(records).fold(
                        onSuccess = {
                            Log.i(TAG, "Synced ${records.size} BP records")
                            dao.deleteByIds(pending.map { it.id })
                            // maxByOrNull is defensive; records are sorted ASC by measuredAt (DAO guarantees this).
                            val latest = records.maxByOrNull { it.measuredAt } ?: return@fold
                            prefs.edit()
                                .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, latest.systolic)
                                .putInt(SyncPrefsKeys.LAST_BP_DIASTOLIC, latest.diastolic)
                                .putString(SyncPrefsKeys.LAST_BP_TIME, latest.measuredAt)
                                .apply()
                        },
                        onFailure = { e ->
                            Log.e(TAG, "BP sync failed — ${pending.size} records kept in queue", e)
                            anyFailure = true
                        }
                    )
                }
            }

            // ---- Sleep ----
            if (filter == null || filter == DATA_TYPE_SLEEP) {
                val pending = dao.getPending(DATA_TYPE_SLEEP)
                if (pending.isNotEmpty()) {
                    val records = pending.map { gson.fromJson(it.payload, SleepData::class.java) }
                    api.syncSleep(records).fold(
                        onSuccess = {
                            Log.i(TAG, "Synced ${records.size} sleep records")
                            dao.deleteByIds(pending.map { it.id })
                            val latest = records.maxByOrNull { it.sleepStart } ?: return@fold
                            prefs.edit()
                                .putInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, latest.durationMinutes)
                                .putInt(SyncPrefsKeys.LAST_SLEEP_DEEP_MIN, latest.deepSleepMinutes ?: 0)
                                .putInt(SyncPrefsKeys.LAST_SLEEP_REM_MIN, latest.remSleepMinutes ?: 0)
                                .putString(SyncPrefsKeys.LAST_SLEEP_TIME, latest.sleepEnd)
                                .apply()
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Sleep sync failed — ${pending.size} records kept in queue", e)
                            anyFailure = true
                        }
                    )
                }
            }

            // ---- Body Measurements ----
            if (filter == null || filter == DATA_TYPE_BODY) {
                val pending = dao.getPending(DATA_TYPE_BODY)
                if (pending.isNotEmpty()) {
                    val records = pending.map { gson.fromJson(it.payload, BodyMeasurementData::class.java) }
                    api.syncBodyMeasurements(records).fold(
                        onSuccess = {
                            Log.i(TAG, "Synced ${records.size} body records")
                            dao.deleteByIds(pending.map { it.id })
                            val latest = records.maxByOrNull { it.measuredAt } ?: return@fold
                            if (latest.weightKg != null) {
                                prefs.edit()
                                    .putFloat(SyncPrefsKeys.LAST_WEIGHT_KG, latest.weightKg.toFloat())
                                    .putString(SyncPrefsKeys.LAST_WEIGHT_TIME, latest.measuredAt)
                                    .apply()
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Body sync failed — ${pending.size} records kept in queue", e)
                            anyFailure = true
                        }
                    )
                }
            }

            // ---- HRV ----
            if (filter == null || filter == DATA_TYPE_HRV) {
                val pending = dao.getPending(DATA_TYPE_HRV)
                if (pending.isNotEmpty()) {
                    val records = pending.map { gson.fromJson(it.payload, HrvData::class.java) }
                    api.syncHrv(records).fold(
                        onSuccess = {
                            Log.i(TAG, "Synced ${records.size} HRV records")
                            dao.deleteByIds(pending.map { it.id })
                            val latest = records.maxByOrNull { it.measuredAt } ?: return@fold
                            prefs.edit()
                                .putFloat(SyncPrefsKeys.LAST_HRV_MS, latest.hrvMs.toFloat())
                                .putString(SyncPrefsKeys.LAST_HRV_TIME, latest.measuredAt)
                                .apply()
                        },
                        onFailure = { e ->
                            Log.e(TAG, "HRV sync failed — ${pending.size} records kept in queue", e)
                            anyFailure = true
                        }
                    )
                }
            }

            if (!anyFailure) {
                prefs.edit().putLong(SyncPrefsKeys.LAST_SYNC, System.currentTimeMillis()).apply()
            }

            recordSyncHistory(prefs, !anyFailure)
            HealthGlanceWidget().updateAll(applicationContext)

            if (anyFailure) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    // -------------------------------------------------------------------------
    // Queue entity builders — map typed data classes → SyncQueueEntity
    // -------------------------------------------------------------------------

    private fun BloodPressureData.toQueueEntity() = SyncQueueEntity(
        dataType = DATA_TYPE_BP,
        measuredAt = measuredAt,
        payload = gson.toJson(this)
    )

    private fun SleepData.toQueueEntity() = SyncQueueEntity(
        dataType = DATA_TYPE_SLEEP,
        measuredAt = sleepStart,   // sleepStart is the unique identifier for a session
        payload = gson.toJson(this)
    )

    private fun BodyMeasurementData.toQueueEntity() = SyncQueueEntity(
        dataType = DATA_TYPE_BODY,
        measuredAt = measuredAt,
        payload = gson.toJson(this)
    )

    private fun HrvData.toQueueEntity() = SyncQueueEntity(
        dataType = DATA_TYPE_HRV,
        measuredAt = measuredAt,
        payload = gson.toJson(this)
    )

    /** Prepends a sync event to the rolling history (last 10 entries, newest first). */
    private fun recordSyncHistory(prefs: SharedPreferences, success: Boolean) {
        val existing = prefs.getString(SyncPrefsKeys.SYNC_HISTORY, "[]") ?: "[]"
        val arr = try { JSONArray(existing) } catch (e: Exception) { JSONArray() }
        val entry = JSONObject().put("t", System.currentTimeMillis()).put("ok", success)
        val updated = JSONArray().apply {
            put(entry)
            for (i in 0 until minOf(arr.length(), 9)) put(arr.getJSONObject(i))
        }
        prefs.edit().putString(SyncPrefsKeys.SYNC_HISTORY, updated.toString()).apply()
    }

    companion object {
        private const val TAG = "SyncWorker"

        /** Name of the periodic work — exposed so callers can observe its state. */
        const val WORK_NAME = "health_sync_periodic"

        /** Shared Gson instance — [ApiService.Companion.gson] is the same object. */
        private val gson = ApiService.gson

        const val KEY_DATA_TYPE_FILTER = "data_type_filter"
        const val DATA_TYPE_BP    = "blood_pressure"
        const val DATA_TYPE_SLEEP = "sleep"
        const val DATA_TYPE_BODY  = "body"
        const val DATA_TYPE_HRV   = "hrv"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * Enqueues a one-time sync and returns the work ID so callers can observe
         * completion via [WorkManager.getWorkInfoByIdFlow].
         *
         * @param dataTypeFilter one of [DATA_TYPE_BP], [DATA_TYPE_SLEEP], [DATA_TYPE_BODY],
         *   [DATA_TYPE_HRV], or null to sync all types.
         */
        fun runOnce(context: Context, dataTypeFilter: String? = null): UUID {
            val inputData = if (dataTypeFilter != null) {
                workDataOf(KEY_DATA_TYPE_FILTER to dataTypeFilter)
            } else {
                workDataOf()
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
