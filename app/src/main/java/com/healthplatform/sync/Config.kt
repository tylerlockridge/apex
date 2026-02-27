package com.healthplatform.sync

object Config {
    const val SERVER_URL = "https://tyler-health.duckdns.org"

    // Device sync secret is injected at build time from local.properties (gitignored).
    // Never hardcode secrets in source — add device.secret=<value> to local.properties.
    val DEVICE_SECRET: String get() = BuildConfig.DEVICE_SECRET
}
