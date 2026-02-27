package com.healthplatform.sync

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
class HealthSyncApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        // WorkManager is initialized on-demand via Configuration.Provider.
        // The default WorkManagerInitializer is removed in AndroidManifest.xml.
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
