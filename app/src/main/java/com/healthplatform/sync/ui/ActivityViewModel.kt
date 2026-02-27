package com.healthplatform.sync.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthplatform.sync.service.ServerApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

data class WorkoutSession(
    val id: String,
    val title: String,
    val startedAt: String,
    val durationMinutes: Int?,
    val totalVolumeKg: Double?,
    val totalSets: Int?,
    val totalReps: Int?
)

data class WorkoutStats(
    val totalWorkouts: Int,
    val avgDurationMin: Int,
    val totalVolumeKg: Double,
    val avgSetsPerWorkout: Int
)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class ActivityState(
    val workouts: List<WorkoutSession> = emptyList(),
    val stats: WorkoutStats? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class ActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ActivityState())
    val state: StateFlow<ActivityState> = _state.asStateFlow()

    private val client: ServerApiClient by lazy {
        val prefs = application.getSharedPreferences("health_sync", Context.MODE_PRIVATE)
        ServerApiClient(prefs.getString("api_key", "") ?: "")
    }

    init {
        loadAll()
    }

    fun refresh() {
        loadAll()
    }

    private fun loadAll() {
        loadWorkouts()
        loadStats()
    }

    fun loadWorkouts(limit: Int = 20, offset: Int = 0) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = client.getWorkouts(limit, offset)
            result.fold(
                onSuccess = { list ->
                    val sessions = list.map {
                        WorkoutSession(
                            id = it.id,
                            title = it.title,
                            startedAt = it.started_at,
                            durationMinutes = it.duration_minutes,
                            totalVolumeKg = it.total_volume_kg,
                            totalSets = it.total_sets,
                            totalReps = it.total_reps
                        )
                    }
                    _state.update { it.copy(workouts = sessions, isLoading = false) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not load workouts: ${e.message}"
                        )
                    }
                }
            )
        }
    }

    private fun loadStats(days: Int = 30) {
        viewModelScope.launch {
            val result = client.getWorkoutStatsSummary(days)
            result.fold(
                onSuccess = { summary ->
                    val stats = WorkoutStats(
                        totalWorkouts = summary.total_workouts,
                        avgDurationMin = summary.avg_duration ?: 0,
                        totalVolumeKg = summary.total_volume_kg ?: 0.0,
                        avgSetsPerWorkout = summary.avg_sets_per_workout ?: 0
                    )
                    _state.update { it.copy(stats = stats) }
                },
                onFailure = { /* stats are non-critical, silently ignore */ }
            )
        }
    }
}
