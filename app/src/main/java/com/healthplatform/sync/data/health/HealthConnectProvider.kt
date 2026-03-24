package com.healthplatform.sync.data.health

import android.content.Context
import com.healthplatform.sync.data.HealthConnectReader
import java.time.Instant

/**
 * Default [HealthDataProvider] implementation backed by Health Connect (ADR-005 §2).
 *
 * Pure delegation to [HealthConnectReader] — no added logic.
 * Accepts an optional [reader] parameter for test injection, matching the
 * existing [HealthConnectReader] constructor pattern (context, client?).
 */
class HealthConnectProvider(
    private val context: Context,
    reader: HealthConnectReader? = null,
) : HealthDataProvider {

    private val hcReader: HealthConnectReader = reader ?: HealthConnectReader(context)

    // -- Full-window reads ------------------------------------------------

    override suspend fun readBloodPressure(since: Instant) = hcReader.readBloodPressure(since)
    override suspend fun readSleep(since: Instant) = hcReader.readSleep(since)
    override suspend fun readWeight(since: Instant) = hcReader.readWeight(since)
    override suspend fun readHeartRateVariability(since: Instant) = hcReader.readHeartRateVariability(since)

    // -- Change-token incremental reads -----------------------------------

    override suspend fun readBloodPressureChanges(token: String) = hcReader.readBloodPressureChanges(token)
    override suspend fun readSleepChanges(token: String) = hcReader.readSleepChanges(token)
    override suspend fun readHrvChanges(token: String) = hcReader.readHrvChanges(token)

    // -- Change-token acquisition -----------------------------------------

    override suspend fun getBpChangesToken() = hcReader.getBpChangesToken()
    override suspend fun getSleepChangesToken() = hcReader.getSleepChangesToken()
    override suspend fun getHrvChangesToken() = hcReader.getHrvChangesToken()

    // -- Availability and permissions -------------------------------------

    override fun isAvailable() = HealthConnectReader.isAvailable(context)
    override suspend fun checkPermissions() = hcReader.checkPermissions()
    override suspend fun hasAllPermissions() = hcReader.hasAllPermissions()
    override val requiredPermissions get() = hcReader.requiredPermissions
}
