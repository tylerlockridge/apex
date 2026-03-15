package com.healthplatform.sync.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncQueueDao {

    /**
     * Inserts records into the queue. Duplicate (dataType, measuredAt) pairs are silently
     * ignored so repeated Health Connect reads never create duplicate queue entries.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<SyncQueueEntity>)

    /** Returns all queued records for a given data type, oldest first. */
    @Query("SELECT * FROM sync_queue WHERE dataType = :dataType ORDER BY measuredAt ASC")
    suspend fun getPending(dataType: String): List<SyncQueueEntity>

    /**
     * Removes successfully-synced records. Uses Room's @Delete which automatically
     * chunks the list, avoiding SQLiteException when the queue exceeds 999 records.
     */
    @Delete
    suspend fun delete(records: List<SyncQueueEntity>)

    /** Total number of records still waiting to be synced (across all types). */
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun pendingCount(): Int

    /** Wipes the entire queue — used by "Clear all data" to ensure no health records survive. */
    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()
}
