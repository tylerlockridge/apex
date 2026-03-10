package com.healthplatform.sync.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.healthplatform.sync.Config
import com.healthplatform.sync.NotificationChannels
import com.healthplatform.sync.SyncPrefsKeys
import com.healthplatform.sync.data.BloodPressureData
import com.healthplatform.sync.data.BodyMeasurementData
import com.healthplatform.sync.data.HealthConnectReader
import com.healthplatform.sync.data.HrvData
import com.healthplatform.sync.data.SleepData
import com.healthplatform.sync.data.db.ApexDatabase
import com.healthplatform.sync.data.db.SyncQueueEntity
import com.healthplatform.sync.data.db.computeRecordHash
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
            val serverUrl = Config.getServerUrl(applicationContext)
            val deviceSecret = com.healthplatform.sync.security.SecurePrefs
                .getDeviceSecret(applicationContext, Config.DEVICE_SECRET)
            val api = ApiService.get(serverUrl, deviceSecret, apiKey)
            val dao = ApexDatabase.get(applicationContext).syncQueueDao()

            val since = Instant.now().minus(30, ChronoUnit.DAYS)
            val filter = inputData.getString(KEY_DATA_TYPE_FILTER)

            // ----------------------------------------------------------------
            // Phase 1 — Read from Health Connect → write to Room queue
            //
            // Uses change tokens for incremental reads (BP, Sleep, HRV) —
            // only records changed since the last sync are fetched, reducing
            // battery drain significantly on subsequent syncs.
            //
            // Body measurements use a full 30-day read because combining
            // weight/body-fat/lean-mass changes incrementally is complex.
            //
            // Token lifecycle: saved after a successful HC read. On expiry
            // (token cleared), the next sync falls back to a full 30-day read
            // and acquires a fresh token.
            //
            // If Health Connect is unavailable (permissions revoked, HC error)
            // we log a warning and continue to Phase 2 — whatever is already
            // in the queue will still be retried.
            // ----------------------------------------------------------------

            if (filter == null || filter == DATA_TYPE_BP) {
                try {
                    val storedToken = prefs.getString(SyncPrefsKeys.CHANGE_TOKEN_BP, null)
                    val (records, newToken) = try {
                        if (storedToken != null) {
                            Log.d(TAG, "BP: incremental read using change token")
                            reader.readBloodPressureChanges(storedToken)
                        } else {
                            Log.d(TAG, "BP: full 30-day read (no token)")
                            reader.readBloodPressure(since) to reader.getBpChangesToken()
                        }
                    } catch (e: Exception) {
                        // Token expired — clear it and fall back to a full read immediately
                        // so this run still captures current data instead of waiting 15 min.
                        Log.w(TAG, "BP token expired/invalid — falling back to full read", e)
                        prefs.edit().remove(SyncPrefsKeys.CHANGE_TOKEN_BP).apply()
                        reader.readBloodPressure(since) to reader.getBpChangesToken()
                    }
                    dao.insertAll(records.map { it.toQueueEntity() })
                    prefs.edit().putString(SyncPrefsKeys.CHANGE_TOKEN_BP, newToken).apply()
                    Log.d(TAG, "Queued ${records.size} BP records from Health Connect")
                } catch (e: Exception) {
                    Log.w(TAG, "HC BP read failed — will flush existing queue", e)
                }
            }

            if (filter == null || filter == DATA_TYPE_SLEEP) {
                try {
                    val storedToken = prefs.getString(SyncPrefsKeys.CHANGE_TOKEN_SLEEP, null)
                    val (records, newToken) = try {
                        if (storedToken != null) {
                            Log.d(TAG, "Sleep: incremental read using change token")
                            reader.readSleepChanges(storedToken)
                        } else {
                            Log.d(TAG, "Sleep: full 30-day read (no token)")
                            reader.readSleep(since) to reader.getSleepChangesToken()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Sleep token expired/invalid — falling back to full read", e)
                        prefs.edit().remove(SyncPrefsKeys.CHANGE_TOKEN_SLEEP).apply()
                        reader.readSleep(since) to reader.getSleepChangesToken()
                    }
                    dao.insertAll(records.map { it.toQueueEntity() })
                    prefs.edit().putString(SyncPrefsKeys.CHANGE_TOKEN_SLEEP, newToken).apply()
                    Log.d(TAG, "Queued ${records.size} sleep records from Health Connect")
                } catch (e: Exception) {
                    Log.w(TAG, "HC sleep read failed — will flush existing queue", e)
                }
            }

            if (filter == null || filter == DATA_TYPE_BODY) {
                // Body measurements: full read (weight/body-fat/lean-mass merge is not
                // easily incremental). Room IGNORE strategy prevents duplicates.
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
                    val storedToken = prefs.getString(SyncPrefsKeys.CHANGE_TOKEN_HRV, null)
                    val (records, newToken) = try {
                        if (storedToken != null) {
                            Log.d(TAG, "HRV: incremental read using change token")
                            reader.readHrvChanges(storedToken)
                        } else {
                            Log.d(TAG, "HRV: full 30-day read (no token)")
                            reader.readHeartRateVariability(since) to reader.getHrvChangesToken()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "HRV token expired/invalid — falling back to full read", e)
                        prefs.edit().remove(SyncPrefsKeys.CHANGE_TOKEN_HRV).apply()
                        reader.readHeartRateVariability(since) to reader.getHrvChangesToken()
                    }
                    dao.insertAll(records.map { it.toQueueEntity() })
                    prefs.edit().putString(SyncPrefsKeys.CHANGE_TOKEN_HRV, newToken).apply()
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

            var anyTransientFailure = false
            var anyPermanentFailure = false

            // ---- Blood Pressure ----
            if (filter == null || filter == DATA_TYPE_BP) {
                val pending = dao.getPending(DATA_TYPE_BP)
                if (pending.isNotEmpty()) {
                    val records = pending.map { gson.fromJson(it.payload, BloodPressureData::class.java) }
                    api.syncBloodPressure(records).fold(
                        onSuccess = {
                            Log.i(TAG, "Synced ${records.size} BP records")
                            dao.delete(pending)
                            // maxByOrNull is defensive; records are sorted ASC by measuredAt (DAO guarantees this).
                            val latest = records.maxByOrNull { it.measuredAt } ?: return@fold
                            prefs.edit()
                                .putInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, latest.systolic)
                                .putInt(SyncPrefsKeys.LAST_BP_DIASTOLIC, latest.diastolic)
                                .putString(SyncPrefsKeys.LAST_BP_TIME, latest.measuredAt)
                                .apply()
                            // Alert if Stage 2 hypertension (systolic ≥ 140 or diastolic ≥ 90)
                            if (latest.systolic >= 140 || latest.diastolic >= 90) {
                                postBpAnomalyNotification(latest.systolic, latest.diastolic)
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "BP sync failed — ${pending.size} records kept in queue", e)
                            if (e is PermanentSyncFailure) anyPermanentFailure = true
                            else anyTransientFailure = true
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
                            dao.delete(pending)
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
                            if (e is PermanentSyncFailure) anyPermanentFailure = true
                            else anyTransientFailure = true
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
                            dao.delete(pending)
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
                            if (e is PermanentSyncFailure) anyPermanentFailure = true
                            else anyTransientFailure = true
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
                            dao.delete(pending)
                            val latest = records.maxByOrNull { it.measuredAt } ?: return@fold
                            prefs.edit()
                                .putFloat(SyncPrefsKeys.LAST_HRV_MS, latest.hrvMs.toFloat())
                                .putString(SyncPrefsKeys.LAST_HRV_TIME, latest.measuredAt)
                                .apply()
                        },
                        onFailure = { e ->
                            Log.e(TAG, "HRV sync failed — ${pending.size} records kept in queue", e)
                            if (e is PermanentSyncFailure) anyPermanentFailure = true
                            else anyTransientFailure = true
                        }
                    )
                }
            }

            val anyFailure = anyTransientFailure || anyPermanentFailure
            if (!anyFailure) {
                prefs.edit().putLong(SyncPrefsKeys.LAST_SYNC, System.currentTimeMillis()).apply()
            }

            recordSyncHistory(prefs, !anyFailure)
            // M-3: only trigger widget update when at least one widget is pinned —
            // avoids unnecessary Glance work when the user has no active widget.
            val widgetIds = GlanceAppWidgetManager(applicationContext)
                .getGlanceIds(HealthGlanceWidget::class.java)
            if (widgetIds.isNotEmpty()) {
                HealthGlanceWidget().updateAll(applicationContext)
            }

            // Permanent failures (401/403/400) must not be retried — stop WorkManager.
            // Transient failures (5xx, network) should be retried with exponential backoff.
            when {
                anyPermanentFailure -> Result.failure()
                anyTransientFailure -> Result.retry()
                else -> Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    // -------------------------------------------------------------------------
    // Queue entity builders — map typed data classes → SyncQueueEntity
    // -------------------------------------------------------------------------

    private fun BloodPressureData.toQueueEntity(): SyncQueueEntity {
        val payload = gson.toJson(this)
        return SyncQueueEntity(dataType = DATA_TYPE_BP, measuredAt = measuredAt,
            payload = payload, recordHash = computeRecordHash(DATA_TYPE_BP, payload))
    }

    private fun SleepData.toQueueEntity(): SyncQueueEntity {
        val payload = gson.toJson(this)
        return SyncQueueEntity(dataType = DATA_TYPE_SLEEP, measuredAt = sleepStart,
            payload = payload, recordHash = computeRecordHash(DATA_TYPE_SLEEP, payload))
    }

    private fun BodyMeasurementData.toQueueEntity(): SyncQueueEntity {
        val payload = gson.toJson(this)
        return SyncQueueEntity(dataType = DATA_TYPE_BODY, measuredAt = measuredAt,
            payload = payload, recordHash = computeRecordHash(DATA_TYPE_BODY, payload))
    }

    private fun HrvData.toQueueEntity(): SyncQueueEntity {
        val payload = gson.toJson(this)
        return SyncQueueEntity(dataType = DATA_TYPE_HRV, measuredAt = measuredAt,
            payload = payload, recordHash = computeRecordHash(DATA_TYPE_HRV, payload))
    }

    private fun postBpAnomalyNotification(systolic: Int, diastolic: Int) {
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Public version shown on lock screen — no raw health values.
        val publicNotification = NotificationCompat.Builder(applicationContext, NotificationChannels.BP_ANOMALY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Health alert")
            .setContentText("Open Apex to review a new alert")
            .build()

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.BP_ANOMALY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Elevated blood pressure")
            .setContentText("A new blood pressure alert is available in Apex")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your latest reading was $systolic/$diastolic mmHg. Open Apex to review your trends."))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_BP, notification)
    }

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
        private const val NOTIF_ID_BP = 1001

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
