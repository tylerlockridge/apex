package com.healthplatform.sync.data.health

import java.time.Instant

/**
 * Abstraction over health data sources (ADR-005).
 *
 * SyncWorker depends on this interface, not on HealthConnectReader directly.
 * The default implementation is [HealthConnectProvider]. Alternative providers
 * (WHOOP, Oura, Garmin) can be added post-MVP if A-01/H-04 require them.
 *
 * Result metadata (staleness flag, source identifier) is deferred to Phase 2
 * when the readiness engine becomes the first consumer. Package 0B keeps the
 * return types identical to HealthConnectReader's current signatures.
 */
interface HealthDataProvider {

    // -- Full-window reads ------------------------------------------------

    suspend fun readBloodPressure(since: Instant): List<BloodPressureData>
    suspend fun readSleep(since: Instant): List<SleepData>
    suspend fun readWeight(since: Instant): List<BodyMeasurementData>
    suspend fun readHeartRateVariability(since: Instant): List<HrvData>

    // -- Change-token incremental reads -----------------------------------

    suspend fun readBloodPressureChanges(token: String): Pair<List<BloodPressureData>, String>
    suspend fun readSleepChanges(token: String): Pair<List<SleepData>, String>
    suspend fun readHrvChanges(token: String): Pair<List<HrvData>, String>

    // -- Change-token acquisition -----------------------------------------

    suspend fun getBpChangesToken(): String
    suspend fun getSleepChangesToken(): String
    suspend fun getHrvChangesToken(): String

    // -- Availability and permissions -------------------------------------

    fun isAvailable(): Boolean
    suspend fun checkPermissions(): Set<String>
    suspend fun hasAllPermissions(): Boolean
    val requiredPermissions: Set<String>
}
