package com.healthplatform.sync.data.db

import androidx.room.Dao
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

    /** Removes successfully-synced records by their primary key. */
    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Total number of records still waiting to be synced (across all types). */
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun pendingCount(): Int
}
