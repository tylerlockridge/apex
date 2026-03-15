package com.healthplatform.sync

import android.content.Context

object Config {
    const val SERVER_URL_DEFAULT = "https://tyler-health.duckdns.org"

    /** Minimum server version required for full feature compatibility. */
    const val MIN_SERVER_VERSION = "1.0.0"

    // Device sync secret is injected at build time from local.properties (gitignored).
    // Never hardcode secrets in source — add device.secret=<value> to local.properties.
    val DEVICE_SECRET: String get() = BuildConfig.DEVICE_SECRET

    // L-4: Centralized cert pin hashes — used by ApiService, ServerApiClient, and SettingsViewModel.
    // Update all three pins here when Let's Encrypt rotates intermediates.
    const val PIN_LETS_ENCRYPT_E8 = "sha256/iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU="
    const val PIN_ISRG_ROOT_X1   = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="
    const val PIN_ISRG_ROOT_X2   = "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvXFu2z8LMAs="

    /** Extracts hostname from a URL for cert pinning. */
    fun extractHost(url: String): String =
        url.removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore(":")

    /** Returns the server URL configured via QR onboarding, falling back to the default. */
    fun getServerUrl(context: Context): String =
        context.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
            .getString(SyncPrefsKeys.SERVER_URL, SERVER_URL_DEFAULT) ?: SERVER_URL_DEFAULT
}
