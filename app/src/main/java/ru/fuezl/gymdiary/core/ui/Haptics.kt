package ru.fuezl.gymdiary.core.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

fun HapticFeedback.performGymHaptic(enabled: Boolean, type: HapticFeedbackType = HapticFeedbackType.TextHandleMove) {
    if (enabled) performHapticFeedback(type)
}

fun Context.vibrateGymCue(enabled: Boolean, durationMs: Long = 250L) {
    if (!enabled) return
    val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
}
