package com.healthplatform.sync.data.health

/**
 * Project-level health domain types — decoupled from Health Connect SDK.
 * Moved from HealthConnectReader.kt as part of Package 0B (ADR-005).
 */

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
