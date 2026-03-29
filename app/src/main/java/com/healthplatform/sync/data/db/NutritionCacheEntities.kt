package com.healthplatform.sync.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object NutritionSyncState {
    const val SYNCED = "synced"
    const val PENDING_CREATE = "pending_create"
    const val PENDING_UPDATE = "pending_update"
    const val PENDING_DELETE = "pending_delete"
    const val PENDING_UPSERT = "pending_upsert"
}

object NutritionQueueAction {
    const val CREATE_FOOD = "create_food"
    const val CREATE_FOOD_ENTRY = "create_food_entry"
    const val UPDATE_FOOD_ENTRY = "update_food_entry"
    const val DELETE_FOOD_ENTRY = "delete_food_entry"
    const val CREATE_WATER_ENTRY = "create_water_entry"
    const val DELETE_WATER_ENTRY = "delete_water_entry"
    const val UPSERT_NUTRITION_TARGET = "upsert_nutrition_target"
    const val UPSERT_HYDRATION_TARGET = "upsert_hydration_target"
}

@Entity(
    tableName = "food_cache",
    indices = [
        Index("barcode"),
        Index("name"),
    ]
)
data class FoodCacheEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val servingSizeG: Double?,
    val calories: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val sugarG: Double?,
    val sodiumMg: Double?,
    val dataSource: String?,
    val qualityFlag: String?,
    val isCustom: Boolean,
    val syncState: String = NutritionSyncState.SYNCED,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val createdAt: String?,
    val updatedAt: String?,
)

@Entity(
    tableName = "food_entry_cache",
    indices = [
        Index("loggedDate"),
        Index("foodId"),
    ]
)
data class FoodEntryCacheEntity(
    @PrimaryKey val id: String,
    val foodId: String?,
    val foodName: String,
    val entrySource: String?,
    val mealType: String?,
    val servings: Double,
    val calories: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val sugarG: Double?,
    val sodiumMg: Double?,
    val loggedAt: String,
    val loggedDate: String,
    val notes: String?,
    val syncState: String = NutritionSyncState.SYNCED,
    val createdAt: String?,
    val updatedAt: String?,
)

@Entity(
    tableName = "water_entry_cache",
    indices = [Index("loggedDate")]
)
data class WaterEntryCacheEntity(
    @PrimaryKey val id: String,
    val amountMl: Int,
    val entrySource: String?,
    val loggedAt: String,
    val loggedDate: String,
    val notes: String?,
    val syncState: String = NutritionSyncState.SYNCED,
    val createdAt: String?,
)

@Entity(tableName = "nutrition_target_cache")
data class NutritionTargetCacheEntity(
    @PrimaryKey val effectiveDate: String,
    val serverId: String?,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val method: String,
    val syncState: String = NutritionSyncState.SYNCED,
    val createdAt: String?,
    val updatedAt: String?,
)

@Entity(tableName = "hydration_target_cache")
data class HydrationTargetCacheEntity(
    @PrimaryKey val effectiveDate: String,
    val serverId: String?,
    val targetMl: Int,
    val method: String,
    val syncState: String = NutritionSyncState.SYNCED,
    val createdAt: String?,
    val updatedAt: String?,
)

@Entity(
    tableName = "nutrition_write_queue",
    indices = [Index(value = ["dedupeKey"], unique = true)]
)
data class PendingNutritionWriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val dedupeKey: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
)
