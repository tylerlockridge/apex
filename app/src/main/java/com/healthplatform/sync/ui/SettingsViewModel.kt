package com.healthplatform.sync.ui

import android.app.Application
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthplatform.sync.Config
import com.healthplatform.sync.data.HealthConnectReader
import com.healthplatform.sync.security.SecurePrefs
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SettingsServerState(
    /** null = check in progress, true = reachable, false = unreachable */
    val serverStatus: Boolean? = null,
    val isHcAvailable: Boolean = false,
    /** Per data-type permission status keyed by display name. */
    val hcPermissions: Map<String, Boolean> = emptyMap(),
    /** null = unknown / endpoint missing, true = version ok, false = server is outdated. */
    val serverVersionOk: Boolean? = null
)

/**
 * Holds the server ping and HC permissions check so the OkHttpClient is built once
 * per ViewModel lifetime rather than on every Settings screen open (M-4).
 * Also surfaces per-type HC permission status instead of a single all-or-nothing
 * flag (L-7).
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _serverState = MutableStateFlow(SettingsServerState())
    val serverState: StateFlow<SettingsServerState> = _serverState.asStateFlow()

    // Built lazily using the runtime server URL so cert pinning targets the correct
    // hostname even after QR onboarding to a non-default server.
    private val pingClient: OkHttpClient by lazy {
        val serverUrl = Config.getServerUrl(getApplication())
        val host = Config.extractHost(serverUrl)
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .certificatePinner(
                CertificatePinner.Builder()
                    .add(host, Config.PIN_LETS_ENCRYPT_E8)
                    .add(host, Config.PIN_ISRG_ROOT_X1)
                    .add(host, Config.PIN_ISRG_ROOT_X2)
                    .build()
            )
            .build()
    }

    init {
        checkAll()
    }

    /** Re-run all checks (called when Settings screen opens). */
    fun checkAll() {
        viewModelScope.launch {
            checkHcPermissions()
            checkServerConnection()  // M-6: single request checks both connectivity + version
        }
    }

    private suspend fun checkHcPermissions() {
        val context = getApplication<Application>()
        val isAvailable = try {
            withContext(Dispatchers.IO) { HealthConnectReader.isAvailable(context) }
        } catch (_: Exception) { false }

        val permMap: Map<String, Boolean> = if (isAvailable) {
            try {
                withContext(Dispatchers.IO) {
                    val granted = HealthConnectReader(context).checkPermissions()
                    mapOf(
                        "Blood Pressure" to (HealthPermission.getReadPermission(BloodPressureRecord::class) in granted),
                        "Sleep"          to (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted),
                        "Weight"         to (HealthPermission.getReadPermission(WeightRecord::class) in granted),
                        "Body Composition" to (
                            HealthPermission.getReadPermission(BodyFatRecord::class) in granted ||
                            HealthPermission.getReadPermission(LeanBodyMassRecord::class) in granted
                        ),
                        "HRV"            to (HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in granted)
                    )
                }
            } catch (_: Exception) { emptyMap() }
        } else emptyMap()

        _serverState.value = _serverState.value.copy(
            isHcAvailable = isAvailable,
            hcPermissions = permMap
        )
    }

    /**
     * Checks server connectivity AND version in a single request to /api/version.
     * Avoids the previous approach of hitting /api/bp?days=1 which leaked health
     * data into server access logs for what is just a connectivity check (M-6).
     */
    private suspend fun checkServerConnection() {
        val context = getApplication<Application>()
        val serverUrl = Config.getServerUrl(context)
        val (reachable, versionOk) = withContext(Dispatchers.IO) {
            try {
                val key = SecurePrefs.getApiKey(context)
                val request = Request.Builder()
                    .url("$serverUrl/api/version")
                    .addHeader("Authorization", "Bearer $key")
                    .build()
                val (body, success) = pingClient.newCall(request).execute().use { response ->
                    (response.body?.string()) to response.isSuccessful
                }
                if (!success || body == null) return@withContext true to null
                val version = JSONObject(body).getString("version")
                true to (compareVersions(version, Config.MIN_SERVER_VERSION) >= 0)
            } catch (_: Exception) { false to null }
        }
        _serverState.value = _serverState.value.copy(
            serverStatus = reachable,
            serverVersionOk = versionOk
        )
    }

    companion object {
        /** Returns negative / zero / positive like [Comparable.compareTo]. */
        private fun compareVersions(a: String, b: String): Int {
            val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
            val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
            val len = maxOf(aParts.size, bParts.size)
            for (i in 0 until len) {
                val diff = (aParts.getOrElse(i) { 0 }) - (bParts.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }
    }
}
