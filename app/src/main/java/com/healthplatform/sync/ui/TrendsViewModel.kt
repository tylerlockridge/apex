package com.healthplatform.sync.ui

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.healthplatform.sync.security.SecurePrefs
import com.healthplatform.sync.service.ServerApiClient
import kotlinx.coroutines.Job
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

data class HrvReading(
    val measuredAt: String,
    val hrvMs: Double
)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class TrendsState(
    val selectedTab: Int = 0,          // 0=BP, 1=Sleep, 2=Body, 3=HRV
    val selectedRange: Int = 7,        // 7, 30, or 90 days
    val bpReadings: List<BpReading> = emptyList(),
    val sleepSessions: List<SleepSession> = emptyList(),
    val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    val hrvReadings: List<HrvReading> = emptyList(),
    val isLoading: Boolean = false,
    // M-6: per-tab error fields so switching tabs doesn't show a stale error
    val bpError: String? = null,
    val sleepError: String? = null,
    val bodyError: String? = null,
    val hrvError: String? = null,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class TrendsViewModel(
    application: Application,
    // M-1: SavedStateHandle persists selectedTab across process death + navigation.
    private val savedStateHandle: SavedStateHandle,
    // Allows injecting a fake client in tests without requiring Hilt or a ViewModelFactory.
    @VisibleForTesting
    internal val clientProvider: () -> ServerApiClient
) : AndroidViewModel(application) {

    // Used by SavedStateViewModelFactory which passes (Application, SavedStateHandle).
    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        application,
        savedStateHandle,
        {
            val serverUrl = com.healthplatform.sync.Config.getServerUrl(application)
            val deviceSecret = SecurePrefs.getDeviceSecret(application, com.healthplatform.sync.Config.DEVICE_SECRET)
            ServerApiClient(SecurePrefs.getApiKey(application), serverUrl, deviceSecret)
        }
    )

    // Used by unit tests — no SavedStateHandle required.
    @VisibleForTesting
    constructor(application: Application, clientProvider: () -> ServerApiClient) : this(
        application,
        SavedStateHandle(),
        clientProvider
    )

    private val _state = MutableStateFlow(
        TrendsState(selectedTab = savedStateHandle.get<Int>("selected_tab") ?: 0)
    )
    val state: StateFlow<TrendsState> = _state.asStateFlow()

    private val client: ServerApiClient by lazy { clientProvider() }

    // Tracks the in-flight network fetch so rapid tab/range switches cancel the stale request
    // before launching a new one — prevents slower responses from overwriting newer UI state.
    private var fetchJob: Job? = null

    fun selectTab(index: Int) {
        savedStateHandle["selected_tab"] = index
        _state.update { it.copy(selectedTab = index) }
        loadDataForCurrentTab()
    }

    fun selectRange(days: Int) {
        _state.update { it.copy(selectedRange = days) }
        loadDataForCurrentTab()
    }

    fun refresh() {
        loadDataForCurrentTab()
    }

    private fun loadDataForCurrentTab() {
        val days = _state.value.selectedRange
        when (_state.value.selectedTab) {
            0 -> loadBp(days)
            1 -> loadSleep(days)
            2 -> loadBody(days)
            3 -> loadHrv(days)
        }
    }

    fun loadBp(days: Int = _state.value.selectedRange) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, bpError = null) }
            client.getBloodPressure(days).fold(
                onSuccess = { list ->
                    val readings = list
                        .sortedBy { it.measured_at }
                        .map { BpReading(it.systolic, it.diastolic, it.measured_at) }
                    _state.update { it.copy(bpReadings = readings, isLoading = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, bpError = e.toFriendlyMessage()) }
                }
            )
        }
    }

    fun loadSleep(days: Int = _state.value.selectedRange) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, sleepError = null) }
            client.getSleep(days).fold(
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
                    _state.update { it.copy(isLoading = false, sleepError = e.toFriendlyMessage()) }
                }
            )
        }
    }

    fun loadBody(days: Int = _state.value.selectedRange) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, bodyError = null) }
            client.getBodyMeasurements(days).fold(
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
                    _state.update { it.copy(isLoading = false, bodyError = e.toFriendlyMessage()) }
                }
            )
        }
    }

    fun loadHrv(days: Int = _state.value.selectedRange) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, hrvError = null) }
            client.getHrv(days).fold(
                onSuccess = { list ->
                    val readings = list
                        .sortedBy { it.measured_at }
                        .map { HrvReading(it.measured_at, it.hrv_ms) }
                    _state.update { it.copy(hrvReadings = readings, isLoading = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, hrvError = e.toFriendlyMessage()) }
                }
            )
        }
    }

    init {
        loadDataForCurrentTab()
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
