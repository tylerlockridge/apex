package com.healthplatform.sync.data

import android.content.Context
import com.google.gson.Gson
import com.healthplatform.sync.Config
import com.healthplatform.sync.data.db.*
import com.healthplatform.sync.security.SecurePrefs
import com.healthplatform.sync.service.*
import com.healthplatform.sync.service.PatchField
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Offline-first repository for nutrition and hydration data.
 *
 * Write path: optimistic local insert → enqueue pending write → background worker drains to server.
 * Read path: return local cache → refresh from server in background → update cache.
 */
class NutritionRepository(private val context: Context) {

    private val dao: NutritionCacheDao by lazy { ApexDatabase.get(context).nutritionCacheDao() }
    private val gson = Gson()

    private fun apiClient(): ServerApiClient? = try {
        val serverUrl = Config.getServerUrl(context)
        val deviceSecret = SecurePrefs.getDeviceSecret(context, Config.DEVICE_SECRET)
        ServerApiClient(SecurePrefs.getApiKey(context), serverUrl, deviceSecret)
    } catch (_: Exception) { null }

    private fun todayString(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    // -----------------------------------------------------------------------
    // Foods
    // -----------------------------------------------------------------------

    suspend fun searchFoods(query: String, limit: Int = 30): List<FoodCacheEntity> {
        return if (query.isBlank()) {
            dao.recentFoods(limit)
        } else {
            dao.searchFoods(query, limit)
        }
    }

    suspend fun refreshFoodsFromServer(query: String, limit: Int = 20) {
        val api = apiClient() ?: return
        val result = api.searchFoods(q = query.ifBlank { null }, limit = limit)
        result.getOrNull()?.foods?.map { it.toCache() }?.let { foods ->
            dao.upsertFoods(foods)
        }
    }

    suspend fun getFoodById(id: String): FoodCacheEntity? = dao.getFoodById(id)

    suspend fun createCustomFood(request: CreateFoodRequest): FoodCacheEntity {
        val localId = UUID.randomUUID().toString()
        val entity = FoodCacheEntity(
            id = localId,
            name = request.name,
            brand = request.brand,
            barcode = request.barcode,
            servingSizeG = request.serving_size_g,
            calories = request.calories,
            proteinG = request.protein_g,
            carbsG = request.carbs_g,
            fatG = request.fat_g,
            fiberG = request.fiber_g,
            sugarG = request.sugar_g,
            sodiumMg = request.sodium_mg,
            dataSource = "custom",
            qualityFlag = "user_created",
            isCustom = true,
            syncState = NutritionSyncState.PENDING_CREATE,
            lastUsedAt = System.currentTimeMillis(),
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString(),
        )
        dao.upsertFood(entity)
        enqueuePendingWrite(
            NutritionQueueAction.CREATE_FOOD,
            "create_food:$localId",
            gson.toJson(mapOf("localId" to localId, "request" to request)),
        )
        return entity
    }

    // -----------------------------------------------------------------------
    // Food Entries
    // -----------------------------------------------------------------------

    suspend fun getFoodEntryById(id: String): FoodEntryCacheEntity? = dao.getFoodEntryById(id)

    suspend fun getFoodEntriesForDate(date: String = todayString()): List<FoodEntryCacheEntity> {
        return dao.getFoodEntriesForDate(date)
    }

    suspend fun refreshFoodEntriesFromServer(date: String = todayString()) {
        val api = apiClient() ?: return
        val result = api.getFoodEntries(date)
        result.getOrNull()?.entries?.let { entries ->
            // Filter out server entries whose IDs match locally-pending edits/deletes,
            // so the server refresh doesn't overwrite uncommitted local changes.
            val pendingIds = dao.getFoodEntriesForDate(date)
                .filter { it.syncState != NutritionSyncState.SYNCED }
                .map { it.id }
                .toSet()
            val cached = entries.map { it.toCache(date) }
                .filter { it.id !in pendingIds }
            dao.replaceSyncedFoodEntriesForDate(date, cached)
        }
    }

    suspend fun createFoodEntry(
        food: FoodCacheEntity,
        servings: Double = 1.0,
        mealType: String? = null,
        notes: String? = null,
    ): FoodEntryCacheEntity {
        val localId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val date = todayString()
        val entry = FoodEntryCacheEntity(
            id = localId,
            foodId = food.id,
            foodName = food.name,
            entrySource = "manual",
            mealType = mealType,
            servings = servings,
            calories = (food.calories) * servings,
            proteinG = food.proteinG?.let { it * servings },
            carbsG = food.carbsG?.let { it * servings },
            fatG = food.fatG?.let { it * servings },
            fiberG = food.fiberG?.let { it * servings },
            sugarG = food.sugarG?.let { it * servings },
            sodiumMg = food.sodiumMg?.let { it * servings },
            loggedAt = now,
            loggedDate = date,
            notes = notes,
            syncState = NutritionSyncState.PENDING_CREATE,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertFoodEntry(entry)
        dao.updateFoodLastUsed(food.id)
        enqueuePendingWrite(
            NutritionQueueAction.CREATE_FOOD_ENTRY,
            "create_food_entry:$localId",
            gson.toJson(mapOf(
                "localId" to localId,
                "food_id" to food.id,
                "servings" to servings,
                "meal_type" to mealType,
                "logged_at" to now,
                "notes" to notes,
                "entry_source" to "manual",
            )),
        )
        return entry
    }

    /**
     * Update a food entry. Uses tri-state for nullable fields:
     * - [PatchField.Unchanged] — leave the field as-is locally and omit from server PATCH
     * - [PatchField.SetNull] — clear the field locally and send explicit null to server
     * - [PatchField.SetValue] — set to a new value
     */
    suspend fun updateFoodEntry(
        id: String,
        servings: Double? = null,
        mealType: PatchField<String> = PatchField.Unchanged,
        notes: PatchField<String> = PatchField.Unchanged,
    ) {
        val existing = dao.getFoodEntryById(id) ?: return
        val newServings = servings ?: existing.servings
        val factor = if (servings != null) newServings / existing.servings else 1.0
        val resolvedMealType = when (mealType) {
            is PatchField.Unchanged -> existing.mealType
            is PatchField.SetNull -> null
            is PatchField.SetValue -> mealType.value
        }
        val resolvedNotes = when (notes) {
            is PatchField.Unchanged -> existing.notes
            is PatchField.SetNull -> null
            is PatchField.SetValue -> notes.value
        }
        val updated = existing.copy(
            servings = newServings,
            calories = existing.calories * factor,
            proteinG = existing.proteinG?.let { it * factor },
            carbsG = existing.carbsG?.let { it * factor },
            fatG = existing.fatG?.let { it * factor },
            fiberG = existing.fiberG?.let { it * factor },
            sugarG = existing.sugarG?.let { it * factor },
            sodiumMg = existing.sodiumMg?.let { it * factor },
            mealType = resolvedMealType,
            notes = resolvedNotes,
            syncState = if (existing.syncState == NutritionSyncState.PENDING_CREATE)
                NutritionSyncState.PENDING_CREATE else NutritionSyncState.PENDING_UPDATE,
            updatedAt = Instant.now().toString(),
        )
        dao.upsertFoodEntry(updated)
        if (existing.syncState == NutritionSyncState.PENDING_CREATE) {
            // The entry hasn't been synced yet — update the queued CREATE payload
            // so the sync worker sends the edited values, not the originals.
            val createKey = "create_food_entry:$id"
            val pendingCreate = dao.getPendingWriteByDedupeKey(createKey)
            if (pendingCreate != null) {
                @Suppress("UNCHECKED_CAST")
                val createMap = (gson.fromJson(pendingCreate.payload,
                    Map::class.java) as MutableMap<String, Any?>).toMutableMap()
                createMap["servings"] = updated.servings
                createMap["meal_type"] = updated.mealType
                createMap["notes"] = updated.notes
                dao.upsertPendingWrite(pendingCreate.copy(payload = gson.toJson(createMap)))
            }
        } else {
            // Encode tri-state: missing key = unchanged, "__null__" = clear, value = set
            val patchMap = mutableMapOf<String, Any?>("id" to id)
            if (servings != null) patchMap["servings"] = servings
            when (mealType) {
                is PatchField.Unchanged -> {}
                is PatchField.SetNull -> patchMap["meal_type"] = SENTINEL_NULL
                is PatchField.SetValue -> patchMap["meal_type"] = mealType.value
            }
            when (notes) {
                is PatchField.Unchanged -> {}
                is PatchField.SetNull -> patchMap["notes"] = SENTINEL_NULL
                is PatchField.SetValue -> patchMap["notes"] = notes.value
            }
            enqueuePendingWrite(
                NutritionQueueAction.UPDATE_FOOD_ENTRY,
                "update_food_entry:$id",
                gson.toJson(patchMap),
            )
        }
    }

    suspend fun deleteFoodEntry(id: String) {
        val existing = dao.getFoodEntryById(id) ?: return
        if (existing.syncState == NutritionSyncState.PENDING_CREATE) {
            dao.deleteFoodEntryById(id)
            dao.deletePendingWriteByDedupeKey("create_food_entry:$id")
        } else {
            dao.upsertFoodEntry(existing.copy(syncState = NutritionSyncState.PENDING_DELETE))
            enqueuePendingWrite(
                NutritionQueueAction.DELETE_FOOD_ENTRY,
                "delete_food_entry:$id",
                gson.toJson(mapOf("id" to id)),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Nutrition Daily
    // -----------------------------------------------------------------------

    suspend fun getNutritionDaily(date: String = todayString()): NutritionDay? {
        val api = apiClient() ?: return null
        return api.getNutritionDaily(date).getOrNull()?.days?.firstOrNull()
    }

    // -----------------------------------------------------------------------
    // Water Entries
    // -----------------------------------------------------------------------

    suspend fun getWaterEntriesForDate(date: String = todayString()): List<WaterEntryCacheEntity> {
        return dao.getWaterEntriesForDate(date)
    }

    suspend fun refreshWaterEntriesFromServer(date: String = todayString()) {
        val api = apiClient() ?: return
        val result = api.getWaterEntries(date)
        result.getOrNull()?.entries?.let { entries ->
            val pendingIds = dao.getWaterEntriesForDate(date)
                .filter { it.syncState != NutritionSyncState.SYNCED }
                .map { it.id }
                .toSet()
            val cached = entries.map { it.toCache(date) }
                .filter { it.id !in pendingIds }
            dao.replaceSyncedWaterEntriesForDate(date, cached)
        }
    }

    suspend fun createWaterEntry(amountMl: Int, notes: String? = null): WaterEntryCacheEntity {
        require(amountMl in 1..10000) { "Water amount must be 1–10000 ml, got $amountMl" }
        val localId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val date = todayString()
        val entry = WaterEntryCacheEntity(
            id = localId,
            amountMl = amountMl,
            entrySource = "quick_add",
            loggedAt = now,
            loggedDate = date,
            notes = notes,
            syncState = NutritionSyncState.PENDING_CREATE,
            createdAt = now,
        )
        dao.upsertWaterEntry(entry)
        enqueuePendingWrite(
            NutritionQueueAction.CREATE_WATER_ENTRY,
            "create_water_entry:$localId",
            gson.toJson(mapOf(
                "localId" to localId,
                "amount_ml" to amountMl,
                "logged_at" to now,
                "notes" to notes,
                "entry_source" to "quick_add",
            )),
        )
        return entry
    }

    suspend fun deleteWaterEntry(id: String) {
        val existing = dao.getWaterEntryById(id) ?: return
        if (existing.syncState == NutritionSyncState.PENDING_CREATE) {
            dao.deleteWaterEntryById(id)
            dao.deletePendingWriteByDedupeKey("create_water_entry:$id")
        } else {
            dao.upsertWaterEntry(existing.copy(syncState = NutritionSyncState.PENDING_DELETE))
            enqueuePendingWrite(
                NutritionQueueAction.DELETE_WATER_ENTRY,
                "delete_water_entry:$id",
                gson.toJson(mapOf("id" to id)),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Nutrition Targets
    // -----------------------------------------------------------------------

    suspend fun getNutritionTarget(date: String = todayString()): NutritionTargetCacheEntity? {
        return dao.getActiveNutritionTarget(date)
    }

    suspend fun refreshNutritionTarget(date: String = todayString()) {
        val api = apiClient() ?: return
        api.getNutritionTarget(date).getOrNull()?.target?.let { target ->
            val effectiveDate = target.effective_date ?: date
            // Don't overwrite a locally-pending target with stale server state
            val existing = dao.getActiveNutritionTarget(effectiveDate)
            if (existing != null && existing.syncState != NutritionSyncState.SYNCED) return@let
            dao.upsertNutritionTarget(
                NutritionTargetCacheEntity(
                    effectiveDate = effectiveDate,
                    serverId = target.id,
                    calories = target.calories,
                    proteinG = target.protein_g,
                    carbsG = target.carbs_g,
                    fatG = target.fat_g,
                    method = target.method ?: "manual",
                    syncState = NutritionSyncState.SYNCED,
                    createdAt = null,
                    updatedAt = null,
                )
            )
        }
    }

    suspend fun setNutritionTarget(calories: Int, proteinG: Int, carbsG: Int, fatG: Int) {
        val date = todayString()
        val entity = NutritionTargetCacheEntity(
            effectiveDate = date,
            serverId = null,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            method = "manual",
            syncState = NutritionSyncState.PENDING_UPSERT,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString(),
        )
        dao.upsertNutritionTarget(entity)
        enqueuePendingWrite(
            NutritionQueueAction.UPSERT_NUTRITION_TARGET,
            "upsert_nutrition_target:$date",
            gson.toJson(mapOf(
                "effective_date" to date,
                "calories" to calories,
                "protein_g" to proteinG,
                "carbs_g" to carbsG,
                "fat_g" to fatG,
                "method" to "manual",
            )),
        )
    }

    // -----------------------------------------------------------------------
    // Hydration Targets
    // -----------------------------------------------------------------------

    suspend fun getHydrationTarget(date: String = todayString()): HydrationTargetCacheEntity? {
        return dao.getActiveHydrationTarget(date)
    }

    suspend fun refreshHydrationTarget(date: String = todayString()) {
        val api = apiClient() ?: return
        api.getHydrationTarget(date).getOrNull()?.target?.let { target ->
            val effectiveDate = target.effective_date ?: date
            val existing = dao.getActiveHydrationTarget(effectiveDate)
            if (existing != null && existing.syncState != NutritionSyncState.SYNCED) return@let
            dao.upsertHydrationTarget(
                HydrationTargetCacheEntity(
                    effectiveDate = effectiveDate,
                    serverId = target.id,
                    targetMl = target.target_ml,
                    method = target.method ?: "manual",
                    syncState = NutritionSyncState.SYNCED,
                    createdAt = null,
                    updatedAt = null,
                )
            )
        }
    }

    suspend fun setHydrationTarget(targetMl: Int) {
        val date = todayString()
        val entity = HydrationTargetCacheEntity(
            effectiveDate = date,
            serverId = null,
            targetMl = targetMl,
            method = "manual",
            syncState = NutritionSyncState.PENDING_UPSERT,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString(),
        )
        dao.upsertHydrationTarget(entity)
        enqueuePendingWrite(
            NutritionQueueAction.UPSERT_HYDRATION_TARGET,
            "upsert_hydration_target:$date",
            gson.toJson(mapOf(
                "effective_date" to date,
                "target_ml" to targetMl,
                "method" to "manual",
            )),
        )
    }

    // -----------------------------------------------------------------------
    // Pending write queue
    // -----------------------------------------------------------------------

    suspend fun getPendingWriteCount(): Int = dao.pendingWriteCount()

    suspend fun getPendingWrites(): List<PendingNutritionWriteEntity> = dao.getPendingWrites()

    suspend fun deletePendingWrite(id: Long) = dao.deletePendingWrite(id)

    private suspend fun enqueuePendingWrite(action: String, dedupeKey: String, payload: String) {
        dao.upsertPendingWrite(
            PendingNutritionWriteEntity(
                actionType = action,
                dedupeKey = dedupeKey,
                payload = payload,
            )
        )
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    companion object {
        /** Sentinel value stored in pending-write JSON to distinguish "clear to null" from "omitted/unchanged". */
        const val SENTINEL_NULL = "__null__"

        fun FoodResponse.toCache(): FoodCacheEntity = FoodCacheEntity(
            id = id,
            name = name,
            brand = brand,
            barcode = barcode,
            servingSizeG = serving_size_g,
            calories = calories,
            proteinG = protein_g,
            carbsG = carbs_g,
            fatG = fat_g,
            fiberG = fiber_g,
            sugarG = sugar_g,
            sodiumMg = sodium_mg,
            dataSource = data_source,
            qualityFlag = quality_flag,
            isCustom = is_custom ?: false,
            syncState = NutritionSyncState.SYNCED,
            lastUsedAt = System.currentTimeMillis(),
            createdAt = created_at,
            updatedAt = updated_at,
        )

        fun FoodEntryResponse.toCache(date: String): FoodEntryCacheEntity = FoodEntryCacheEntity(
            id = id,
            foodId = food_id,
            foodName = food_name,
            entrySource = entry_source,
            mealType = meal_type,
            servings = servings,
            calories = calories,
            proteinG = protein_g,
            carbsG = carbs_g,
            fatG = fat_g,
            fiberG = fiber_g,
            sugarG = sugar_g,
            sodiumMg = sodium_mg,
            loggedAt = logged_at,
            loggedDate = date,
            notes = notes,
            syncState = NutritionSyncState.SYNCED,
            createdAt = created_at,
            updatedAt = updated_at,
        )

        fun WaterEntryResponse.toCache(date: String): WaterEntryCacheEntity = WaterEntryCacheEntity(
            id = id,
            amountMl = amount_ml,
            entrySource = entry_source,
            loggedAt = logged_at,
            loggedDate = date,
            notes = notes,
            syncState = NutritionSyncState.SYNCED,
            createdAt = created_at,
        )
    }
}
