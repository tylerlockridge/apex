package com.healthplatform.sync.readiness

/**
 * Phase 2 readiness engine models (ADR-003).
 * Pure data — no Android dependencies.
 */

enum class ReadinessInputId {
    SLEEP,
    BLOOD_PRESSURE,
    HRV,
    SUBJECTIVE,
    TRAINING_LOAD
}

enum class ReadinessInputStatus {
    /** Data is recent (< normalHours). */
    FRESH,
    /** Data is older than normalHours but within degradedHours — weight halved. */
    DEGRADED,
    /** No data available for this input. */
    MISSING,
    /** Input weight is 0 by configuration — not contributing. */
    EXCLUDED
}

data class ReadinessInputResult(
    val id: ReadinessInputId,
    val status: ReadinessInputStatus,
    val rawLabel: String?,
    /** 0-100 individual score, or null if missing/excluded. */
    val score: Int?,
    val configuredWeight: Double,
    /** Actual weight after staleness/missing adjustments. */
    val effectiveWeight: Double,
    val lastUpdatedAt: String?,
    val reason: String
)

data class ReadinessResult(
    /** 0-100 aggregate, or null when no inputs contributed. */
    val aggregateScore: Int?,
    /** "Good to go" / "Moderate" / "Recovery day" / null */
    val label: String?,
    val summary: String,
    val inputs: List<ReadinessInputResult>
)
