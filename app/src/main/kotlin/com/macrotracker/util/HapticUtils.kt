package com.macrotracker.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

object HapticUtils {
    private var lastHapticAt = 0L
    private const val COOLDOWN_MS = 120L

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }
    }

    private fun vibrate(context: Context, millis: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHapticAt < COOLDOWN_MS) return
        try {
            val vibrator = getVibrator(context) ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(millis, amplitude))
            lastHapticAt = System.currentTimeMillis()
        } catch (_: Exception) { }
    }

    fun hapticLight(context: Context) {
        vibrate(context, 20, 40)
    }

    fun hapticMedium(context: Context) {
        vibrate(context, 35, 120, force = true)
    }

    fun hapticSuccess(context: Context) {
        vibrate(context, 40, 100, force = true)
    }

    fun hapticError(context: Context) {
        vibrate(context, 60, 200, force = true)
    }
}

