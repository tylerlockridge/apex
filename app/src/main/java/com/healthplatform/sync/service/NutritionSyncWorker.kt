package com.healthplatform.sync.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthplatform.sync.Config
import com.healthplatform.sync.data.NutritionRepository
import com.healthplatform.sync.data.NutritionRepository.Companion.SENTINEL_NULL
import com.healthplatform.sync.data.NutritionRepository.Companion.toCache
import com.healthplatform.sync.data.db.*
import com.healthplatform.sync.security.SecurePrefs
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that drains the nutrition/hydration pending-write queue.
 * Follows the same pattern as SyncWorker: network-constrained, exponential backoff,
 * deduplicated writes, delete-from-queue only after server ACK.
 */
class NutritionSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val gson = Gson()
    private val dao by lazy { ApexDatabase.get(applicationContext).nutritionCacheDao() }

    override suspend fun doWork(): Result {
        val pending = dao.getPendingWrites()
        if (pending.isEmpty()) return Result.success()

        val api = try {
            val serverUrl = Config.getServerUrl(applicationContext)
            val deviceSecret = SecurePrefs.getDeviceSecret(applicationContext, Config.DEVICE_SECRET)
            ServerApiClient(SecurePrefs.getApiKey(applicationContext), serverUrl, deviceSecret)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot create API client", e)
            return Result.retry()
        }

        var anyTransient = false
        for (write in pending) {
            val success = try {
                processWrite(api, write)
            } catch (e: Exception) {
                Log.w(TAG, "Error processing ${write.actionType}: ${e.message}")
                anyTransient = true
                false
            }
            if (success) {
                dao.deletePendingWrite(write.id)
            }
        }

        return if (anyTransient) Result.retry() else Result.success()
    }

    private suspend fun processWrite(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        return when (write.actionType) {
            NutritionQueueAction.CREATE_FOOD -> processCreateFood(api, write)
            NutritionQueueAction.CREATE_FOOD_ENTRY -> processCreateFoodEntry(api, write)
            NutritionQueueAction.UPDATE_FOOD_ENTRY -> processUpdateFoodEntry(api, write)
            NutritionQueueAction.DELETE_FOOD_ENTRY -> processDeleteFoodEntry(api, write)
            NutritionQueueAction.CREATE_WATER_ENTRY -> processCreateWaterEntry(api, write)
            NutritionQueueAction.DELETE_WATER_ENTRY -> processDeleteWaterEntry(api, write)
            NutritionQueueAction.UPSERT_NUTRITION_TARGET -> processUpsertNutritionTarget(api, write)
            NutritionQueueAction.UPSERT_HYDRATION_TARGET -> processUpsertHydrationTarget(api, write)
            else -> {
                Log.w(TAG, "Unknown action type: ${write.actionType}")
                true // drop unknown actions
            }
        }
    }

    private suspend fun processCreateFood(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        val map = parseMap(write.payload)
        val localId = map["localId"] as? String ?: return true
        @Suppress("UNCHECKED_CAST")
        val reqMap = map["request"] as? Map<String, Any?> ?: return true
        val request = CreateFoodRequest(
            name = reqMap["name"] as? String ?: return true,
            brand = reqMap["brand"] as? String,
            barcode = reqMap["barcode"] as? String,
            serving_size_g = (reqMap["serving_size_g"] as? Number)?.toDouble(),
            calories = (reqMap["calories"] as? Number)?.toDouble() ?: return true,
            protein_g = (reqMap["protein_g"] as? Number)?.toDouble(),
            carbs_g = (reqMap["carbs_g"] as? Number)?.toDouble(),
            fat_g = (reqMap["fat_g"] as? Number)?.toDouble(),
            fiber_g = (reqMap["fiber_g"] as? Number)?.toDouble(),
            sugar_g = (reqMap["sugar_g"] as? Number)?.toDouble(),
            sodium_mg = (reqMap["sodium_mg"] as? Number)?.toDouble(),
        )
        val result = api.createFood(request)
        val serverFood = result.getOrNull() ?: return false
        // Replace local-id entity with server-id entity
        dao.deleteFoodById(localId)
        dao.upsertFood(serverFood.toCache())
        // Update any pending food entry writes that reference the local ID
        val pendingEntries = dao.getPendingWrites().filter {
            it.actionType == NutritionQueueAction.CREATE_FOOD_ENTRY &&
                it.payload.contains(localId)
        }
        for (pe in pendingEntries) {
            val updated = pe.copy(payload = pe.payload.replace(localId, serverFood.id))
            dao.upsertPendingWrite(updated)
        }
        // Update food entry cache references
        val entries = dao.getFoodEntriesForDate(
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        )
        for (entry in entries) {
            if (entry.foodId == localId) {
                dao.upsertFoodEntry(entry.copy(foodId = serverFood.id))
            }
        }
        return true
    }

    private suspend fun processCreateFoodEntry(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        val map = parseMap(write.payload)
        val localId = map["localId"] as? String ?: return true
        val foodId = map["food_id"] as? String ?: return true
        val request = CreateFoodEntryRequest(
            food_id = foodId,
            servings = (map["servings"] as? Number)?.toDouble() ?: 1.0,
            meal_type = map["meal_type"] as? String,
            logged_at = map["logged_at"] as? String,
            notes = map["notes"] as? String,
            entry_source = map["entry_source"] as? String,
        )
        val result = api.createFoodEntry(request)
        val serverEntry = result.getOrNull() ?: return false
        // Replace local-id entry with server entry
        dao.deleteFoodEntryById(localId)
        val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        dao.upsertFoodEntry(
            NutritionRepository.run { serverEntry.toCache(date) }
        )
        return true
    }

    private suspend fun processUpdateFoodEntry(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        val map = parseMap(write.payload)
        val id = map["id"] as? String ?: return true
        val patch = UpdateFoodEntryPatch(
            servings = (map["servings"] as? Number)?.toDouble()
                ?.let { PatchField.SetValue(it) } ?: PatchField.Unchanged,
            meal_type = decodePatchString(map, "meal_type"),
            notes = decodePatchString(map, "notes"),
        )
        val result = api.updateFoodEntry(id, patch)
        if (result.isSuccess) {
            val entry = dao.getFoodEntryById(id)
            if (entry != null && entry.syncState == NutritionSyncState.PENDING_UPDATE) {
                dao.upsertFoodEntry(entry.copy(syncState = NutritionSyncState.SYNCED))
            }
        }
        return result.isSuccess
    }

    /** Decode a tri-state string from the pending-write JSON payload. */
    private fun decodePatchString(map: Map<String, Any?>, key: String): PatchField<String> {
        if (!map.containsKey(key)) return PatchField.Unchanged
        val raw = map[key]
        if (raw == NutritionRepository.SENTINEL_NULL) return PatchField.SetNull
        return if (raw is String) PatchField.SetValue(raw) else PatchField.Unchanged
    }

    private suspend fun processDeleteFoodEntry(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        val map = parseMap(write.payload)
        val id = map["id"] as? String ?: return true
        val result = api.deleteFoodEntry(id)
        if (result.isSuccess) {
            dao.deleteFoodEntryById(id)
        }
        return result.isSuccess
    }

    private suspend fun processCreateWaterEntry(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        val map = parseMap(write.payload)
        val localId = map["localId"] as? String ?: return true
        val request = CreateWaterEntryRequest(
            amount_ml = (map["amount_ml"] as? Number)?.toInt() ?: return true,
            logged_at = map["logged_at"] as? String,
            notes = map["notes"] as? String,
            entry_source = map["entry_source"] as? String,
        )
        val result = api.createWaterEntry(request)
        val serverEntry = result.getOrNull() ?: return false
        dao.deleteWaterEntryById(localId)
        val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        dao.upsertWaterEntry(
            NutritionRepository.run { serverEntry.toCache(date) }
        )
        return true
    }

    private suspend fun processDeleteWaterEntry(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        val map = parseMap(write.payload)
        val id = map["id"] as? String ?: return true
        val result = api.deleteWaterEntry(id)
        if (result.isSuccess) {
            dao.deleteWaterEntryById(id)
        }
        return result.isSuccess
    }

    private suspend fun processUpsertNutritionTarget(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        val map = parseMap(write.payload)
        val request = PutNutritionTargetRequest(
            effective_date = map["effective_date"] as? String,
            calories = (map["calories"] as? Number)?.toInt() ?: return true,
            protein_g = (map["protein_g"] as? Number)?.toInt() ?: return true,
            carbs_g = (map["carbs_g"] as? Number)?.toInt() ?: return true,
            fat_g = (map["fat_g"] as? Number)?.toInt() ?: return true,
            method = map["method"] as? String,
        )
        val result = api.putNutritionTarget(request)
        if (result.isSuccess) {
            val date = request.effective_date ?: return true
            val target = dao.getActiveNutritionTarget(date)
            if (target != null && target.syncState == NutritionSyncState.PENDING_UPSERT) {
                dao.upsertNutritionTarget(target.copy(syncState = NutritionSyncState.SYNCED))
            }
        }
        return result.isSuccess
    }

    private suspend fun processUpsertHydrationTarget(api: ServerApiClient, write: PendingNutritionWriteEntity): Boolean {
        val map = parseMap(write.payload)
        val request = PutHydrationTargetRequest(
            effective_date = map["effective_date"] as? String,
            target_ml = (map["target_ml"] as? Number)?.toInt() ?: return true,
            method = map["method"] as? String,
        )
        val result = api.putHydrationTarget(request)
        if (result.isSuccess) {
            val date = request.effective_date ?: return true
            val target = dao.getActiveHydrationTarget(date)
            if (target != null && target.syncState == NutritionSyncState.PENDING_UPSERT) {
                dao.upsertHydrationTarget(target.copy(syncState = NutritionSyncState.SYNCED))
            }
        }
        return result.isSuccess
    }

    private fun parseMap(json: String): Map<String, Any?> {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(json, type)
    }

    companion object {
        private const val TAG = "NutritionSyncWorker"
        const val WORK_NAME = "nutrition_sync_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<NutritionSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runOnce(context: Context): java.util.UUID {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<NutritionSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }
    }
}
