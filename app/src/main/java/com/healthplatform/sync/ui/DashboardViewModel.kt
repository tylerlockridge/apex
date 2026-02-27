package com.healthplatform.sync.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthplatform.sync.service.ServerApiClient
import com.healthplatform.sync.service.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DashboardState(
    val lastSyncMs: Long = 0L,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val hasAllPermissions: Boolean = false,
    val isHealthConnectAvailable: Boolean = false,
    val lastBpSystolic: Int? = null,
    val lastBpDiastolic: Int? = null,
    val lastBpTime: String? = null,
    val lastSleepDurationMin: Int? = null,
    val lastSleepDeepMin: Int? = null,
    val lastSleepRemMin: Int? = null,
    val lastSleepTime: String? = null,
    val lastWeightKg: Double? = null,
    val lastWeightTime: String? = null,
    val lastHrvMs: Double? = null,
    val lastHrvTime: String? = null,
    // Recent workout snippet from server
    val lastWorkoutTitle: String? = null,
    val lastWorkoutDate: String? = null,
)

class DashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var serverClient: ServerApiClient? = null

    fun loadFromPrefs(context: Context) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("health_sync", Context.MODE_PRIVATE)

            val bpSystolic = if (prefs.contains("last_bp_systolic")) prefs.getInt("last_bp_systolic", 0) else null
            val bpDiastolic = if (prefs.contains("last_bp_diastolic")) prefs.getInt("last_bp_diastolic", 0) else null
            val bpTime = prefs.getString("last_bp_time", null)

            val sleepDurationMin = if (prefs.contains("last_sleep_duration_min")) prefs.getInt("last_sleep_duration_min", 0) else null
            val sleepDeepMin = if (prefs.contains("last_sleep_deep_min")) prefs.getInt("last_sleep_deep_min", 0) else null
            val sleepRemMin = if (prefs.contains("last_sleep_rem_min")) prefs.getInt("last_sleep_rem_min", 0) else null
            val sleepTime = prefs.getString("last_sleep_time", null)

            val weightKg = if (prefs.contains("last_weight_kg")) prefs.getFloat("last_weight_kg", 0f).toDouble() else null
            val weightTime = prefs.getString("last_weight_time", null)

            val hrvMs = if (prefs.contains("last_hrv_ms")) prefs.getFloat("last_hrv_ms", 0f).toDouble() else null
            val hrvTime = prefs.getString("last_hrv_time", null)

            val isHealthConnectAvailable = com.healthplatform.sync.data.HealthConnectReader.isAvailable(context)

            var hasAllPermissions = false
            if (isHealthConnectAvailable) {
                try {
                    val reader = com.healthplatform.sync.data.HealthConnectReader(context)
                    hasAllPermissions = reader.hasAllPermissions()
                } catch (e: Exception) {
                    hasAllPermissions = false
                }
            }

            // Build server client with stored API key
            serverClient = ServerApiClient(prefs.getString("api_key", "") ?: "")

            _state.update { current ->
                current.copy(
                    lastSyncMs = prefs.getLong("last_sync", 0L),
                    isHealthConnectAvailable = isHealthConnectAvailable,
                    hasAllPermissions = hasAllPermissions,
                    lastBpSystolic = bpSystolic,
                    lastBpDiastolic = bpDiastolic,
                    lastBpTime = bpTime,
                    lastSleepDurationMin = sleepDurationMin,
                    lastSleepDeepMin = sleepDeepMin,
                    lastSleepRemMin = sleepRemMin,
                    lastSleepTime = sleepTime,
                    lastWeightKg = weightKg,
                    lastWeightTime = weightTime,
                    lastHrvMs = hrvMs,
                    lastHrvTime = hrvTime,
                )
            }

            // Fetch most recent workout from server for the snippet
            loadRecentWorkout()
        }
    }

    private fun loadRecentWorkout() {
        viewModelScope.launch {
            val result = serverClient?.getWorkouts(limit = 1, offset = 0) ?: return@launch
            result.fold(
                onSuccess = { list ->
                    val latest = list.firstOrNull()
                    if (latest != null) {
                        val dateLabel = try {
                            val instant = Instant.parse(latest.started_at)
                            val formatter = DateTimeFormatter.ofPattern("EEE, MMM d")
                                .withZone(ZoneId.systemDefault())
                            formatter.format(instant)
                        } catch (e: Exception) { latest.started_at.take(10) }

                        _state.update { it.copy(lastWorkoutTitle = latest.title, lastWorkoutDate = dateLabel) }
                    }
                },
                onFailure = { /* Non-critical — silently ignore */ }
            )
        }
    }

    fun triggerSync(context: Context) {
        _state.update { it.copy(isSyncing = true, syncError = null) }
        SyncWorker.runOnce(context)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _state.update { it.copy(isSyncing = false) }
            // Reload prefs after a short delay to pick up any immediate writes
            kotlinx.coroutines.delay(3000)
            loadFromPrefs(context)
        }
    }

    fun triggerBpSync(context: Context) {
        triggerSync(context)
    }

    fun triggerSleepSync(context: Context) {
        triggerSync(context)
    }
}
