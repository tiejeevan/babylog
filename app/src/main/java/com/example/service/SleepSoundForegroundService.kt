package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.engine.BabySoundSynthesizer
import com.example.engine.SoundType

class SleepSoundForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_USER_STOP -> {
                BabySoundSynthesizer.stopSound()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                BabySoundSynthesizer.pauseSound()
                refreshNotification()
                return START_STICKY
            }
            ACTION_RESUME -> {
                BabySoundSynthesizer.resumeSound()
                refreshNotification()
                return START_STICKY
            }
            ACTION_UPDATE -> {
                refreshNotification()
                return START_STICKY
            }
            else -> {
                ensureChannel()
                val notification = buildNotification(
                    soundTitle = intent?.getStringExtra(EXTRA_SOUND_TITLE)
                        ?: BabySoundSynthesizer.currentSound.value.title,
                    playing = BabySoundSynthesizer.isPlaying.value
                )
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else {
                        0
                    }
                )
            }
        }
        return START_STICKY
    }

    private fun refreshNotification() {
        ensureChannel()
        val manager = getSystemService(NotificationManager::class.java)
        val playing = BabySoundSynthesizer.isPlaying.value
        val paused = BabySoundSynthesizer.isPaused.value
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                soundTitle = BabySoundSynthesizer.currentSound.value.title,
                playing = playing
            )
        )
        if (!playing && !paused) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Sound Machine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps white noise playing while screen is off"
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(soundTitle: String, playing: Boolean): Notification {
        val launch = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_OPEN_SLEEP_SOUND, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, SleepSoundForegroundService::class.java).apply { action = ACTION_USER_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (playing) ACTION_PAUSE else ACTION_RESUME
        val toggleLabel = if (playing) "Pause" else "Play"
        val togglePending = PendingIntent.getService(
            this,
            2,
            Intent(this, SleepSoundForegroundService::class.java).apply { action = toggleAction },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = if (playing) "Playing" else "Paused"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Sound Machine")
            .setContentText("$status · $soundTitle")
            .setSmallIcon(R.drawable.ic_shortcut_sleep)
            .setContentIntent(contentPending)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, toggleLabel, togglePending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "channel_sleep_sound"
        const val NOTIFICATION_ID = 4202
        const val ACTION_START = "com.example.ACTION_SLEEP_SOUND_START"
        const val ACTION_DISMISS = "com.example.ACTION_SLEEP_SOUND_DISMISS"
        const val ACTION_USER_STOP = "com.example.ACTION_SLEEP_SOUND_USER_STOP"
        const val ACTION_PAUSE = "com.example.ACTION_SLEEP_SOUND_PAUSE"
        const val ACTION_RESUME = "com.example.ACTION_SLEEP_SOUND_RESUME"
        const val ACTION_UPDATE = "com.example.ACTION_SLEEP_SOUND_UPDATE"
        const val EXTRA_SOUND_TITLE = "sound_title"
        const val EXTRA_OPEN_SLEEP_SOUND = "open_sleep_sound"

        fun start(context: Context, sound: SoundType) {
            val intent = Intent(context, SleepSoundForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SOUND_TITLE, sound.title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context) {
            val intent = Intent(context, SleepSoundForegroundService::class.java).apply {
                action = ACTION_UPDATE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        /** Tear down notification/service only — does not call synthesizer. */
        fun dismiss(context: Context) {
            val intent = Intent(context, SleepSoundForegroundService::class.java).apply {
                action = ACTION_DISMISS
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
