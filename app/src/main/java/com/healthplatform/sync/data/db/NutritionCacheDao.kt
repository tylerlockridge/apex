package com.healthplatform.sync.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NutritionCacheDao {

    @Query(
        """
        SELECT * FROM food_cache
        WHERE syncState != :pendingDelete
          AND (
            name LIKE '%' || :query || '%'
            OR COALESCE(brand, '') LIKE '%' || :query || '%'
            OR COALESCE(barcode, '') LIKE '%' || :query || '%'
          )
        ORDER BY
          CASE WHEN syncState = :pendingCreate THEN 0 ELSE 1 END,
          lastUsedAt DESC,
          name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    suspend fun searchFoods(
        query: String,
        limit: Int,
        pendingCreate: String = NutritionSyncState.PENDING_CREATE,
        pendingDelete: String = NutritionSyncState.PENDING_DELETE,
    ): List<FoodCacheEntity>

    @Query(
        """
        SELECT * FROM food_cache
        WHERE syncState != :pendingDelete
        ORDER BY
          CASE WHEN syncState = :pendingCreate THEN 0 ELSE 1 END,
          lastUsedAt DESC,
          name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    suspend fun recentFoods(
        limit: Int,
        pendingCreate: String = NutritionSyncState.PENDING_CREATE,
        pendingDelete: String = NutritionSyncState.PENDING_DELETE,
    ): List<FoodCacheEntity>

    @Query("SELECT * FROM food_cache WHERE id = :id LIMIT 1")
    suspend fun getFoodById(id: String): FoodCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoods(foods: List<FoodCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFood(food: FoodCacheEntity)

    @Query("UPDATE food_cache SET lastUsedAt = :usedAt WHERE id = :id")
    suspend fun updateFoodLastUsed(id: String, usedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM food_cache WHERE id = :id")
    suspend fun deleteFoodById(id: String)

    @Query(
        """
        SELECT * FROM food_entry_cache
        WHERE loggedDate = :date
          AND syncState != :pendingDelete
        ORDER BY loggedAt DESC
        """
    )
    suspend fun getFoodEntriesForDate(
        date: String,
        pendingDelete: String = NutritionSyncState.PENDING_DELETE,
    ): List<FoodEntryCacheEntity>

    @Query("SELECT * FROM food_entry_cache WHERE id = :id LIMIT 1")
    suspend fun getFoodEntryById(id: String): FoodEntryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodEntries(entries: List<FoodEntryCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodEntry(entry: FoodEntryCacheEntity)

    @Query(
        """
        DELETE FROM food_entry_cache
        WHERE loggedDate = :date
          AND syncState = :synced
        """
    )
    suspend fun deleteSyncedFoodEntriesForDate(
        date: String,
        synced: String = NutritionSyncState.SYNCED,
    )

    @Query("DELETE FROM food_entry_cache WHERE id = :id")
    suspend fun deleteFoodEntryById(id: String)

    @Query(
        """
        SELECT * FROM water_entry_cache
        WHERE loggedDate = :date
          AND syncState != :pendingDelete
        ORDER BY loggedAt DESC
        """
    )
    suspend fun getWaterEntriesForDate(
        date: String,
        pendingDelete: String = NutritionSyncState.PENDING_DELETE,
    ): List<WaterEntryCacheEntity>

    @Query("SELECT * FROM water_entry_cache WHERE id = :id LIMIT 1")
    suspend fun getWaterEntryById(id: String): WaterEntryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWaterEntries(entries: List<WaterEntryCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWaterEntry(entry: WaterEntryCacheEntity)

    @Query(
        """
        DELETE FROM water_entry_cache
        WHERE loggedDate = :date
          AND syncState = :synced
        """
    )
    suspend fun deleteSyncedWaterEntriesForDate(
        date: String,
        synced: String = NutritionSyncState.SYNCED,
    )

    @Query("DELETE FROM water_entry_cache WHERE id = :id")
    suspend fun deleteWaterEntryById(id: String)

    @Query(
        """
        SELECT * FROM nutrition_target_cache
        WHERE effectiveDate <= :date
        ORDER BY effectiveDate DESC
        LIMIT 1
        """
    )
    suspend fun getActiveNutritionTarget(date: String): NutritionTargetCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNutritionTarget(target: NutritionTargetCacheEntity)

    @Query(
        """
        SELECT * FROM hydration_target_cache
        WHERE effectiveDate <= :date
        ORDER BY effectiveDate DESC
        LIMIT 1
        """
    )
    suspend fun getActiveHydrationTarget(date: String): HydrationTargetCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHydrationTarget(target: HydrationTargetCacheEntity)

    @Query("SELECT * FROM nutrition_write_queue ORDER BY createdAt ASC, id ASC")
    suspend fun getPendingWrites(): List<PendingNutritionWriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingWrite(write: PendingNutritionWriteEntity)

    @Query("SELECT * FROM nutrition_write_queue WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun getPendingWriteByDedupeKey(dedupeKey: String): PendingNutritionWriteEntity?

    @Query("DELETE FROM nutrition_write_queue WHERE id = :id")
    suspend fun deletePendingWrite(id: Long)

    @Query("DELETE FROM nutrition_write_queue WHERE dedupeKey = :dedupeKey")
    suspend fun deletePendingWriteByDedupeKey(dedupeKey: String)

    @Query("SELECT COUNT(*) FROM nutrition_write_queue")
    suspend fun pendingWriteCount(): Int

    @Transaction
    suspend fun replaceSyncedFoodEntriesForDate(date: String, entries: List<FoodEntryCacheEntity>) {
        deleteSyncedFoodEntriesForDate(date)
        if (entries.isNotEmpty()) {
            upsertFoodEntries(entries)
        }
    }

    @Transaction
    suspend fun replaceSyncedWaterEntriesForDate(date: String, entries: List<WaterEntryCacheEntity>) {
        deleteSyncedWaterEntriesForDate(date)
        if (entries.isNotEmpty()) {
            upsertWaterEntries(entries)
        }
    }
}
