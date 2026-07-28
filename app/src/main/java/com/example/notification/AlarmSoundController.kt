package com.example.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays the system alarm ringtone (USAGE_ALARM) with vibration until stopped.
 */
object AlarmSoundController {
    private val playing = AtomicBoolean(false)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start(context: Context) {
        if (!playing.compareAndSet(false, true)) return
        val app = context.applicationContext
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val player = MediaPlayer().apply {
                setDataSource(app, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
        } catch (_: Exception) {
            playing.set(false)
            mediaPlayer = null
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 400, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
            vibrator = null
        }
    }

    fun stop() {
        playing.set(false)
        try {
            mediaPlayer?.run {
                stop()
                release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
    }

    fun isPlaying(): Boolean = playing.get()
}
