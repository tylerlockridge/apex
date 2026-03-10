package com.healthplatform.sync.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest

/**
 * Represents a single health record waiting to be uploaded to the server.
 *
 * Records are inserted after each Health Connect read. [OnConflictStrategy.IGNORE] on the
 * DAO prevents double-queuing identical records (unique index on [recordHash]).
 *
 * [recordHash] is a SHA-256 of "$dataType|$payload" which captures both the data type and
 * the full record content. This correctly handles multiple distinct records from different
 * devices that share the same timestamp — a scenario where the previous (dataType, measuredAt)
 * index would silently drop the second record.
 *
 * Rows are deleted from the queue only after a successful server acknowledgement, so a
 * network failure leaves them in place for the next WorkManager retry.
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["recordHash"], unique = true)]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** One of "blood_pressure", "sleep", "body", "hrv". */
    val dataType: String,
    /** ISO-8601 timestamp of the health record — kept for ordering and debugging. */
    val measuredAt: String,
    /** Gson-serialised typed data class (BloodPressureData, SleepData, etc.). */
    val payload: String,
    /** SHA-256 of "$dataType|$payload" — used as the unique deduplication key. */
    val recordHash: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Computes the deduplication hash for a queue entity. */
fun computeRecordHash(dataType: String, payload: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest("$dataType|$payload".toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
