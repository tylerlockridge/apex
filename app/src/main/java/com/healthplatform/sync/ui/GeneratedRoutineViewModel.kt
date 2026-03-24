package com.healthplatform.sync.ui

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthplatform.sync.readiness.ReadinessPayloadBuilder
import com.healthplatform.sync.security.SecurePrefs
import com.healthplatform.sync.service.GenerateRoutineRequest
import com.healthplatform.sync.service.GeneratedRoutineResponse
import com.healthplatform.sync.service.ReadinessPayloadRequest
import com.healthplatform.sync.service.ServerApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GeneratedRoutineState(
    val routine: GeneratedRoutineResponse? = null,
    val isGenerating: Boolean = false,
    val isDeciding: Boolean = false,
    val error: String? = null,
    val decisionMade: String? = null,
)

class GeneratedRoutineViewModel(
    application: Application,
    @VisibleForTesting
    internal val clientProvider: () -> ServerApiClient
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, {
        val serverUrl = com.healthplatform.sync.Config.getServerUrl(application)
        val deviceSecret = SecurePrefs.getDeviceSecret(application, com.healthplatform.sync.Config.DEVICE_SECRET)
        ServerApiClient(SecurePrefs.getApiKey(application), serverUrl, deviceSecret)
    })

    private val _state = MutableStateFlow(GeneratedRoutineState())
    val state: StateFlow<GeneratedRoutineState> = _state.asStateFlow()

    private val client: ServerApiClient by lazy { clientProvider() }

    fun generateRoutine(
        sessionType: String,
        targetMuscleGroups: List<String>,
        durationMinutes: Int = 60,
        readiness: ReadinessPayloadRequest? = null
    ) {
        if (_state.value.isGenerating) return
        _state.update { it.copy(isGenerating = true, error = null, routine = null, decisionMade = null) }

        viewModelScope.launch {
            val request = GenerateRoutineRequest(
                sessionType = sessionType,
                targetMuscleGroups = targetMuscleGroups,
                durationMinutes = durationMinutes,
                readiness = readiness
            )
            client.generateRoutine(request).fold(
                onSuccess = { routine ->
                    _state.update { it.copy(isGenerating = false, routine = routine) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isGenerating = false, error = e.toFriendlyMessage()) }
                }
            )
        }
    }

    fun loadRoutine(routineId: String) {
        _state.update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch {
            client.getGeneratedRoutine(routineId).fold(
                onSuccess = { routine ->
                    _state.update { it.copy(isGenerating = false, routine = routine) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isGenerating = false, error = e.toFriendlyMessage()) }
                }
            )
        }
    }

    /**
     * Entry point called by the screen on first composition.
     * Fetches training-load score from the server (same as DashboardViewModel),
     * builds a readiness payload via [ReadinessPayloadBuilder], and triggers
     * generation. Training load is included when progression data exists and
     * excluded only when genuinely unavailable.
     */
    fun generateOnEntry(
        sessionType: String = "push",
        targetMuscleGroups: List<String> = listOf("chest", "front_delts", "triceps"),
        durationMinutes: Int = 60
    ) {
        if (_state.value.isGenerating || _state.value.routine != null) return
        _state.update { it.copy(isGenerating = true, error = null, routine = null, decisionMade = null) }

        viewModelScope.launch {
            val context = getApplication<Application>()

            // Fetch training load from server — same call DashboardViewModel makes
            val trainingLoadScore = ReadinessPayloadBuilder.fetchTrainingLoadScore(client)

            val readinessPayload = ReadinessPayloadBuilder.build(context, trainingLoadScore)

            val request = GenerateRoutineRequest(
                sessionType = sessionType,
                targetMuscleGroups = targetMuscleGroups,
                durationMinutes = durationMinutes,
                readiness = readinessPayload
            )
            client.generateRoutine(request).fold(
                onSuccess = { routine ->
                    _state.update { it.copy(isGenerating = false, routine = routine) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isGenerating = false, error = e.toFriendlyMessage()) }
                }
            )
        }
    }

    fun acceptRoutine() = decide("accepted")
    fun rejectRoutine() = decide("rejected")

    private fun decide(decision: String) {
        val routineId = _state.value.routine?.id ?: return
        if (_state.value.isDeciding) return
        _state.update { it.copy(isDeciding = true) }

        viewModelScope.launch {
            client.decideGeneratedRoutine(routineId, decision).fold(
                onSuccess = { result ->
                    _state.update { it.copy(
                        isDeciding = false,
                        decisionMade = result.status,
                        routine = it.routine?.copy(status = result.status)
                    ) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isDeciding = false, error = e.toFriendlyMessage()) }
                }
            )
        }
    }
}
