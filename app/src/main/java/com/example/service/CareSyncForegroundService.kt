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
import com.example.engine.BluetoothCareEngine

class CareSyncForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val peer = intent?.getStringExtra(EXTRA_PEER_NAME)
                    ?: BluetoothCareEngine.connectedDeviceName.value
                    ?: "caregiver"
                ensureChannel()
                val notification = buildNotification("Connected to $peer · syncing")
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    } else {
                        0
                    }
                )
            }
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Care Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps nearby caregiver sync connected"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val launch = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BabyCare Care Sync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "channel_care_sync"
        const val NOTIFICATION_ID = 4201
        const val ACTION_START = "com.example.ACTION_CARE_SYNC_START"
        const val ACTION_STOP = "com.example.ACTION_CARE_SYNC_STOP"
        const val EXTRA_PEER_NAME = "peer_name"

        fun start(context: Context, peerName: String? = null) {
            val intent = Intent(context, CareSyncForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PEER_NAME, peerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CareSyncForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
