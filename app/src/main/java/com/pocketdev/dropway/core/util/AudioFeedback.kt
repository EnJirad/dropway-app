package com.pocketdev.dropway.core.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Audio and haptic feedback engine matching the Dropway web scanner.
 * Emits a crisp 2.1kHz beep on capture and subtle tactile vibration.
 */
object AudioFeedback {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 95)
        } catch (_: Exception) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            } catch (_: Exception) {}
        }
    }

    /**
     * Emits a crisp 2.1kHz scanner beep and short vibration on successful label capture.
     */
    fun beepCapture(context: Context? = null) {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Exception) {}
        if (context != null) {
            vibrate(context, 60L)
        }
    }

    /**
     * Emits a success confirmation tone (e.g. house matched and parcel added).
     */
    fun beepSuccess(context: Context? = null) {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 160)
        } catch (_: Exception) {}
        if (context != null) {
            vibrate(context, 80L)
        }
    }

    /**
     * Emits a warning tone (e.g. duplicate parcel detected).
     */
    fun beepWarning(context: Context? = null) {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
        } catch (_: Exception) {}
        if (context != null) {
            vibrate(context, 120L)
        }
    }

    fun vibrate(context: Context, durationMs: Long = 60L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }
}
