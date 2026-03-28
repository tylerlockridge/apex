package com.healthplatform.sync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthplatform.sync.data.NutritionRepository
import com.healthplatform.sync.data.db.HydrationTargetCacheEntity
import com.healthplatform.sync.data.db.WaterEntryCacheEntity
import com.healthplatform.sync.service.NutritionSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class HydrationState(
    val isLoading: Boolean = true,
    val date: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val entries: List<WaterEntryCacheEntity> = emptyList(),
    val target: HydrationTargetCacheEntity? = null,
    val totalMl: Int = 0,
    val error: String? = null,
)

class HydrationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NutritionRepository(application)
    private val _state = MutableStateFlow(HydrationState())
    val state: StateFlow<HydrationState> = _state

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val date = _state.value.date
                val entries = repo.getWaterEntriesForDate(date)
                val target = repo.getHydrationTarget(date)
                _state.update {
                    it.copy(
                        isLoading = false,
                        entries = entries,
                        target = target,
                        totalMl = entries.sumOf { e -> e.amountMl },
                    )
                }
                // Background refresh
                repo.refreshWaterEntriesFromServer(date)
                repo.refreshHydrationTarget(date)
                val refreshed = repo.getWaterEntriesForDate(date)
                val refreshedTarget = repo.getHydrationTarget(date)
                _state.update {
                    it.copy(
                        entries = refreshed,
                        target = refreshedTarget,
                        totalMl = refreshed.sumOf { e -> e.amountMl },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun quickAdd(amountMl: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.createWaterEntry(amountMl)
            NutritionSyncWorker.runOnce(getApplication())
            loadAll()
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteWaterEntry(id)
            NutritionSyncWorker.runOnce(getApplication())
            loadAll()
        }
    }
}
