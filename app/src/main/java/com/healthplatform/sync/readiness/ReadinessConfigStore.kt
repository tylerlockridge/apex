package com.healthplatform.sync.readiness

import android.content.Context
import android.content.SharedPreferences

/**
 * Persisted readiness engine configuration (ADR-003 §2).
 * Weights and thresholds are stored in SharedPreferences — modifiable
 * without an app release as H-02/H-03 validation produces data.
 *
 * Defaults: sleep 0.30, BP 0.20, HRV 0.00 (until A-01 positive),
 * subjective 0.00 (until capture UI), training-load 0.00 (until Phase 3).
 */
class ReadinessConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var sleepWeight: Double
        get() = prefs.getFloat(KEY_SLEEP_WEIGHT, 0.30f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_SLEEP_WEIGHT, v.toFloat()).apply()

    var bpWeight: Double
        get() = prefs.getFloat(KEY_BP_WEIGHT, 0.20f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_BP_WEIGHT, v.toFloat()).apply()

    var hrvWeight: Double
        get() = prefs.getFloat(KEY_HRV_WEIGHT, 0.00f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_HRV_WEIGHT, v.toFloat()).apply()

    var subjectiveWeight: Double
        get() = prefs.getFloat(KEY_SUBJECTIVE_WEIGHT, 0.00f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_SUBJECTIVE_WEIGHT, v.toFloat()).apply()

    var trainingLoadWeight: Double
        get() = prefs.getFloat(KEY_TRAINING_WEIGHT, 0.00f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_TRAINING_WEIGHT, v.toFloat()).apply()

    /** Hours before data is considered degraded. */
    var normalHours: Int
        get() = prefs.getInt(KEY_NORMAL_HOURS, 12)
        set(v) = prefs.edit().putInt(KEY_NORMAL_HOURS, v).apply()

    /** Hours before data is excluded entirely. */
    var degradedHours: Int
        get() = prefs.getInt(KEY_DEGRADED_HOURS, 24)
        set(v) = prefs.edit().putInt(KEY_DEGRADED_HOURS, v).apply()

    companion object {
        const val PREFS_NAME = "readiness_config"
        private const val KEY_SLEEP_WEIGHT = "weight_sleep"
        private const val KEY_BP_WEIGHT = "weight_bp"
        private const val KEY_HRV_WEIGHT = "weight_hrv"
        private const val KEY_SUBJECTIVE_WEIGHT = "weight_subjective"
        private const val KEY_TRAINING_WEIGHT = "weight_training_load"
        private const val KEY_NORMAL_HOURS = "staleness_normal_hours"
        private const val KEY_DEGRADED_HOURS = "staleness_degraded_hours"
    }
}
