package com.healthplatform.sync.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectReader(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    )

    suspend fun checkPermissions(): Set<String> {
        return healthConnectClient.permissionController.getGrantedPermissions()
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = checkPermissions()
        return requiredPermissions.all { it in granted }
    }

    suspend fun readBloodPressure(since: Instant): List<BloodPressureData> {
        val timeRange = TimeRangeFilter.after(since)
        val allRecords = mutableListOf<BloodPressureRecord>()
        var pageToken: String? = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = timeRange,
                    pageToken = pageToken
                )
            )
            allRecords += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return allRecords.map { record ->
            BloodPressureData(
                systolic = record.systolic.inMillimetersOfMercury.toInt(),
                diastolic = record.diastolic.inMillimetersOfMercury.toInt(),
                measuredAt = record.time.toString(),
                deviceName = record.metadata.device?.manufacturer
            )
        }
    }

    suspend fun readSleep(since: Instant): List<SleepData> {
        val timeRange = TimeRangeFilter.after(since)
        val allRecords = mutableListOf<SleepSessionRecord>()
        var pageToken: String? = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRange,
                    pageToken = pageToken
                )
            )
            allRecords += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        // Prefer Oura Ring data — better sleep staging than Pixel Watch
        val ouraRecords = allRecords.filter {
            it.metadata.dataOrigin.packageName == "com.ouraring.oura"
        }
        val records = if (ouraRecords.isNotEmpty()) ouraRecords else allRecords
        return records.map { record ->
            val stages = record.stages
            val deepMinutes = stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_DEEP }
                .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
            val remMinutes = stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_REM }
                .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
            val lightMinutes = stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_LIGHT }
                .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }

            SleepData(
                sleepStart = record.startTime.toString(),
                sleepEnd = record.endTime.toString(),
                durationMinutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime).toInt(),
                deepSleepMinutes = deepMinutes.toInt(),
                remSleepMinutes = remMinutes.toInt(),
                lightSleepMinutes = lightMinutes.toInt(),
                deviceName = record.metadata.device?.manufacturer
            )
        }
    }

    suspend fun readWeight(since: Instant): List<BodyMeasurementData> {
        val timeRange = TimeRangeFilter.after(since)

        val weightRecords = mutableListOf<WeightRecord>()
        var pageToken: String? = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = timeRange,
                    pageToken = pageToken
                )
            )
            weightRecords += response.records
            pageToken = response.pageToken
        } while (pageToken != null)

        val bodyFatRecords = mutableListOf<BodyFatRecord>()
        pageToken = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = timeRange,
                    pageToken = pageToken
                )
            )
            bodyFatRecords += response.records
            pageToken = response.pageToken
        } while (pageToken != null)

        val leanMassRecords = mutableListOf<LeanBodyMassRecord>()
        pageToken = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = LeanBodyMassRecord::class,
                    timeRangeFilter = timeRange,
                    pageToken = pageToken
                )
            )
            leanMassRecords += response.records
            pageToken = response.pageToken
        } while (pageToken != null)

        // Combine by matching timestamps (within 1 hour = 3600 seconds).
        // Using SECONDS avoids the HOURS truncation bug where 1h59m returns 1 (in -1..1).
        return weightRecords.map { weight ->
            val bodyFat = bodyFatRecords.find {
                Math.abs(ChronoUnit.SECONDS.between(it.time, weight.time)) <= 3600
            }
            val leanMass = leanMassRecords.find {
                Math.abs(ChronoUnit.SECONDS.between(it.time, weight.time)) <= 3600
            }

            BodyMeasurementData(
                measuredAt = weight.time.toString(),
                weightKg = weight.weight.inKilograms,
                bodyFatPercent = bodyFat?.percentage?.value,
                muscleMassKg = leanMass?.mass?.inKilograms,
                deviceName = weight.metadata.device?.manufacturer
            )
        }
    }

    suspend fun readHeartRateVariability(since: Instant): List<HrvData> {
        val timeRange = TimeRangeFilter.after(since)
        val allRecords = mutableListOf<HeartRateVariabilityRmssdRecord>()
        var pageToken: String? = null
        do {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = timeRange,
                    pageToken = pageToken
                )
            )
            allRecords += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        // Prefer Oura Ring HRV — overnight resting RMSSD is more meaningful than spot checks
        val ouraRecords = allRecords.filter {
            it.metadata.dataOrigin.packageName == "com.ouraring.oura"
        }
        val records = if (ouraRecords.isNotEmpty()) ouraRecords else allRecords
        return records.map { record ->
            HrvData(
                measuredAt = record.time.toString(),
                hrvMs = record.heartRateVariabilityMillis,
                deviceName = record.metadata.device?.manufacturer
            )
        }
    }

    // -------------------------------------------------------------------------
    // Change token helpers — incremental sync (avoids re-reading unchanged data)
    // -------------------------------------------------------------------------

    /** Returns a change token for BP records. Store and pass to [readBloodPressureChanges]. */
    suspend fun getBpChangesToken(): String =
        healthConnectClient.getChangesToken(
            ChangesTokenRequest(recordTypes = setOf(BloodPressureRecord::class))
        )

    /** Returns a change token for sleep records. Store and pass to [readSleepChanges]. */
    suspend fun getSleepChangesToken(): String =
        healthConnectClient.getChangesToken(
            ChangesTokenRequest(recordTypes = setOf(SleepSessionRecord::class))
        )

    /** Returns a change token for HRV records. Store and pass to [readHrvChanges]. */
    suspend fun getHrvChangesToken(): String =
        healthConnectClient.getChangesToken(
            ChangesTokenRequest(recordTypes = setOf(HeartRateVariabilityRmssdRecord::class))
        )

    /**
     * Returns BP records changed since [token] and the updated token for the next call.
     * Throws [Exception] if the token is expired — caller should fall back to [readBloodPressure].
     */
    suspend fun readBloodPressureChanges(token: String): Pair<List<BloodPressureData>, String> {
        val records = mutableListOf<BloodPressureData>()
        var currentToken = token
        var hasMore = true
        while (hasMore) {
            val response = healthConnectClient.getChanges(currentToken)
            response.changes
                .filterIsInstance<UpsertionChange>()
                .mapNotNull { it.record as? BloodPressureRecord }
                .forEach { record ->
                    records += BloodPressureData(
                        systolic = record.systolic.inMillimetersOfMercury.toInt(),
                        diastolic = record.diastolic.inMillimetersOfMercury.toInt(),
                        measuredAt = record.time.toString(),
                        deviceName = record.metadata.device?.manufacturer
                    )
                }
            currentToken = response.nextChangesToken
            hasMore = response.hasMore
        }
        return records to currentToken
    }

    /**
     * Returns sleep sessions changed since [token] and the updated token.
     * Throws [Exception] if the token is expired — caller should fall back to [readSleep].
     */
    suspend fun readSleepChanges(token: String): Pair<List<SleepData>, String> {
        val sessions = mutableListOf<SleepData>()
        var currentToken = token
        var hasMore = true
        while (hasMore) {
            val response = healthConnectClient.getChanges(currentToken)
            val newRecords = response.changes
                .filterIsInstance<UpsertionChange>()
                .mapNotNull { it.record as? SleepSessionRecord }
            // Prefer Oura Ring data
            val oura = newRecords.filter { it.metadata.dataOrigin.packageName == "com.ouraring.oura" }
            val toProcess = if (oura.isNotEmpty()) oura else newRecords
            toProcess.forEach { record ->
                val stages = record.stages
                sessions += SleepData(
                    sleepStart = record.startTime.toString(),
                    sleepEnd = record.endTime.toString(),
                    durationMinutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime).toInt(),
                    deepSleepMinutes = stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_DEEP }
                        .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }.toInt(),
                    remSleepMinutes = stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_REM }
                        .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }.toInt(),
                    lightSleepMinutes = stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_LIGHT }
                        .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }.toInt(),
                    deviceName = record.metadata.device?.manufacturer
                )
            }
            currentToken = response.nextChangesToken
            hasMore = response.hasMore
        }
        return sessions to currentToken
    }

    /**
     * Returns HRV records changed since [token] and the updated token.
     * Throws [Exception] if the token is expired — caller should fall back to [readHeartRateVariability].
     */
    suspend fun readHrvChanges(token: String): Pair<List<HrvData>, String> {
        val records = mutableListOf<HrvData>()
        var currentToken = token
        var hasMore = true
        while (hasMore) {
            val response = healthConnectClient.getChanges(currentToken)
            val newRecords = response.changes
                .filterIsInstance<UpsertionChange>()
                .mapNotNull { it.record as? HeartRateVariabilityRmssdRecord }
            // Prefer Oura Ring HRV
            val oura = newRecords.filter { it.metadata.dataOrigin.packageName == "com.ouraring.oura" }
            val toProcess = if (oura.isNotEmpty()) oura else newRecords
            toProcess.forEach { record ->
                records += HrvData(
                    measuredAt = record.time.toString(),
                    hrvMs = record.heartRateVariabilityMillis,
                    deviceName = record.metadata.device?.manufacturer
                )
            }
            currentToken = response.nextChangesToken
            hasMore = response.hasMore
        }
        return records to currentToken
    }

    companion object {
        fun isAvailable(context: Context): Boolean {
            return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }
    }
}

// Data classes for API sync
data class BloodPressureData(
    val systolic: Int,
    val diastolic: Int,
    val measuredAt: String,
    val pulse: Int? = null,
    val context: String? = null,
    val deviceName: String? = null
)

data class SleepData(
    val sleepStart: String,
    val sleepEnd: String,
    val durationMinutes: Int,
    val deepSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null,
    val sleepScore: Int? = null,
    val deviceName: String? = null
)

data class BodyMeasurementData(
    val measuredAt: String,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val deviceName: String? = null
)

data class HrvData(
    val measuredAt: String,
    val hrvMs: Double,
    val deviceName: String? = null
)
