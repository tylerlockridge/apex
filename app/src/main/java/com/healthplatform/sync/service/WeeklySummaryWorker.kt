package com.healthplatform.sync.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.healthplatform.sync.NotificationChannels
import com.healthplatform.sync.SyncPrefsKeys
import com.healthplatform.sync.ui.MainActivity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Posts a weekly health summary notification on Sunday at 9 AM. */
class WeeklySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)

        // Skip if we've never synced — nothing to summarise
        if (prefs.getLong(SyncPrefsKeys.LAST_SYNC, 0L) == 0L) return Result.success()

        val bpSystolic    = if (prefs.contains(SyncPrefsKeys.LAST_BP_SYSTOLIC)) prefs.getInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 0) else null
        val bpDiastolic   = if (prefs.contains(SyncPrefsKeys.LAST_BP_DIASTOLIC)) prefs.getInt(SyncPrefsKeys.LAST_BP_DIASTOLIC, 0) else null
        val sleepMin      = if (prefs.contains(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN)) prefs.getInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 0) else null
        val hrvMs         = if (prefs.contains(SyncPrefsKeys.LAST_HRV_MS)) prefs.getFloat(SyncPrefsKeys.LAST_HRV_MS, 0f).toDouble() else null

        val lines = buildList {
            if (bpSystolic != null && bpDiastolic != null) {
                val status = when {
                    bpSystolic < 120 -> "Normal"
                    bpSystolic < 130 -> "Elevated"
                    else -> "High"
                }
                add("Blood pressure: $bpSystolic/$bpDiastolic mmHg ($status)")
            }
            if (sleepMin != null) {
                val h = sleepMin / 60
                val m = sleepMin % 60
                add("Last sleep: ${h}h ${m}m")
            }
            if (hrvMs != null) add("HRV: ${"%.0f".format(hrvMs)} ms RMSSD")
        }
        if (lines.isEmpty()) return Result.success()

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Public version shown on lock screen — no raw health values.
        val publicNotification = NotificationCompat.Builder(applicationContext, NotificationChannels.WEEKLY_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Weekly Health Summary")
            .setContentText("Open Apex to review your summary")
            .build()

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.WEEKLY_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Weekly Health Summary")
            .setContentText("Your weekly health summary is ready in Apex")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)

        return Result.success()
    }

    companion object {
        private const val NOTIF_ID  = 1002
        const val WORK_NAME         = "weekly_health_summary"

        fun schedule(context: Context) {
            // Calculate initial delay to the next Sunday at 09:00
            val now    = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.WEEK_OF_YEAR, 1)
            }
            val initialDelayMs = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
