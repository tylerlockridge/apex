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
        val host = serverUrl
            .removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore(":")
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .certificatePinner(
                CertificatePinner.Builder()
                    .add(host, "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1
                    .add(host, "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvXFu2z8LMAs=") // ISRG Root X2
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
            checkServerConnection()
            checkServerVersion()
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

    private suspend fun checkServerConnection() {
        val context = getApplication<Application>()
        val serverUrl = Config.getServerUrl(context)
        val status = withContext(Dispatchers.IO) {
            try {
                val key = SecurePrefs.getApiKey(context)
                val request = Request.Builder()
                    .url("$serverUrl/api/bp?days=1")
                    .addHeader("Authorization", "Bearer $key")
                    .build()
                pingClient.newCall(request).execute().use { it.isSuccessful }
            } catch (_: Exception) { false }
        }
        _serverState.value = _serverState.value.copy(serverStatus = status)
    }

    private suspend fun checkServerVersion() {
        val context = getApplication<Application>()
        val serverUrl = Config.getServerUrl(context)
        val versionOk = withContext(Dispatchers.IO) {
            try {
                val key = SecurePrefs.getApiKey(context)
                val request = Request.Builder()
                    .url("$serverUrl/api/version")
                    .addHeader("Authorization", "Bearer $key")
                    .build()
                val body = pingClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.string()
                } ?: return@withContext null
                val version = JSONObject(body).getString("version")
                compareVersions(version, Config.MIN_SERVER_VERSION) >= 0
            } catch (_: Exception) { null }  // endpoint absent or unreachable — skip banner
        }
        _serverState.value = _serverState.value.copy(serverVersionOk = versionOk)
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
