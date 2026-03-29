package com.healthplatform.sync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthplatform.sync.data.NutritionRepository
import com.healthplatform.sync.data.db.FoodCacheEntity
import com.healthplatform.sync.data.db.FoodEntryCacheEntity
import com.healthplatform.sync.data.db.NutritionTargetCacheEntity
import com.healthplatform.sync.service.CreateFoodRequest
import com.healthplatform.sync.service.NutritionSyncWorker
import com.healthplatform.sync.service.PatchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class NutritionState(
    val isLoading: Boolean = true,
    val date: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val entries: List<FoodEntryCacheEntity> = emptyList(),
    val target: NutritionTargetCacheEntity? = null,
    val totalCalories: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFat: Double = 0.0,
    val searchQuery: String = "",
    val searchResults: List<FoodCacheEntity> = emptyList(),
    val isSearching: Boolean = false,
    val showAddEntrySheet: Boolean = false,
    val showCreateFoodDialog: Boolean = false,
    val selectedFood: FoodCacheEntity? = null,
    val editingEntry: FoodEntryCacheEntity? = null,
    val pendingWriteCount: Int = 0,
    val error: String? = null,
)

class NutritionViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NutritionRepository(application)
    private val _state = MutableStateFlow(NutritionState())
    val state: StateFlow<NutritionState> = _state

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val date = _state.value.date
                val entries = repo.getFoodEntriesForDate(date)
                val target = repo.getNutritionTarget(date)
                val pending = repo.getPendingWriteCount()
                _state.update {
                    it.copy(
                        isLoading = false,
                        entries = entries,
                        target = target,
                        totalCalories = entries.sumOf { e -> e.calories },
                        totalProtein = entries.sumOf { e -> e.proteinG ?: 0.0 },
                        totalCarbs = entries.sumOf { e -> e.carbsG ?: 0.0 },
                        totalFat = entries.sumOf { e -> e.fatG ?: 0.0 },
                        pendingWriteCount = pending,
                    )
                }
                // Background refresh from server
                repo.refreshFoodEntriesFromServer(date)
                repo.refreshNutritionTarget(date)
                val refreshedEntries = repo.getFoodEntriesForDate(date)
                val refreshedTarget = repo.getNutritionTarget(date)
                _state.update {
                    it.copy(
                        entries = refreshedEntries,
                        target = refreshedTarget,
                        totalCalories = refreshedEntries.sumOf { e -> e.calories },
                        totalProtein = refreshedEntries.sumOf { e -> e.proteinG ?: 0.0 },
                        totalCarbs = refreshedEntries.sumOf { e -> e.carbsG ?: 0.0 },
                        totalFat = refreshedEntries.sumOf { e -> e.fatG ?: 0.0 },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun searchFoods(query: String) {
        _state.update { it.copy(searchQuery = query, isSearching = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = repo.searchFoods(query)
                _state.update { it.copy(searchResults = results, isSearching = false) }
                // Background server search
                if (query.length >= 2) {
                    repo.refreshFoodsFromServer(query)
                    val refreshed = repo.searchFoods(query)
                    _state.update { it.copy(searchResults = refreshed) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    fun showAddEntry() {
        _state.update { it.copy(showAddEntrySheet = true, searchQuery = "", searchResults = emptyList()) }
        searchFoods("")
    }

    fun hideAddEntry() {
        _state.update { it.copy(showAddEntrySheet = false, selectedFood = null) }
    }

    fun selectFood(food: FoodCacheEntity) {
        _state.update { it.copy(selectedFood = food) }
    }

    fun addFoodEntry(food: FoodCacheEntity, servings: Double, mealType: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            // Re-read the food from the DB to pick up any ID remapping that occurred
            // when the sync worker replaced the local UUID with the server-assigned ID.
            // After sync, the local UUID entity is deleted and replaced with the
            // server-ID entity, so fall back to a name search if the ID is gone.
            val freshFood = repo.getFoodById(food.id)
                ?: repo.searchFoods(food.name).firstOrNull { it.name == food.name }
                ?: food
            repo.createFoodEntry(freshFood, servings, mealType)
            NutritionSyncWorker.runOnce(getApplication())
            _state.update { it.copy(showAddEntrySheet = false, selectedFood = null) }
            loadAll()
        }
    }

    fun startEditEntry(entry: FoodEntryCacheEntity) {
        _state.update { it.copy(editingEntry = entry) }
    }

    fun cancelEditEntry() {
        _state.update { it.copy(editingEntry = null) }
    }

    fun saveEditEntry(servings: Double, mealType: PatchField<String>) {
        val entry = _state.value.editingEntry ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Re-read the entry from DB to pick up any ID remapping that occurred
            // when the sync worker replaced the local UUID with the server-assigned ID.
            val freshEntry = repo.getFoodEntryById(entry.id) ?: run {
                // ID was remapped — search by food name + logged date as fallback
                val todayEntries = repo.getFoodEntriesForDate(entry.loggedDate)
                todayEntries.firstOrNull { it.foodName == entry.foodName && it.loggedAt == entry.loggedAt }
            }
            val targetId = freshEntry?.id ?: entry.id
            val baseServings = freshEntry?.servings ?: entry.servings
            repo.updateFoodEntry(
                id = targetId,
                servings = if (servings != baseServings) servings else null,
                mealType = mealType,
            )
            NutritionSyncWorker.runOnce(getApplication())
            _state.update { it.copy(editingEntry = null) }
            loadAll()
        }
    }

    fun deleteFoodEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteFoodEntry(id)
            NutritionSyncWorker.runOnce(getApplication())
            loadAll()
        }
    }

    fun showCreateFoodDialog() {
        _state.update { it.copy(showCreateFoodDialog = true) }
    }

    fun hideCreateFoodDialog() {
        _state.update { it.copy(showCreateFoodDialog = false) }
    }

    fun createCustomFood(name: String, calories: Double, proteinG: Double?, carbsG: Double?, fatG: Double?, brand: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val food = repo.createCustomFood(
                CreateFoodRequest(
                    name = name,
                    brand = brand,
                    calories = calories,
                    protein_g = proteinG,
                    carbs_g = carbsG,
                    fat_g = fatG,
                )
            )
            NutritionSyncWorker.runOnce(getApplication())
            _state.update { it.copy(showCreateFoodDialog = false, selectedFood = food) }
            // Refresh search to show new food
            searchFoods(_state.value.searchQuery)
        }
    }
}
