package app.awavenue.c001apk.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import app.awavenue.c001apk.data.HapticStrength
import app.awavenue.c001apk.ui.LocalApp

/**
 * The tick every click shares. compileSdk 37's Compose ships ContextClick, which the Android
 * implementation maps to [android.view.HapticFeedbackConstants.CONTEXT_CLICK] (API 23+).
 */
private val ClickHaptic = HapticFeedbackType.ContextClick

/**
 * Wraps [onClick] with the click haptic the settings page configures. With the setting off, or
 * with [enabled] false, the returned lambda is a plain pass-through.
 */
@Composable
fun rememberHapticClick(enabled: Boolean = true, onClick: () -> Unit): () -> Unit {
    val settings = LocalApp.current.settings
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val hapticsOn = settings.hapticFeedback
    val customHaptics = settings.customHaptics
    val reverseEffects = settings.reverseHapticEffects
    val strength = settings.hapticStrength

    return remember(
        enabled,
        hapticsOn,
        customHaptics,
        reverseEffects,
        strength,
        context,
        hapticFeedback,
        onClick,
    ) {
        {
            if (enabled && hapticsOn) {
                val customPlayed = customHaptics && context.playCustomHaptic(strength, reverseEffects)
                // Custom haptics that could not run (old platform, no permission, no vibrator
                // reported as unavailable) fall back to the Compose path, which needs no permission.
                if (!customPlayed) hapticFeedback.performHapticFeedback(ClickHaptic)
            }
            onClick()
        }
    }
}

/** Returns false when the caller should use the Compose fallback instead. */
private fun Context.playCustomHaptic(strength: HapticStrength, reverse: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val vibrator = vibrator() ?: return true
    if (!vibrator.hasVibrator()) return true

    val effect = VibrationEffect.createOneShot(
        strength.effective(reverse).durationMs,
        VibrationEffect.DEFAULT_AMPLITUDE,
    )
    // VIBRATE is a normal permission, but a build without it must not crash on a tap.
    return runCatching { vibrator.vibrate(effect) }.isSuccess
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
