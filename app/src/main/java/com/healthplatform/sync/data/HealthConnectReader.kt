package com.healthplatform.sync.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectReader(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
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
        val request = ReadRecordsRequest(
            recordType = BloodPressureRecord::class,
            timeRangeFilter = TimeRangeFilter.after(since)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.map { record ->
            BloodPressureData(
                systolic = record.systolic.inMillimetersOfMercury.toInt(),
                diastolic = record.diastolic.inMillimetersOfMercury.toInt(),
                measuredAt = record.time.toString(),
                deviceName = record.metadata.device?.manufacturer
            )
        }
    }

    suspend fun readSleep(since: Instant): List<SleepData> {
        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.after(since)
        )
        val response = healthConnectClient.readRecords(request)
        // Prefer Oura Ring data — better sleep staging than Pixel Watch
        val allRecords = response.records
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
        val weightRequest = ReadRecordsRequest(
            recordType = WeightRecord::class,
            timeRangeFilter = TimeRangeFilter.after(since)
        )
        val weightResponse = healthConnectClient.readRecords(weightRequest)

        val bodyFatRequest = ReadRecordsRequest(
            recordType = BodyFatRecord::class,
            timeRangeFilter = TimeRangeFilter.after(since)
        )
        val bodyFatResponse = healthConnectClient.readRecords(bodyFatRequest)

        val leanMassRequest = ReadRecordsRequest(
            recordType = LeanBodyMassRecord::class,
            timeRangeFilter = TimeRangeFilter.after(since)
        )
        val leanMassResponse = healthConnectClient.readRecords(leanMassRequest)

        // Combine by matching timestamps (within 1 hour)
        return weightResponse.records.map { weight ->
            val bodyFat = bodyFatResponse.records.find {
                ChronoUnit.HOURS.between(it.time, weight.time).let { diff -> diff in -1..1 }
            }
            val leanMass = leanMassResponse.records.find {
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
        val request = ReadRecordsRequest(
            recordType = HeartRateVariabilityRmssdRecord::class,
            timeRangeFilter = TimeRangeFilter.after(since)
        )
        val response = healthConnectClient.readRecords(request)
        // Prefer Oura Ring HRV — overnight resting RMSSD is more meaningful than spot checks
        val allRecords = response.records
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
