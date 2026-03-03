package com.healthplatform.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Centralises notification channel IDs and creation. Call [createAll] once in Application.onCreate(). */
object NotificationChannels {
    const val BP_ANOMALY     = "bp_anomaly"
    const val WEEKLY_SUMMARY = "weekly_summary"

    fun createAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(BP_ANOMALY, "Blood Pressure Alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alerts when your blood pressure is elevated" }
        )
        nm.createNotificationChannel(
            NotificationChannel(WEEKLY_SUMMARY, "Weekly Health Summary", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Sunday digest of your weekly health trends" }
        )
    }
}
