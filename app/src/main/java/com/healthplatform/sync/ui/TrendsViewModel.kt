package com.healthplatform.sync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthplatform.sync.security.SecurePrefs
import com.healthplatform.sync.service.ServerApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

data class BpReading(
    val systolic: Int,
    val diastolic: Int,
    val measuredAt: String
)

data class SleepSession(
    val sleepStart: String,
    val sleepEnd: String?,
    val durationMinutes: Int,
    val deepSleepMinutes: Int?,
    val remSleepMinutes: Int?,
    val lightSleepMinutes: Int?
)

data class BodyMeasurement(
    val measuredAt: String,
    val weightKg: Double?,
    val bodyFatPercent: Double?
)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class TrendsState(
    val selectedTab: Int = 0,          // 0=BP, 1=Sleep, 2=Body
    val selectedRange: Int = 7,        // 7, 30, or 90 days
    val bpReadings: List<BpReading> = emptyList(),
    val sleepSessions: List<SleepSession> = emptyList(),
    val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class TrendsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TrendsState())
    val state: StateFlow<TrendsState> = _state.asStateFlow()

    private val client: ServerApiClient by lazy {
        ServerApiClient(SecurePrefs.getApiKey(application))
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(selectedTab = index, error = null) }
        loadDataForCurrentTab()
    }

    fun selectRange(days: Int) {
        _state.update { it.copy(selectedRange = days, error = null) }
        loadDataForCurrentTab()
    }

    fun refresh() {
        loadDataForCurrentTab()
    }

    private fun loadDataForCurrentTab() {
        val currentTab = _state.value.selectedTab
        val days = _state.value.selectedRange
        when (currentTab) {
            0 -> loadBp(days)
            1 -> loadSleep(days)
            2 -> loadBody(days)
        }
    }

    fun loadBp(days: Int = _state.value.selectedRange) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = client.getBloodPressure(days)
            result.fold(
                onSuccess = { list ->
                    val readings = list
                        .sortedBy { it.measured_at }
                        .map { BpReading(it.systolic, it.diastolic, it.measured_at) }
                    _state.update { it.copy(bpReadings = readings, isLoading = false) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toFriendlyMessage())
                    }
                }
            )
        }
    }

    fun loadSleep(days: Int = _state.value.selectedRange) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = client.getSleep(days)
            result.fold(
                onSuccess = { list ->
                    val sessions = list
                        .sortedBy { it.sleep_start }
                        .map {
                            SleepSession(
                                sleepStart = it.sleep_start,
                                sleepEnd = it.sleep_end,
                                durationMinutes = it.duration_minutes,
                                deepSleepMinutes = it.deep_sleep_minutes,
                                remSleepMinutes = it.rem_sleep_minutes,
                                lightSleepMinutes = it.light_sleep_minutes
                            )
                        }
                    _state.update { it.copy(sleepSessions = sessions, isLoading = false) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toFriendlyMessage())
                    }
                }
            )
        }
    }

    fun loadBody(days: Int = _state.value.selectedRange) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = client.getBodyMeasurements(days)
            result.fold(
                onSuccess = { list ->
                    val measurements = list
                        .sortedBy { it.measured_at }
                        .map {
                            BodyMeasurement(
                                measuredAt = it.measured_at,
                                weightKg = it.weight_kg,
                                bodyFatPercent = it.body_fat_percent
                            )
                        }
                    _state.update { it.copy(bodyMeasurements = measurements, isLoading = false) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toFriendlyMessage())
                    }
                }
            )
        }
    }

    init {
        loadBp()
    }
}

// Shared across ViewModels in this package
internal fun Throwable.toFriendlyMessage(): String = when {
    this is java.net.SocketTimeoutException -> "Server unreachable — check your connection"
    this is java.net.ConnectException -> "Cannot connect to server"
    this is java.net.UnknownHostException -> "No network connection"
    this is HttpException && code() == 401 -> "Not authorized — check your API key"
    this is HttpException -> "Server error (${code()})"
    else -> "Server unreachable — try again"
}
