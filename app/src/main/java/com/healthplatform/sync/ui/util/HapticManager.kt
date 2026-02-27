package com.healthplatform.sync.ui.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Apex haptic feedback system.
 *
 * Two tiers:
 *   1. View.performHapticFeedback() — zero permissions, respects system haptic settings,
 *      uses CONFIRM / REJECT / CLOCK_TICK / VIRTUAL_KEY (all available on API 34+)
 *   2. VibrationEffect.Composition — richer primitives for sync events (requires VIBRATE permission)
 *
 * All API calls are API 31+. minSdk = 34 so no version guards needed.
 */
object HapticManager {

    // -------------------------------------------------------------------------
    // Tier 1 — View.performHapticFeedback (no permission required)
    // -------------------------------------------------------------------------

    /** Crisp tap — button presses, FAB taps, card taps */
    fun click(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Subtle tick — tab selection, FilterChip toggle, switch toggle */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Positive double-tap — successful sync, permission granted */
    fun confirm(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /** Negative double-thud — sync error, invalid input */
    fun reject(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    // -------------------------------------------------------------------------
    // Tier 2 — VibrationEffect.Composition (VIBRATE permission required)
    // -------------------------------------------------------------------------

    private fun vibrator(context: Context): Vibrator {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        return manager.defaultVibrator
    }

    /**
     * Sync complete — SLOW_RISE builds anticipation, QUICK_FALL resolves it.
     * Feels like a "snap into place".
     */
    fun syncComplete(context: Context) {
        try {
            vibrator(context).vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.4f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f)
                    .compose()
            )
        } catch (_: Exception) { }
    }

    /**
     * Sync error — THUD signals something went wrong.
     */
    fun syncError(context: Context) {
        try {
            vibrator(context).vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                    .compose()
            )
        } catch (_: Exception) { }
    }
}

// ---------------------------------------------------------------------------
// Compose convenience wrapper
// ---------------------------------------------------------------------------

/**
 * Returns an [ApexHaptic] scoped to the current composable's View and Context.
 *
 * Usage:
 *   val haptic = rememberApexHaptic()
 *   Button(onClick = { haptic.click() }) { ... }
 */
@Composable
fun rememberApexHaptic(): ApexHaptic {
    val view = LocalView.current
    return remember(view) { ApexHaptic(view, view.context) }
}

class ApexHaptic(private val view: View, private val context: Context) {
    fun click()        = HapticManager.click(view)
    fun tick()         = HapticManager.tick(view)
    fun confirm()      = HapticManager.confirm(view)
    fun reject()       = HapticManager.reject(view)
    fun syncComplete() = HapticManager.syncComplete(context)
    fun syncError()    = HapticManager.syncError(context)
}
