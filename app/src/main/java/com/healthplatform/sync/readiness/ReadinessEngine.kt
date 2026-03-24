package com.healthplatform.sync.readiness

/**
 * Stateless readiness computation engine (ADR-003).
 * Pure function — no Android dependencies. Testable in isolation.
 *
 * Inputs are optional. Missing/excluded inputs are omitted from the
 * aggregate and their weight redistributed across remaining inputs.
 * If all inputs are missing/excluded, returns null score.
 *
 * Staleness: each input carries its lastUpdatedAt timestamp.
 * The engine compares against [nowMs] to classify staleness.
 */
object ReadinessEngine {

    data class RawInput(
        val id: ReadinessInputId,
        val value: Double?,
        val lastUpdatedAt: String?,
    )

    /**
     * Compute readiness from raw health inputs.
     *
     * @param inputs Available raw inputs (sleep minutes, systolic BP, HRV ms, etc.)
     * @param config Weights and staleness thresholds
     * @param nowMs Current time in epoch millis (injectable for testing)
     */
    fun compute(
        inputs: List<RawInput>,
        config: Config,
        nowMs: Long = System.currentTimeMillis(),
    ): ReadinessResult {
        val inputResults = inputs.map { raw ->
            val weight = config.weightFor(raw.id)

            // Excluded by config (weight == 0)
            if (weight <= 0.0) {
                return@map ReadinessInputResult(
                    id = raw.id, status = ReadinessInputStatus.EXCLUDED,
                    rawLabel = null, score = null,
                    configuredWeight = weight, effectiveWeight = 0.0,
                    lastUpdatedAt = raw.lastUpdatedAt, reason = "Disabled"
                )
            }

            // Missing data
            if (raw.value == null) {
                return@map ReadinessInputResult(
                    id = raw.id, status = ReadinessInputStatus.MISSING,
                    rawLabel = null, score = null,
                    configuredWeight = weight, effectiveWeight = 0.0,
                    lastUpdatedAt = raw.lastUpdatedAt, reason = "No data"
                )
            }

            // Staleness
            val staleness = classifyStaleness(raw.lastUpdatedAt, nowMs, config)
            val stalenessFactor = when (staleness) {
                ReadinessInputStatus.FRESH -> 1.0
                ReadinessInputStatus.DEGRADED -> 0.5
                else -> 0.0 // MISSING treated as excluded for staleness
            }

            if (stalenessFactor == 0.0) {
                return@map ReadinessInputResult(
                    id = raw.id, status = ReadinessInputStatus.MISSING,
                    rawLabel = formatRaw(raw.id, raw.value), score = null,
                    configuredWeight = weight, effectiveWeight = 0.0,
                    lastUpdatedAt = raw.lastUpdatedAt, reason = "Data too old"
                )
            }

            val score = scoreInput(raw.id, raw.value)
            val effectiveWeight = weight * stalenessFactor
            val reason = buildInputReason(raw.id, raw.value, score, staleness)

            ReadinessInputResult(
                id = raw.id, status = staleness,
                rawLabel = formatRaw(raw.id, raw.value), score = score,
                configuredWeight = weight, effectiveWeight = effectiveWeight,
                lastUpdatedAt = raw.lastUpdatedAt, reason = reason
            )
        }

        // Aggregate: weighted mean of contributing inputs
        val contributing = inputResults.filter { it.score != null && it.effectiveWeight > 0 }
        val totalWeight = contributing.sumOf { it.effectiveWeight }

        if (totalWeight <= 0.0 || contributing.isEmpty()) {
            return ReadinessResult(
                aggregateScore = null, label = null,
                summary = "Sync to update readiness",
                inputs = inputResults
            )
        }

        val aggregate = contributing.sumOf { it.score!!.toDouble() * it.effectiveWeight } / totalWeight
        val score = aggregate.toInt().coerceIn(0, 100)

        val label = when {
            score >= 80 -> "Good to go"
            score >= 50 -> "Take it easy"
            else -> "Recovery day"
        }

        val concerns = contributing.filter { it.score!! < 50 }.map { it.reason }
        val summary = if (concerns.isEmpty()) "All metrics looking good"
        else concerns.joinToString(" · ")

        return ReadinessResult(
            aggregateScore = score, label = label,
            summary = summary, inputs = inputResults
        )
    }

    // -- Scoring functions (ADR-003 §4: isolated, testable, replaceable) --

    private fun scoreInput(id: ReadinessInputId, value: Double): Int = when (id) {
        ReadinessInputId.SLEEP -> scoreSleep(value)
        ReadinessInputId.BLOOD_PRESSURE -> scoreBp(value)
        ReadinessInputId.HRV -> scoreHrv(value)
        ReadinessInputId.SUBJECTIVE -> (value * 20).toInt().coerceIn(0, 100) // 1-5 → 20-100
        ReadinessInputId.TRAINING_LOAD -> value.toInt().coerceIn(0, 100) // direct 0-100
    }

    /** Sleep: discrete thresholds from Phase 2 brief. */
    private fun scoreSleep(minutes: Double): Int = when {
        minutes >= 420 -> 100  // 7h+
        minutes >= 360 -> 70   // 6-7h
        else           -> 25   // <6h
    }

    /** BP: discrete thresholds from Phase 2 brief. */
    private fun scoreBp(systolic: Double): Int = when {
        systolic < 120  -> 100
        systolic < 130  -> 70
        systolic < 140  -> 40
        else            -> 10
    }

    /** HRV: discrete thresholds from Phase 2 brief. */
    private fun scoreHrv(ms: Double): Int = when {
        ms >= 60 -> 100
        ms >= 30 -> 70
        else     -> 25
    }

    // -- Staleness classification --

    private fun classifyStaleness(
        lastUpdatedAt: String?,
        nowMs: Long,
        config: Config
    ): ReadinessInputStatus {
        if (lastUpdatedAt == null) return ReadinessInputStatus.MISSING
        val updatedMs = try {
            java.time.Instant.parse(lastUpdatedAt).toEpochMilli()
        } catch (_: Exception) {
            // Try parsing as epoch millis string
            lastUpdatedAt.toLongOrNull() ?: return ReadinessInputStatus.MISSING
        }
        val hoursAgo = (nowMs - updatedMs) / (1000.0 * 60 * 60)
        return when {
            hoursAgo < config.normalHours -> ReadinessInputStatus.FRESH
            hoursAgo < config.degradedHours -> ReadinessInputStatus.DEGRADED
            else -> ReadinessInputStatus.MISSING
        }
    }

    // -- Display helpers --

    private fun formatRaw(id: ReadinessInputId, value: Double): String = when (id) {
        ReadinessInputId.SLEEP -> "${(value / 60).toInt()}h ${(value % 60).toInt()}m"
        ReadinessInputId.BLOOD_PRESSURE -> "${value.toInt()} mmHg"
        ReadinessInputId.HRV -> "${value.toInt()} ms"
        ReadinessInputId.SUBJECTIVE -> "${value.toInt()}/5"
        ReadinessInputId.TRAINING_LOAD -> "${value.toInt()}%"
    }

    private fun buildInputReason(
        id: ReadinessInputId, value: Double, score: Int,
        staleness: ReadinessInputStatus
    ): String {
        val staleTag = if (staleness == ReadinessInputStatus.DEGRADED) " (yesterday)" else ""
        return when (id) {
            ReadinessInputId.SLEEP -> {
                val h = (value / 60).toInt()
                when { score >= 80 -> "Sleep ${h}h$staleTag"; score >= 50 -> "Under 7h sleep$staleTag"; else -> "Low sleep (${h}h)$staleTag" }
            }
            ReadinessInputId.BLOOD_PRESSURE -> {
                when { score >= 80 -> "BP normal$staleTag"; score >= 50 -> "BP elevated$staleTag"; else -> "BP high (${value.toInt()})$staleTag" }
            }
            ReadinessInputId.HRV -> {
                when { score >= 80 -> "HRV good$staleTag"; score >= 50 -> "HRV moderate$staleTag"; else -> "Low HRV (${value.toInt()} ms)$staleTag" }
            }
            ReadinessInputId.SUBJECTIVE -> "Subjective ${value.toInt()}/5$staleTag"
            ReadinessInputId.TRAINING_LOAD -> "Load ${value.toInt()}%$staleTag"
        }
    }

    /** Lightweight config interface so tests can provide values without SharedPreferences. */
    data class Config(
        val sleepWeight: Double = 0.30,
        val bpWeight: Double = 0.20,
        val hrvWeight: Double = 0.00,
        val subjectiveWeight: Double = 0.00,
        val trainingLoadWeight: Double = 0.00,
        val normalHours: Int = 12,
        val degradedHours: Int = 24,
    ) {
        fun weightFor(id: ReadinessInputId): Double = when (id) {
            ReadinessInputId.SLEEP -> sleepWeight
            ReadinessInputId.BLOOD_PRESSURE -> bpWeight
            ReadinessInputId.HRV -> hrvWeight
            ReadinessInputId.SUBJECTIVE -> subjectiveWeight
            ReadinessInputId.TRAINING_LOAD -> trainingLoadWeight
        }
    }
}
