package com.healthplatform.sync.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.healthplatform.sync.Config
import com.healthplatform.sync.data.HealthConnectReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("health_sync", Context.MODE_PRIVATE)

            val reader = HealthConnectReader(applicationContext)
            val api = ApiService(Config.SERVER_URL, Config.DEVICE_SECRET, "")

            // Read data from the last 30 days to ensure recent values are always shown
            val since = Instant.now().minus(30, ChronoUnit.DAYS)

            // Sync Blood Pressure
            val bpRecords = reader.readBloodPressure(since)
            if (bpRecords.isNotEmpty()) {
                api.syncBloodPressure(bpRecords)
                Log.i(TAG, "Synced ${bpRecords.size} BP records")
                val latest = bpRecords.last()
                prefs.edit()
                    .putInt("last_bp_systolic", latest.systolic)
                    .putInt("last_bp_diastolic", latest.diastolic)
                    .putString("last_bp_time", latest.measuredAt)
                    .apply()
            }

            // Sync Sleep
            val sleepRecords = reader.readSleep(since)
            if (sleepRecords.isNotEmpty()) {
                api.syncSleep(sleepRecords)
                Log.i(TAG, "Synced ${sleepRecords.size} sleep records")
                val latest = sleepRecords.last()
                prefs.edit()
                    .putInt("last_sleep_duration_min", latest.durationMinutes)
                    .putInt("last_sleep_deep_min", latest.deepSleepMinutes ?: 0)
                    .putInt("last_sleep_rem_min", latest.remSleepMinutes ?: 0)
                    .putString("last_sleep_time", latest.sleepEnd)
                    .apply()
            }

            // Sync Body Measurements
            val bodyRecords = reader.readWeight(since)
            if (bodyRecords.isNotEmpty()) {
                api.syncBodyMeasurements(bodyRecords)
                Log.i(TAG, "Synced ${bodyRecords.size} body measurement records")
                val latest = bodyRecords.last()
                if (latest.weightKg != null) {
                    prefs.edit()
                        .putFloat("last_weight_kg", latest.weightKg.toFloat())
                        .putString("last_weight_time", latest.measuredAt)
                        .apply()
                }
            }

            // Sync HRV
            val hrvRecords = reader.readHeartRateVariability(since)
            if (hrvRecords.isNotEmpty()) {
                Log.i(TAG, "Read ${hrvRecords.size} HRV records")
                val latest = hrvRecords.last()
                prefs.edit()
                    .putFloat("last_hrv_ms", latest.hrvMs.toFloat())
                    .putString("last_hrv_time", latest.measuredAt)
                    .apply()
            }

            // Update last sync time
            prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "health_sync_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
        }

        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
