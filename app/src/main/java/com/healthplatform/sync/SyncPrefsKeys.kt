package com.healthplatform.sync

/** Single source of truth for all SharedPreferences key strings used across
 *  SyncWorker, DashboardViewModel, SettingsScreen, and HealthGlanceWidget. */
object SyncPrefsKeys {
    const val FILE_NAME = "health_sync"

    const val LAST_BP_SYSTOLIC       = "last_bp_systolic"
    const val LAST_BP_DIASTOLIC      = "last_bp_diastolic"
    const val LAST_BP_TIME           = "last_bp_time"

    const val LAST_SLEEP_DURATION_MIN = "last_sleep_duration_min"
    const val LAST_SLEEP_DEEP_MIN    = "last_sleep_deep_min"
    const val LAST_SLEEP_REM_MIN     = "last_sleep_rem_min"
    const val LAST_SLEEP_TIME        = "last_sleep_time"

    const val LAST_WEIGHT_KG         = "last_weight_kg"
    const val LAST_WEIGHT_TIME       = "last_weight_time"

    const val LAST_HRV_MS            = "last_hrv_ms"
    const val LAST_HRV_TIME          = "last_hrv_time"

    const val LAST_SYNC              = "last_sync"
    const val AUTO_SYNC              = "auto_sync"

    /** JSON array of last 10 sync events — `[{"t":<ms>,"ok":<bool>},...]` newest first. */
    const val SYNC_HISTORY           = "sync_history"

    // Health Connect change tokens — one per data type.
    // Stored after each successful HC read; cleared on token expiry.
    const val CHANGE_TOKEN_BP        = "change_token_bp"
    const val CHANGE_TOKEN_SLEEP     = "change_token_sleep"
    const val CHANGE_TOKEN_HRV       = "change_token_hrv"

    /** Server URL set by QR onboarding. Falls back to Config.SERVER_URL_DEFAULT if absent. */
    const val SERVER_URL             = "server_url"
}
