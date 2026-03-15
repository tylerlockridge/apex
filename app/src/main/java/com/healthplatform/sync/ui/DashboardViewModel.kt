package com.healthplatform.sync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.healthplatform.sync.SyncPrefsKeys
import com.healthplatform.sync.security.SecurePrefs
import com.healthplatform.sync.service.ServerApiClient
import com.healthplatform.sync.service.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DashboardState(
    val lastSyncMs: Long = 0L,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val isLoadingPrefs: Boolean = false,
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
    val lastWorkoutTitle: String? = null,
    val lastWorkoutDate: String? = null,
    // Readiness score derived from BP + sleep + HRV (null until data is available)
    val readinessLabel: String? = null,
    val readinessReason: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val workManager = WorkManager.getInstance(application)
    private var serverClient: ServerApiClient? = null

    init {
        loadFromPrefs()
    }

    fun loadFromPrefs() {
        val context = getApplication<Application>()
        _state.update { it.copy(isLoadingPrefs = true) }
        viewModelScope.launch {
            val prefs = context.getSharedPreferences(SyncPrefsKeys.FILE_NAME, android.content.Context.MODE_PRIVATE)

            val bpSystolic  = if (prefs.contains(SyncPrefsKeys.LAST_BP_SYSTOLIC)) prefs.getInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 0) else null
            val bpDiastolic = if (prefs.contains(SyncPrefsKeys.LAST_BP_DIASTOLIC)) prefs.getInt(SyncPrefsKeys.LAST_BP_DIASTOLIC, 0) else null
            val bpTime      = prefs.getString(SyncPrefsKeys.LAST_BP_TIME, null)

            val sleepDurationMin = if (prefs.contains(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN)) prefs.getInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 0) else null
            val sleepDeepMin     = if (prefs.contains(SyncPrefsKeys.LAST_SLEEP_DEEP_MIN)) prefs.getInt(SyncPrefsKeys.LAST_SLEEP_DEEP_MIN, 0) else null
            val sleepRemMin      = if (prefs.contains(SyncPrefsKeys.LAST_SLEEP_REM_MIN)) prefs.getInt(SyncPrefsKeys.LAST_SLEEP_REM_MIN, 0) else null
            val sleepTime        = prefs.getString(SyncPrefsKeys.LAST_SLEEP_TIME, null)

            val weightKg   = if (prefs.contains(SyncPrefsKeys.LAST_WEIGHT_KG)) prefs.getFloat(SyncPrefsKeys.LAST_WEIGHT_KG, 0f).toDouble() else null
            val weightTime = prefs.getString(SyncPrefsKeys.LAST_WEIGHT_TIME, null)

            val hrvMs   = if (prefs.contains(SyncPrefsKeys.LAST_HRV_MS)) prefs.getFloat(SyncPrefsKeys.LAST_HRV_MS, 0f).toDouble() else null
            val hrvTime = prefs.getString(SyncPrefsKeys.LAST_HRV_TIME, null)

            // H-5: HC availability/permissions checks are suspend operations that hit the
            // Android Keystore and HC client — must run off the Main thread.
            val isHealthConnectAvailable = try {
                withContext(Dispatchers.IO) {
                    com.healthplatform.sync.data.HealthConnectReader.isAvailable(context)
                }
            } catch (_: Exception) { false }

            var hasAllPermissions = false
            if (isHealthConnectAvailable) {
                try {
                    hasAllPermissions = withContext(Dispatchers.IO) {
                        com.healthplatform.sync.data.HealthConnectReader(context).hasAllPermissions()
                    }
                } catch (_: Exception) {}
            }

            // SecurePrefs uses EncryptedSharedPreferences (Android Keystore) which is
            // unavailable in unit test environments. Null serverClient means loadRecentWorkout()
            // no-ops rather than crashing; health-metric state still loads from regular prefs.
            serverClient = try {
                val serverUrl = com.healthplatform.sync.Config.getServerUrl(context)
                val deviceSecret = SecurePrefs.getDeviceSecret(context, com.healthplatform.sync.Config.DEVICE_SECRET)
                ServerApiClient(SecurePrefs.getApiKey(context), serverUrl, deviceSecret)
            } catch (_: Exception) { null }

            val (readinessLabel, readinessReason) = computeReadiness(bpSystolic, sleepDurationMin, hrvMs)

            _state.update { current ->
                current.copy(
                    lastSyncMs               = prefs.getLong(SyncPrefsKeys.LAST_SYNC, 0L),
                    isHealthConnectAvailable = isHealthConnectAvailable,
                    hasAllPermissions        = hasAllPermissions,
                    isLoadingPrefs           = false,
                    lastBpSystolic           = bpSystolic,
                    lastBpDiastolic          = bpDiastolic,
                    lastBpTime               = bpTime,
                    lastSleepDurationMin     = sleepDurationMin,
                    lastSleepDeepMin         = sleepDeepMin,
                    lastSleepRemMin          = sleepRemMin,
                    lastSleepTime            = sleepTime,
                    lastWeightKg             = weightKg,
                    lastWeightTime           = weightTime,
                    lastHrvMs                = hrvMs,
                    lastHrvTime              = hrvTime,
                    readinessLabel           = readinessLabel,
                    readinessReason          = readinessReason,
                )
            }

            loadRecentWorkout()
        }
    }

    private fun loadRecentWorkout() {
        viewModelScope.launch {
            val result = serverClient?.getWorkouts(limit = 1, offset = 0) ?: return@launch
            result.fold(
                onSuccess = { list ->
                    val latest = list.firstOrNull() ?: return@fold
                    val dateLabel = try {
                        val instant = Instant.parse(latest.started_at)
                        DateTimeFormatter.ofPattern("EEE, MMM d")
                            .withZone(ZoneId.systemDefault())
                            .format(instant)
                    } catch (_: Exception) { latest.started_at.take(10) }
                    _state.update { it.copy(lastWorkoutTitle = latest.title, lastWorkoutDate = dateLabel) }
                },
                onFailure = { /* Non-critical — silently ignore */ }
            )
        }
    }

    /**
     * Derives a readiness label from the most recent BP, sleep, and HRV values.
     * Uses fixed population thresholds — no server call required.
     * Returns (label, reason) where reason summarises the limiting factor(s).
     */
    private fun computeReadiness(
        systolic: Int?,
        sleepMin: Int?,
        hrvMs: Double?
    ): Pair<String?, String?> {
        if (listOfNotNull(systolic, sleepMin, hrvMs).isEmpty()) return null to null

        var score = 0
        val concerns = mutableListOf<String>()

        systolic?.let {
            when {
                it < 120 -> score += 2
                it < 130 -> score += 1
                it < 140 -> concerns.add("BP elevated")
                else     -> { score -= 1; concerns.add("BP high ($it)") }
            }
        }

        sleepMin?.let {
            when {
                it >= 420 -> score += 2                          // 7+ h
                it >= 360 -> { score += 1; concerns.add("Under 7h sleep") }
                else      -> { score -= 1; concerns.add("Low sleep (${it / 60}h)") }
            }
        }

        hrvMs?.let {
            when {
                it >= 60 -> score += 2
                it >= 30 -> score += 1
                else     -> { score -= 1; concerns.add("Low HRV (${it.toInt()} ms)") }
            }
        }

        val label = when {
            score >= 4 -> "Good to go"
            score >= 2 -> "Take it easy"
            else       -> "Recovery day"
        }
        val reason = if (concerns.isEmpty()) "All metrics looking good" else concerns.joinToString(" · ")
        return label to reason
    }

    /**
     * Triggers a WorkManager sync and observes it to completion, then refreshes
     * SharedPreferences state. Debounced — no-ops if a sync is already in flight.
     * Also skips if the periodic worker is currently running to prevent concurrent
     * uploads of the same queued records.
     *
     * @param dataTypeFilter one of [SyncWorker.DATA_TYPE_BP], [SyncWorker.DATA_TYPE_SLEEP],
     *   [SyncWorker.DATA_TYPE_BODY], [SyncWorker.DATA_TYPE_HRV], or null to sync all.
     */
    fun triggerSync(dataTypeFilter: String? = null) {
        if (_state.value.isSyncing) return
        // Set synchronously so the debounce guard is effective on immediate re-calls.
        _state.update { it.copy(isSyncing = true, syncError = null) }

        viewModelScope.launch {
            // Avoid double-upload if the 15-min periodic worker is already running.
            val periodicRunning = workManager
                .getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
                .first()
                .any { it.state == WorkInfo.State.RUNNING }
            if (periodicRunning) {
                _state.update { it.copy(isSyncing = false) }
                return@launch
            }

            val workId = SyncWorker.runOnce(getApplication(), dataTypeFilter)
            workManager.getWorkInfoByIdFlow(workId)
                .filter { info -> info?.state?.isFinished == true }
                .take(1)
                .collect {
                    _state.update { it.copy(isSyncing = false) }
                    loadFromPrefs()
                }
        }
    }

    fun triggerBpSync()    = triggerSync(SyncWorker.DATA_TYPE_BP)
    fun triggerSleepSync() = triggerSync(SyncWorker.DATA_TYPE_SLEEP)
}
