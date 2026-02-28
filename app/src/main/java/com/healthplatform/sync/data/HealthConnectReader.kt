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

        // Combine by matching timestamps (within 1 hour)
        return weightRecords.map { weight ->
            val bodyFat = bodyFatRecords.find {
                ChronoUnit.HOURS.between(it.time, weight.time).let { diff -> diff in -1..1 }
            }
            val leanMass = leanMassRecords.find {
                ChronoUnit.HOURS.between(it.time, weight.time).let { diff -> diff in -1..1 }
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
