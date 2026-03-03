package com.healthplatform.sync.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single health record waiting to be uploaded to the server.
 *
 * Records are inserted after each Health Connect read. [OnConflictStrategy.IGNORE] on the
 * DAO prevents double-queuing the same record (unique index on [dataType] + [measuredAt]).
 * Rows are deleted from the queue only after a successful server acknowledgement, so a
 * network failure leaves them in place for the next WorkManager retry.
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["dataType", "measuredAt"], unique = true)]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** One of "blood_pressure", "sleep", "body", "hrv". */
    val dataType: String,
    /** ISO-8601 timestamp of the health record — used for deduplication. */
    val measuredAt: String,
    /** Gson-serialised typed data class (BloodPressureData, SleepData, etc.). */
    val payload: String,
    val createdAt: Long = System.currentTimeMillis()
)
