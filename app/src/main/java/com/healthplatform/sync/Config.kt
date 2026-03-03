package com.healthplatform.sync

import android.content.Context

object Config {
    const val SERVER_URL_DEFAULT = "https://tyler-health.duckdns.org"

    /** Minimum server version required for full feature compatibility. */
    const val MIN_SERVER_VERSION = "1.0.0"

    // Device sync secret is injected at build time from local.properties (gitignored).
    // Never hardcode secrets in source — add device.secret=<value> to local.properties.
    val DEVICE_SECRET: String get() = BuildConfig.DEVICE_SECRET

    /** Returns the server URL configured via QR onboarding, falling back to the default. */
    fun getServerUrl(context: Context): String =
        context.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
            .getString(SyncPrefsKeys.SERVER_URL, SERVER_URL_DEFAULT) ?: SERVER_URL_DEFAULT
}
