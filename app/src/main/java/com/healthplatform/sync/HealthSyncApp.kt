package com.healthplatform.sync

import android.app.Application
import androidx.work.Configuration
import com.healthplatform.sync.service.WeeklySummaryWorker

class HealthSyncApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        WeeklySummaryWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
