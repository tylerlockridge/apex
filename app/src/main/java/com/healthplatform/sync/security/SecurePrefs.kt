package com.healthplatform.sync.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    private const val FILE_NAME = "apex_secure"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    @Volatile
    private var instance: SharedPreferences? = null

    private fun get(context: Context): SharedPreferences =
        instance ?: synchronized(this) {
            // Double-checked locking: migration runs inside the lock so it is
            // guaranteed to execute exactly once before the instance is published.
            instance ?: create(context.applicationContext).also { prefs ->
                migrateIfNeeded(context.applicationContext, prefs)
                instance = prefs
            }
        }

    private fun create(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // One-time migration from plain "health_sync" prefs — runs once, inside the
    // synchronized block in get(), so there is no concurrent-migration race.
    private fun migrateIfNeeded(context: Context, secure: SharedPreferences) {
        val plain = context.getSharedPreferences("health_sync", Context.MODE_PRIVATE)
        if (plain.contains(KEY_API_KEY) && !secure.contains(KEY_API_KEY)) {
            val value = plain.getString(KEY_API_KEY, "") ?: ""
            if (value.isNotEmpty()) {
                secure.edit().putString(KEY_API_KEY, value).apply()
            }
            plain.edit().remove(KEY_API_KEY).apply()
        }
        if (plain.contains(KEY_BIOMETRIC_ENABLED) && !secure.contains(KEY_BIOMETRIC_ENABLED)) {
            secure.edit()
                .putBoolean(KEY_BIOMETRIC_ENABLED, plain.getBoolean(KEY_BIOMETRIC_ENABLED, false))
                .apply()
            plain.edit().remove(KEY_BIOMETRIC_ENABLED).apply()
        }
    }

    fun getApiKey(context: Context): String =
        get(context).getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(context: Context, key: String) {
        get(context).edit().putString(KEY_API_KEY, key).apply()
    }

    fun getBiometricEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        get(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Wipes all secure preferences (API key, biometric flag). Used by "Clear all data".
     *
     * The cached [instance] is also nulled so the next [get] call re-creates the
     * [EncryptedSharedPreferences] against the now-empty backing file, preventing
     * stale in-memory state from surviving the clear.
     */
    fun clearAll(context: Context) {
        get(context).edit().clear().commit()
        synchronized(this) { instance = null }
    }
}
