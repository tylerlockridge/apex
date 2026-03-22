package com.healthplatform.sync.readiness

import android.content.Context
import com.healthplatform.sync.SyncPrefsKeys
import com.healthplatform.sync.service.ReadinessInputRequest
import com.healthplatform.sync.service.ReadinessPayloadRequest
import com.healthplatform.sync.service.ServerApiClient
import java.time.Instant

/**
 * Shared builder for readiness payloads used by both DashboardViewModel and
 * GeneratedRoutineViewModel. Ensures generation requests use the same readiness
 * computation (including training load) that the dashboard displays.
 *
 * This is the single source of truth for the generation readiness payload.
 */
object ReadinessPayloadBuilder {

    /**
     * Build a complete readiness payload from SharedPrefs health data and
     * an optional server-fetched training-load score.
     *
     * @param context Application context for SharedPrefs access
     * @param trainingLoadScore Training-load score from the server progression summary,
     *   or null if unavailable. When non-null, activates the training-load weight.
     * @param nowMs Injectable time for testing
     */
    fun build(
        context: Context,
        trainingLoadScore: Double? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): ReadinessPayloadRequest {
        val prefs = context.getSharedPreferences(SyncPrefsKeys.FILE_NAME, Context.MODE_PRIVATE)

        val sleepMin = if (prefs.contains(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN))
            prefs.getInt(SyncPrefsKeys.LAST_SLEEP_DURATION_MIN, 0).toDouble() else null
        val sleepTime = prefs.getString(SyncPrefsKeys.LAST_SLEEP_TIME, null)
        val bpSystolic = if (prefs.contains(SyncPrefsKeys.LAST_BP_SYSTOLIC))
            prefs.getInt(SyncPrefsKeys.LAST_BP_SYSTOLIC, 0).toDouble() else null
        val bpTime = prefs.getString(SyncPrefsKeys.LAST_BP_TIME, null)
        val hrvMs = if (prefs.contains(SyncPrefsKeys.LAST_HRV_MS))
            prefs.getFloat(SyncPrefsKeys.LAST_HRV_MS, 0f).toDouble() else null
        val hrvTime = prefs.getString(SyncPrefsKeys.LAST_HRV_TIME, null)

        val trainingLoadTime = if (trainingLoadScore != null) Instant.now().toString() else null

        val configStore = ReadinessConfigStore(context)
        val effectiveTrainingWeight = if (trainingLoadScore != null)
            maxOf(configStore.trainingLoadWeight, 0.10) else configStore.trainingLoadWeight

        val engineConfig = ReadinessEngine.Config(
            sleepWeight = configStore.sleepWeight,
            bpWeight = configStore.bpWeight,
            hrvWeight = configStore.hrvWeight,
            subjectiveWeight = configStore.subjectiveWeight,
            trainingLoadWeight = effectiveTrainingWeight,
            normalHours = configStore.normalHours,
            degradedHours = configStore.degradedHours,
        )
        val rawInputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, sleepMin, sleepTime),
            ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, bpSystolic, bpTime),
            ReadinessEngine.RawInput(ReadinessInputId.HRV, hrvMs, hrvTime),
            ReadinessEngine.RawInput(ReadinessInputId.SUBJECTIVE, null, null),
            ReadinessEngine.RawInput(ReadinessInputId.TRAINING_LOAD, trainingLoadScore, trainingLoadTime),
        )
        val result = ReadinessEngine.compute(rawInputs, engineConfig, nowMs)

        val inputs = result.inputs.map { inp ->
            ReadinessInputRequest(
                id = inp.id.name,
                status = inp.status.name,
                score = inp.score,
                effectiveWeight = inp.effectiveWeight,
                lastUpdatedAt = inp.lastUpdatedAt,
                reason = inp.reason
            )
        }

        return ReadinessPayloadRequest(
            aggregateScore = result.aggregateScore,
            label = result.label,
            computedAt = Instant.ofEpochMilli(nowMs).toString(),
            inputs = inputs
        )
    }

    /**
     * Fetch training-load score from the server progression summary.
     * Returns null on any failure — training load is non-critical.
     */
    suspend fun fetchTrainingLoadScore(client: ServerApiClient): Double? {
        return try {
            client.getProgressionSummary(7).getOrNull()?.trainingLoadScore?.toDouble()
        } catch (_: Exception) {
            null
        }
    }
}
